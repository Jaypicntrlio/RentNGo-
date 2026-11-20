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
 * Fix #7 from the loophole review: each customer can use a promo code once. A cancelled booking
 * doesn't count, and an "already used" answer never counts as a wrong guess (fix #6).
 */
class PromoOncePerCustomerTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;
    private String johnId;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        auth.login("admin", "admin123");
        bookings.addPromoCode("BIG40", 40, TODAY.plusDays(30), 100);
        johnId = auth.findCustomerRecord("CUS001").orElseThrow().getUserId();
        auth.logout();
        auth.login("john", "john123");
    }

    private Booking book(String vehicle, int startOffset, String code) throws Exception {
        return bookings.createBooking(johnId, vehicle, TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 1), code);
    }

    private int usesLeft() throws Exception {
        auth.logout();
        auth.login("admin", "admin123");
        int left = bookings.getPromoCodes().get(0).getUsesLeft();
        auth.logout();
        auth.login("john", "john123");
        return left;
    }

    @Test
    void secondBookingWithTheSameCodeIsRefused() throws Exception {
        Booking first = book("VEH001", 1, "BIG40");
        BookingException e = assertThrows(BookingException.class, () -> book("VEH002", 4, "big40"));
        assertTrue(e.getMessage().contains("already used BIG40") && e.getMessage().contains(first.getBookingId()), e.getMessage());
        assertEquals(1, bookings.getAllBookings().size(), "nothing booked");
        assertEquals(99, usesLeft(), "no second use spent");
    }

    @Test
    void applyAlreadySaysSo() throws Exception {
        book("VEH001", 1, "BIG40");
        BookingException e = assertThrows(BookingException.class, () -> bookings.checkPromoCode("BIG40"));
        assertTrue(e.getMessage().contains("already used"), e.getMessage());
    }

    @Test
    void afterCancellingTheCodeCanBeUsedAgain() throws Exception {
        Booking first = book("VEH001", 1, "BIG40");
        payments.cancelBooking(first.getBookingId());
        Booking again = book("VEH002", 4, "BIG40");
        assertEquals("BIG40", again.getPromoCode());
    }

    @Test
    void otherCustomersCanStillUseIt() throws Exception {
        book("VEH001", 1, "BIG40");
        auth.logout();
        auth.login("jane", "jane123");
        String janeId = auth.getCurrentUser().getUserId();
        assertEquals("BIG40", bookings.createBooking(janeId, "VEH002", TODAY.plusDays(1), TODAY.plusDays(2), "BIG40").getPromoCode());
    }

    @Test
    void adminRentingForTheCustomerIsRefusedToo() throws Exception {
        book("VEH001", 1, "BIG40");
        auth.logout();
        auth.login("admin", "admin123");
        BookingException onApply = assertThrows(BookingException.class, () -> bookings.checkPromoCode("BIG40", johnId));
        assertTrue(onApply.getMessage().contains("This customer already used"), onApply.getMessage());
        assertThrows(BookingException.class,
                () -> bookings.createBooking(johnId, "VEH002", TODAY.plusDays(4), TODAY.plusDays(5), "BIG40"));
        assertDoesNotThrow(() -> bookings.checkPromoCode("BIG40", "CUS002"), "fine for another customer");
    }

    @Test
    void alreadyUsedDoesntCountAsAWrongGuess() throws Exception {
        book("VEH001", 1, "BIG40");
        for (int i = 0; i < 8; i++) {
            assertThrows(BookingException.class, () -> bookings.checkPromoCode("BIG40"));
        }
        BookingException e = assertThrows(BookingException.class, () -> bookings.checkPromoCode("BIG40"));
        assertFalse(e.getMessage().contains("Too many wrong promo codes"), "a real code isn't a guess: " + e.getMessage());
    }

    @Test
    void customersCantCheckForSomeoneElse() {
        assertThrows(BookingException.class, () -> bookings.checkPromoCode("BIG40", "CUS002"));
    }
}
