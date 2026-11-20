package service;

import model.Booking;
import model.BookingStatus;
import model.Payment;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixes from the loophole review: (1) admins can cancel a booking that was never picked up, such as
 * a no-show, with the refund recorded; (2) a cancellation that is undone because its refund couldn't
 * be saved no longer gives back the promo use and loyalty points.
 */
class CancellationFixesTest {

    private static final LocalDate TODAY = LocalDate.now();
    /** John agrees to an admin spending his points by typing his own password (fix #9). */
    private static final String JOHNS_PASSWORD = "john123";

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;
    private PaymentService payments;
    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("admin", "admin123");
    }

    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
    }

    /** Another program changes the file, so RentNGo's next save of it is refused. */
    private void touch(String name) throws Exception {
        Path p = dataDir.resolve(name);
        Files.writeString(p, Files.readString(p) + "# changed elsewhere\n");
    }

    private int points() {
        return auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints();
    }

    private int uses(String code) {
        return bookings.getPromoCodes().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow().getUsesLeft();
    }

    private long refundRows() {
        return payments.getAllPayments().stream().filter(Payment::isRefund).count();
    }

    private Booking paidBookingWithDiscounts() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 300);
        bookings.addPromoCode("SAVE10", 10, TODAY.plusDays(30), 1);
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7), "SAVE10", 300, JOHNS_PASSWORD);
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        assertEquals(0, points());
        assertEquals(0, uses("SAVE10"));
        return b;
    }

    // ===== Fix 2 =====

    @Test
    void undoneCancellationKeepsItsDiscounts() throws Exception {
        Booking b = paidBookingWithDiscounts();
        touch("payments.txt"); // the refund row can't be saved
        assertThrows(BookingException.class, () -> payments.cancelBooking(b.getBookingId()));

        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, after.getStatus(), "the cancellation was undone");
        assertEquals(300, after.getLoyaltyPointsUsed());
        assertEquals(0, points(), "points stay spent while the booking still uses them");
        assertEquals(0, uses("SAVE10"), "the promo use stays spent while the booking still has the discount");
        List<String> actions = audit.entriesNewestFirst().stream().map(AuditLog.Entry::getAction).collect(Collectors.toList());
        assertFalse(actions.contains(AuditLog.LOYALTY_RETURNED));
        assertFalse(actions.contains(AuditLog.PROMO_USE_RETURNED));
    }

    @Test
    void cancellingAgainAfterRestartGivesBackExactlyOnce() throws Exception {
        Booking b = paidBookingWithDiscounts();
        touch("payments.txt");
        assertThrows(BookingException.class, () -> payments.cancelBooking(b.getBookingId()));

        open(); // restart: payments.txt is read again, so it can be saved
        auth.login("admin", "admin123");
        payments.cancelBooking(b.getBookingId());
        assertEquals(BookingStatus.CANCELLED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
        assertEquals(300, points());
        assertEquals(1, uses("SAVE10"), "given back once, not twice");
        assertEquals(1, refundRows());
    }

    @Test
    void successfulCancellationStillGivesEverythingBack() throws Exception {
        Booking b = paidBookingWithDiscounts();
        payments.cancelBooking(b.getBookingId());
        assertEquals(300, points());
        assertEquals(1, uses("SAVE10"));
        List<String> actions = audit.entriesNewestFirst().stream().map(AuditLog.Entry::getAction).collect(Collectors.toList());
        assertTrue(actions.contains(AuditLog.LOYALTY_RETURNED));
        assertTrue(actions.contains(AuditLog.PROMO_USE_RETURNED));
    }

    @Test
    void statusChangeToCancelledAlsoGivesBack() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 200);
        Booking b = bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(5), TODAY.plusDays(6), null, 200, JOHNS_PASSWORD);
        bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED);
        assertEquals(200, points());
    }

    // ===== Fix 1 =====

    /** Books 3 rentals for John starting tomorrow, then moves them 10 days into the past (no-shows). */
    private void threeNoShows() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 100);
        for (int i = 1; i <= 3; i++) {
            Booking b = bookings.createBooking("CUS001", "VEH00" + i, TODAY.plusDays(1), TODAY.plusDays(2), null, i == 1 ? 100 : 0,
                    JOHNS_PASSWORD);
            payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        }
        Path f = dataDir.resolve("bookings.txt");
        Files.writeString(f, Files.readString(f)
                .replace(TODAY.plusDays(1).toString(), TODAY.minusDays(9).toString())
                .replace(TODAY.plusDays(2).toString(), TODAY.minusDays(8).toString()));
        open();
    }

    @Test
    void adminCancelsNoShowWithRefundRecorded() throws Exception {
        threeNoShows();
        auth.login("john", "john123");
        String johnId = auth.getCurrentUser().getUserId();
        assertThrows(BookingException.class, () -> payments.cancelBooking("BKG0001"), "customers still can't cancel after the start");
        assertThrows(BookingException.class, () -> bookings.createBooking(johnId, "VEH004", TODAY.plusDays(3), TODAY.plusDays(4)),
                "stuck at 3 open rentals");

        auth.logout();
        auth.login("admin", "admin123");
        Booking before = bookings.getBookingById("BKG0001").orElseThrow(); // ₱2,500 less 100 points = ₱2,400 paid
        BigDecimal refund = payments.cancelBooking("BKG0001");

        Booking after = bookings.getBookingById("BKG0001").orElseThrow();
        assertEquals(BookingStatus.CANCELLED, after.getStatus());
        assertEquals(0, new BigDecimal("1920.00").compareTo(refund), "a no-show keeps the 20% late-cancellation fee of what was paid");
        assertEquals(0, after.getLateFee().signum(), "no late fees for a car that was never taken");
        assertEquals(1, refundRows(), "the refund is a payment row, so Reports are right");
        assertEquals(0, new BigDecimal("2400.00").compareTo(before.getNetPaid()));
        assertEquals(100, points(), "the points come back");

        auth.logout();
        auth.login("john", "john123");
        assertNotNull(bookings.createBooking(johnId, "VEH004", TODAY.plusDays(3), TODAY.plusDays(4)),
                "the customer can book again");
    }
}
