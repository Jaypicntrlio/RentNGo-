package service;

import model.Booking;
import model.Review;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for ratings and reviews (feature 2). */
class ReviewTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;
    private PaymentService payments;
    private ReviewService reviews;

    @BeforeEach
    void setUp() {
        open();
    }

    /** Opens the services on dataDir, like starting the app again. */
    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        reviews = bookings.getReviewService();
    }

    private void as(String user, String password) throws Exception {
        if (auth.isLoggedIn()) auth.logout();
        auth.login(user, password);
    }

    /** An admin rents the vehicle to the customer today, takes payment, hands it over and takes it back. */
    private Booking completedRental(String customerId, String vehicleId) throws Exception {
        as("admin", "admin123");
        Booking b = bookings.createBooking(customerId, vehicleId, TODAY, TODAY.plusDays(1));
        payments.processPayment(b.getBookingId(), customerId, b.getBalanceDue(), "Cash", b.getBalanceDue());
        bookings.markPickedUp(b.getBookingId());
        return bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
    }

    @Test
    void customerReviewsTheirCompletedRentalOnce() throws Exception {
        Booking b = completedRental("CUS001", "VEH001");
        as("john", "john123");
        assertNull(reviews.whyCannotReview(b.getBookingId()));

        Review r = reviews.addReview(b.getBookingId(), 4, "  Clean car,\n friendly staff  ");
        assertEquals("VEH001", r.getVehicleId());
        assertEquals("Clean car, friendly staff", r.getComment(), "trimmed, line breaks turned into spaces");
        assertEquals("RVW0001", r.getReviewId());

        BookingException again = assertThrows(BookingException.class, () -> reviews.addReview(b.getBookingId(), 5, "again"));
        assertTrue(again.getMessage().contains("already reviewed"));
    }

    @Test
    void reviewSurvivesRestart() throws Exception {
        Booking b = completedRental("CUS001", "VEH002");
        as("john", "john123");
        reviews.addReview(b.getBookingId(), 5, "Great\tcar");

        open();
        Review loaded = reviews.getReviewForBooking(b.getBookingId()).orElseThrow();
        assertEquals(5, loaded.getRating());
        assertEquals("Great\tcar", loaded.getComment());
    }

    @Test
    void onlyCompletedRentalsCanBeReviewed() throws Exception {
        as("admin", "admin123");
        Booking upcoming = bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(3), TODAY.plusDays(4));
        as("john", "john123");
        BookingException e = assertThrows(BookingException.class, () -> reviews.addReview(upcoming.getBookingId(), 5, ""));
        assertTrue(e.getMessage().contains("returned"));
    }

    @Test
    void onlyTheRentersCanReview() throws Exception {
        Booking johns = completedRental("CUS001", "VEH001");
        // the admin can't write it for them
        assertThrows(BookingException.class, () -> reviews.addReview(johns.getBookingId(), 1, "fake"));
        // another customer can't even see the booking
        as("jane", "jane123");
        BookingException e = assertThrows(BookingException.class, () -> reviews.addReview(johns.getBookingId(), 1, "fake"));
        assertTrue(e.getMessage().contains("not found"));
        assertTrue(reviews.getVisibleReviews("VEH001").isEmpty());
    }

    @Test
    void ratingAndCommentAreChecked() throws Exception {
        Booking b = completedRental("CUS001", "VEH001");
        as("john", "john123");
        assertThrows(BookingException.class, () -> reviews.addReview(b.getBookingId(), 0, ""));
        assertThrows(BookingException.class, () -> reviews.addReview(b.getBookingId(), 6, ""));
        BookingException tooLong = assertThrows(BookingException.class,
                () -> reviews.addReview(b.getBookingId(), 3, "x".repeat(Review.MAX_COMMENT_LENGTH + 1)));
        assertTrue(tooLong.getMessage().contains("300"));
        assertNotNull(reviews.addReview(b.getBookingId(), 3, "x".repeat(Review.MAX_COMMENT_LENGTH)), "exactly 300 is fine");
    }

    @Test
    void averageRatingCountsVisibleReviewsOnly() throws Exception {
        Booking b1 = completedRental("CUS001", "VEH004");
        Booking b2 = completedRental("CUS002", "VEH004");
        as("john", "john123");
        reviews.addReview(b1.getBookingId(), 5, "Loved it");
        as("jane", "jane123");
        Review rude = reviews.addReview(b2.getBookingId(), 2, "something offensive");

        ReviewService.Summary both = reviews.getRatingSummary("VEH004").orElseThrow();
        assertEquals(3.5, both.getAverage(), 0.0001);
        assertEquals(2, both.getCount());
        assertEquals("★ 3.5 (2)", both.toString());
        assertTrue(reviews.getRatingSummary("VEH005").isEmpty(), "no reviews, no rating");

        as("admin", "admin123");
        reviews.setHidden(rude.getReviewId(), true);
        ReviewService.Summary visible = reviews.getRatingSummary("VEH004").orElseThrow();
        assertEquals(5.0, visible.getAverage(), 0.0001);
        assertEquals(1, visible.getCount());
        assertEquals(1, reviews.getVisibleReviews("VEH004").size());
        assertEquals(2, reviews.getAllReviews().size(), "admins still see the hidden one");

        reviews.setHidden(rude.getReviewId(), false);
        assertEquals(2, reviews.getRatingSummary("VEH004").orElseThrow().getCount(), "shown again");
    }

    @Test
    void onlyAdminsCanHideAndItIsAudited() throws Exception {
        Booking b = completedRental("CUS001", "VEH001");
        as("john", "john123");
        Review r = reviews.addReview(b.getBookingId(), 1, "bad words");
        assertThrows(BookingException.class, () -> reviews.setHidden(r.getReviewId(), true));
        assertTrue(reviews.getAllReviews().isEmpty(), "customers can't list every review");

        File auditFile = dataDir.resolve("audit.txt").toFile();
        AuditLog audit = new AuditLog(auditFile, auth::getCurrentUser);
        bookings.setAuditLog(audit);
        as("admin", "admin123");
        reviews.setHidden(r.getReviewId(), true);
        List<AuditLog.Entry> entries = audit.entriesNewestFirst();
        assertEquals(AuditLog.REVIEW_HIDDEN, entries.get(0).getAction());
        assertTrue(entries.get(0).getDetails().contains(r.getReviewId()));
    }

    @Test
    void damagedLineIsSkippedAndNumbersAreNotReused() throws Exception {
        Booking b = completedRental("CUS001", "VEH001");
        as("john", "john123");
        reviews.addReview(b.getBookingId(), 4, "ok");
        File file = dataDir.resolve("reviews.txt").toFile();
        Files.writeString(file.toPath(), Files.readString(file.toPath()) + "RVW0007\tBKG0099\tCUS001\tVEH002\t9\tx\t2026-01-01\tfalse\n");

        open();
        assertNotNull(bookings.getLoadError());
        assertTrue(bookings.getLoadError().contains("reviews.txt line"));
        Booking b2 = completedRental("CUS001", "VEH002");
        as("john", "john123");
        assertEquals("RVW0008", reviews.addReview(b2.getBookingId(), 5, "").getReviewId());
    }
}
