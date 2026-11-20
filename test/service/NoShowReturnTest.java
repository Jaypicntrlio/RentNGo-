package service;

import model.Booking;
import model.BookingStatus;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #3 from the loophole review: only a vehicle that was picked up can be returned. A booking the
 * customer never collected is cancelled instead, so it is never charged late fees for a car that
 * stayed in the lot, and never earns loyalty points or a review.
 */
class NoShowReturnTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("admin", "admin123");
    }

    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
    }

    private Booking paidBooking() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2)); // ₱5,000
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        return b;
    }

    @Test
    void bookingThatWasNeverPickedUpCantBeReturned() throws Exception {
        Booking b = paidBooking();
        BookingException e = assertThrows(BookingException.class,
                () -> bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO));
        assertTrue(e.getMessage().contains("picked up"), e.getMessage());
        assertTrue(e.getMessage().contains("cancel"), "says what to do instead: " + e.getMessage());

        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, after.getStatus(), "nothing changed");
        assertEquals(0, after.getLateFee().signum());
        assertEquals(0, auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints(), "no points for a car never taken");
    }

    @Test
    void returnWithPaymentAndStatusChangeAreRefusedToo() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH002", TODAY, TODAY.plusDays(1));
        assertThrows(model.exceptions.RentNGoException.class, () -> payments.completeReturn(b.getBookingId(), TODAY,
                BigDecimal.ZERO, "Cash", b.getBalanceDue()));
        assertTrue(payments.getAllPayments().isEmpty(), "no payment taken for a return that didn't happen");
        assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.COMPLETED));
        assertEquals(BookingStatus.CONFIRMED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void noShowCantBeReviewed() throws Exception {
        Booking b = paidBooking();
        assertThrows(BookingException.class, () -> bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO));
        auth.logout();
        auth.login("john", "john123");
        assertThrows(BookingException.class, () -> bookings.getReviewService().addReview(b.getBookingId(), 1, "never got the car"));
    }

    @Test
    void noShowWithPastDatesIsCancelledNotChargedLateFees() throws Exception {
        Booking b = paidBooking();
        Path f = dataDir.resolve("bookings.txt"); // pretend the rental dates passed without a pick-up
        Files.writeString(f, Files.readString(f).replace(TODAY.toString(), TODAY.minusDays(12).toString())
                .replace(TODAY.plusDays(2).toString(), TODAY.minusDays(10).toString()));
        open();
        auth.login("admin", "admin123");
        assertThrows(BookingException.class, () -> bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO),
                "before this fix it was 'returned' with ₱37,500 of late fees");
        BigDecimal refund = payments.cancelBooking(b.getBookingId());
        assertEquals(0, new BigDecimal("4000.00").compareTo(refund), "cancelled: 20% no-show fee kept, rest refunded");
    }

    @Test
    void pickedUpRentalReturnsAsBefore() throws Exception {
        Booking b = paidBooking();
        bookings.markPickedUp(b.getBookingId());
        Booking returned = bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        assertEquals(BookingStatus.COMPLETED, returned.getStatus());
        assertEquals(50, returned.getLoyaltyPointsEarned());
    }
}
