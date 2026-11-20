package service;

import model.Booking;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #9 from the loophole review: an admin can only spend a customer's loyalty points when the
 * customer agrees by typing their own password. Customers booking for themselves need nothing extra.
 */
class PointsConsentTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
        auth.login("admin", "admin123");
        auth.changeLoyaltyPoints("CUS001", 1000);
    }

    private int points() {
        return auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints();
    }

    private Booking adminBooks(int points, String password) throws Exception {
        return bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2), null, points, password);
    }

    @Test
    void adminNeedsTheCustomersPassword() {
        BookingException noPassword = assertThrows(BookingException.class, () -> adminBooks(1000, null));
        assertTrue(noPassword.getMessage().contains("must type their own password"), noPassword.getMessage());
        BookingException wrong = assertThrows(BookingException.class, () -> adminBooks(1000, "admin123"));
        assertTrue(wrong.getMessage().contains("Wrong password for John Doe"), wrong.getMessage());
        assertEquals(1000, points(), "no points spent");
        assertTrue(bookings.getAllBookings().isEmpty(), "nothing booked");
    }

    @Test
    void withTheCustomersPasswordThePointsAreSpentAndAudited() throws Exception {
        Booking b = adminBooks(1000, "john123");
        assertEquals(1000, b.getLoyaltyPointsUsed());
        assertEquals(0, points());
        assertTrue(audit.entriesNewestFirst().stream().anyMatch(e -> e.getAction().equals(AuditLog.LOYALTY_SPENT)
                && e.getDetails().contains("confirmed with the customer's password")));
    }

    @Test
    void adminBookingWithoutPointsNeedsNoPassword() throws Exception {
        assertNotNull(adminBooks(0, null));
    }

    @Test
    void customerBookingForThemselvesNeedsNoPassword() throws Exception {
        auth.logout();
        auth.login("john", "john123");
        String johnId = auth.getCurrentUser().getUserId();
        Booking own = bookings.createBooking(johnId, "VEH001", TODAY.plusDays(1), TODAY.plusDays(2), null, 500);
        assertEquals(500, own.getLoyaltyPointsUsed());
    }

    @Test
    void guessingTheCustomersPasswordLocksTheirAccount() throws Exception {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(BookingException.class, () -> adminBooks(100, "guess"));
        }
        BookingException locked = assertThrows(BookingException.class, () -> adminBooks(100, "guess"));
        assertTrue(locked.getMessage().contains("Too many attempts"), locked.getMessage());
        assertThrows(BookingException.class, () -> adminBooks(100, "john123"), "even the right password while locked");
        auth.logout();
        assertThrows(Exception.class, () -> auth.login("john", "john123"), "the customer's own login is locked too");
        assertEquals(1000, auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints());
    }

    @Test
    void customerStillOnTheStartingPasswordMustChooseTheirOwnFirst() throws Exception {
        String id = auth.addCustomer("New Person", "new@email.com", "555-0000", "DL555000").getUserId();
        String startingPassword = auth.takeTemporaryPassword(id);
        auth.changeLoyaltyPoints(id, 300);
        BookingException e = assertThrows(BookingException.class, () -> bookings.createBooking(id, "VEH002",
                TODAY.plusDays(1), TODAY.plusDays(2), null, 300, startingPassword));
        assertTrue(e.getMessage().contains("choose their own password"), "the admin saw that password: " + e.getMessage());
    }
}
