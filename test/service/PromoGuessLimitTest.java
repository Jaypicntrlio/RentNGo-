package service;

import model.Booking;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #6 from the loophole review: promo codes can't be found by guessing. After 5 wrong codes in a
 * row a customer can't check or use any promo code for 15 minutes; admins aren't limited.
 */
class PromoGuessLimitTest {

    private static final LocalDate TODAY = LocalDate.now();

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-19T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private AuditLog audit;
    private final TestClock clock = new TestClock();

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("admin", "admin123");
        bookings.addPromoCode("VIP50", 50, TODAY.plusDays(30), 100);
        auth.logout();
        auth.login("john", "john123");
    }

    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
        bookings.setPromoGuessClock(clock);
    }

    private void guessWrong(int times) {
        for (int i = 0; i < times; i++) {
            int n = i;
            assertThrows(BookingException.class, () -> bookings.checkPromoCode("VIP" + (10 + n)));
        }
    }

    @Test
    void fiveWrongCodesLockEvenTheRightOne() {
        guessWrong(4);
        assertEquals(50, assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50")).getPercentOff(),
                "4 wrong guesses are still fine");

        guessWrong(4);
        BookingException fifth = assertThrows(BookingException.class, () -> bookings.checkPromoCode("NOPE"));
        assertTrue(fifth.getMessage().contains("Too many wrong promo codes"), fifth.getMessage());

        BookingException locked = assertThrows(BookingException.class, () -> bookings.checkPromoCode("VIP50"));
        assertTrue(locked.getMessage().contains("Try again in 15 minutes"), locked.getMessage());
        assertTrue(audit.entriesNewestFirst().stream().anyMatch(e -> e.getAction().equals(AuditLog.PROMO_GUESSES_LOCKED)));
    }

    @Test
    void lockEndsAfter15Minutes() {
        guessWrong(5);
        clock.now = clock.now.plus(Duration.ofMinutes(14));
        assertThrows(BookingException.class, () -> bookings.checkPromoCode("VIP50"));
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50"));
    }

    @Test
    void rightCodeStartsTheCountAgain() {
        guessWrong(4);
        assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50"));
        guessWrong(4);
        assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50"), "8 wrong in total, but never 5 in a row");
    }

    @Test
    void restartingDoesntResetTheLock() throws Exception {
        guessWrong(5);
        open();
        auth.login("john", "john123");
        assertThrows(BookingException.class, () -> bookings.checkPromoCode("VIP50"));
    }

    @Test
    void lockedCustomerCanStillBookWithoutACode() throws Exception {
        guessWrong(5);
        String johnId = auth.getCurrentUser().getUserId();
        assertThrows(BookingException.class,
                () -> bookings.createBooking(johnId, "VEH001", TODAY.plusDays(1), TODAY.plusDays(2), "VIP50"));
        Booking plain = bookings.createBooking(johnId, "VEH001", TODAY.plusDays(1), TODAY.plusDays(2), null);
        assertNull(plain.getPromoCode());
    }

    @Test
    void eachCustomerHasTheirOwnCount() throws Exception {
        guessWrong(5);
        auth.logout();
        auth.login("jane", "jane123");
        assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50"));
    }

    @Test
    void adminsAreNotLimited() throws Exception {
        auth.logout();
        auth.login("admin", "admin123");
        guessWrong(12);
        assertDoesNotThrow(() -> bookings.checkPromoCode("VIP50"));
    }
}
