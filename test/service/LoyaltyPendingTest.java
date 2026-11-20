package service;

import model.Booking;
import model.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #4 from the loophole review: loyalty points that can't be saved to the customer's account are
 * kept as owed on the booking, the admin is told, and they are given at the next start, only once.
 */
class LoyaltyPendingTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private DataStore store;
    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;

    @BeforeEach
    void setUp() throws Exception {
        start();
    }

    /** Starts RentNGo on dataDir (like the app does) and logs in as admin. */
    private void start() throws Exception {
        store = new DataStore(dataDir.toFile(), null);
        auth = store.getAuthService();
        bookings = store.getBookingService();
        payments = store.getPaymentService();
        if (!auth.isLoggedIn()) auth.login("admin", "admin123");
    }

    /** Another program changes accounts.txt, so RentNGo refuses to save over it until it restarts. */
    private void touchAccounts() throws Exception {
        Path p = dataDir.resolve("accounts.txt");
        Files.writeString(p, Files.readString(p) + "# changed elsewhere\n");
    }

    private int johnsPoints() {
        return auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints();
    }

    /** Camry today → in 2 days (₱5,000), paid and picked up. */
    private Booking paidAndPickedUp() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        bookings.markPickedUp(b.getBookingId());
        return b;
    }

    @Test
    void pointsThatCantBeSavedAreKeptAsOwedAndTheAdminIsTold() throws Exception {
        Booking b = paidAndPickedUp();
        touchAccounts();
        Booking returned = bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);

        assertEquals(BookingStatus.COMPLETED, returned.getStatus(), "the return itself is saved");
        assertEquals(0, returned.getLoyaltyPointsEarned());
        assertEquals(50, returned.getLoyaltyPointsPending(), "the 50 points are owed, not lost");
        assertEquals(0, johnsPoints());
        String warning = bookings.getLoyaltyPointsWarning(b.getBookingId());
        assertNotNull(warning, "the admin is told");
        assertTrue(warning.contains("50 loyalty points") && warning.contains("next time RentNGo starts"), warning);
        assertTrue(store.getBookingService().getAuditLog().entriesNewestFirst().stream()
                .anyMatch(e -> e.getAction().equals(AuditLog.LOYALTY_PENDING)));
    }

    @Test
    void owedPointsAreGivenAtTheNextStartOnlyOnce() throws Exception {
        Booking b = paidAndPickedUp();
        touchAccounts();
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);

        start(); // restart: accounts.txt is read again, so it can be saved
        assertEquals(50, johnsPoints(), "given at start-up");
        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(50, after.getLoyaltyPointsEarned());
        assertEquals(0, after.getLoyaltyPointsPending());
        assertNull(bookings.getLoyaltyPointsWarning(b.getBookingId()));
        assertTrue(store.getLoadProblems().isEmpty(), store.getLoadProblems().toString());

        start(); // and again: nothing is given twice
        assertEquals(50, johnsPoints());
        bookings.awardLoyaltyPointsIfDue(b.getBookingId());
        assertEquals(50, johnsPoints());
    }

    @Test
    void pointsDueAtPaymentTimeAreKeptToo() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1)); // ₱2,500, not paid
        bookings.markPickedUp(b.getBookingId());
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        touchAccounts();
        payments.processPayment(b.getBookingId(), "CUS001", new BigDecimal("2500.00"), "Cash", new BigDecimal("2500.00"));
        assertEquals(25, bookings.getBookingById(b.getBookingId()).orElseThrow().getLoyaltyPointsPending());
        assertNotNull(bookings.getLoyaltyPointsWarning(b.getBookingId()));

        start();
        assertEquals(25, johnsPoints());
    }

    @Test
    void stillOwedWhenTheAccountsFileStaysBrokenAndSaidAtStartUp() throws Exception {
        Booking b = paidAndPickedUp();
        touchAccounts();
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);

        // accounts.txt loses its admin row: RentNGo then never saves it (read-only)
        Path accounts = dataDir.resolve("accounts.txt");
        Files.writeString(accounts, Files.readAllLines(accounts).stream()
                .filter(line -> !line.startsWith("ADMIN")).collect(Collectors.joining("\n", "", "\n")));
        store = new DataStore(dataDir.toFile(), null);
        assertTrue(store.accountsUnavailable());
        assertEquals(50, store.getBookingService().find(b.getBookingId()).orElseThrow().getLoyaltyPointsPending(), "still owed, not lost");
        assertTrue(store.getLoadProblems().stream().anyMatch(p -> p.contains("loyalty points")), store.getLoadProblems().toString());
    }

    @Test
    void ordinaryReturnHasNoWarning() throws Exception {
        Booking b = paidAndPickedUp();
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        assertEquals(50, johnsPoints());
        assertNull(bookings.getLoyaltyPointsWarning(b.getBookingId()));
    }
}
