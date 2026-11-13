package service;

import model.Booking;
import model.Customer;
import model.User;
import model.exceptions.AuthenticationException;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for deactivating customers instead of deleting them. */
class CustomerDeactivationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private DataStore store;
    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;

    @BeforeEach
    void setUp() {
        startStore();
    }

    /** (Re)opens the data folder, like restarting the app. */
    private void startStore() {
        store = Stores.asAdmin(dataDir);
        auth = store.getAuthService();
        bookings = store.getBookingService();
        payments = store.getPaymentService();
    }

    private Customer customer(String id) {
        return auth.getAllCustomers().stream().filter(c -> c.getUserId().equals(id)).findFirst().orElseThrow();
    }

    private void pay(Booking b) throws Exception {
        payments.processPayment(b.getBookingId(), b.getCustomerId(), b.getBalanceDue(), "Cash", b.getBalanceDue());
    }

    // ===== Saved in accounts.txt =====

    @Test
    void activeFlagIsSavedAndMissingColumnMeansActive() throws Exception {
        // accounts written before the column existed load as active
        String file = Files.readString(dataDir.resolve("accounts.txt"));
        StringBuilder old = new StringBuilder();
        for (String line : file.split("\\R")) {
            old.append(line.startsWith("#") ? line : line.substring(0, line.lastIndexOf('\t'))).append('\n');
        }
        Files.writeString(dataDir.resolve("accounts.txt"), old.toString());
        startStore();
        assertTrue(auth.getAllUsers().stream().allMatch(User::isActive));
        assertNull(auth.getLoadError());

        auth.deactivateCustomer("CUS002", bookings);
        assertTrue(Files.readString(dataDir.resolve("accounts.txt")).contains("\tjane\t"), "jane is kept, not deleted");
        startStore();
        assertFalse(customer("CUS002").isActive(), "deactivation survives a restart");
        assertTrue(customer("CUS001").isActive());
    }

    @Test
    void badActiveValueSkipsOnlyThatAccount() throws Exception {
        String file = Files.readString(dataDir.resolve("accounts.txt"));
        Files.writeString(dataDir.resolve("accounts.txt"), file.replaceFirst("(\tjane\t.*)\ttrue", "$1\tmaybe"));
        startStore();
        assertTrue(auth.getLoadError().contains("active must be true or false"), auth.getLoadError());
        assertTrue(auth.getAllCustomers().stream().noneMatch(c -> c.getUsername().equals("jane")));
        assertEquals(1, auth.getAllCustomers().size());
    }

    // ===== deactivateCustomer() refuses open rentals and debts =====

    @Test
    void customerWhoOwesMoneyAndHasConfirmedBookingIsNotDeactivated() throws Exception {
        Booking owed = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(3), TODAY.plusDays(5)); // 2 x 2,500 = 5,000 unpaid

        ValidationException e = assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS001", bookings));

        assertTrue(e.getMessage().contains(owed.getBookingId()), e.getMessage());
        assertTrue(e.getMessage().contains("confirmed for " + TODAY.plusDays(3)), e.getMessage());
        assertTrue(e.getMessage().contains("owes ₱5,000.00"), e.getMessage());
        assertTrue(customer("CUS001").isActive(), "nothing changes when refused");
        startStore();
        assertTrue(customer("CUS001").isActive(), "and nothing was saved");
    }

    @Test
    void confirmedBookingBlocksEvenWhenPaid() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(3), TODAY.plusDays(4));
        pay(b);
        ValidationException e = assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS001", bookings));
        assertTrue(e.getMessage().contains(b.getBookingId() + " (confirmed"), e.getMessage());
        assertFalse(e.getMessage().contains("owes"), e.getMessage());
    }

    @Test
    void pickedUpRentalBlocks() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
        pay(b);
        bookings.markPickedUp(b.getBookingId());
        ValidationException e = assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS001", bookings));
        assertTrue(e.getMessage().contains(b.getBookingId() + " (vehicle not returned yet)"), e.getMessage());
    }

    @Test
    void unpaidBalanceOnFinishedRentalBlocks() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        pay(b);
        bookings.markPickedUp(b.getBookingId());
        bookings.returnBooking(b.getBookingId(), TODAY, new BigDecimal("1200")); // damage charge not paid yet
        ValidationException e = assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS001", bookings));
        assertTrue(e.getMessage().contains(b.getBookingId() + " (owes ₱1,200.00)"), e.getMessage());
    }

    @Test
    void finishedPaidAndCancelledBookingsDontBlockAndHistoryKeepsTheName() throws Exception {
        Booking done = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        pay(done);
        bookings.markPickedUp(done.getBookingId());
        bookings.returnBooking(done.getBookingId(), TODAY, BigDecimal.ZERO);
        Booking cancelled = bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(4), TODAY.plusDays(5));
        payments.cancelBooking(cancelled.getBookingId());

        auth.deactivateCustomer("CUS001", bookings);

        assertFalse(customer("CUS001").isActive());
        assertEquals("John Doe", customer("CUS001").getFullName(), "bookings still find the customer's name");
        assertEquals(2, bookings.getBookingsByCustomer("CUS001").size());
    }

    @Test
    void deactivatingTwiceOrUnknownCustomerIsRefused() throws Exception {
        auth.deactivateCustomer("CUS002", bookings);
        assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS002", bookings));
        assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS999", bookings));
        assertThrows(ValidationException.class, () -> auth.deactivateCustomer("ADM001", bookings), "admins aren't customers");
    }

    // ===== login() and createBooking() =====

    @Test
    void deactivatedCustomerCannotLogIn() throws Exception {
        auth.deactivateCustomer("CUS002", bookings);

        auth.logout();
        AuthenticationException e = assertThrows(AuthenticationException.class, () -> auth.login("jane", "jane123"));
        assertEquals("This account has been deactivated. Please contact RentNGo.", e.getMessage());
        assertNull(auth.getCurrentUser());

        AuthenticationException wrong = assertThrows(AuthenticationException.class, () -> auth.login("jane", "wrong-pass"));
        assertEquals("Invalid username or password", wrong.getMessage(), "a wrong password doesn't reveal the account state");
    }

    @Test
    void deactivatedCustomerCannotBook() throws Exception {
        auth.deactivateCustomer("CUS002", bookings);
        BookingException e = assertThrows(BookingException.class,
                () -> bookings.createBooking("CUS002", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2)));
        assertTrue(e.getMessage().contains("deactivated"), e.getMessage());
        assertTrue(bookings.getBookingsByCustomer("CUS002").isEmpty());
    }

    @Test
    void onlyAnAdminCanDeactivateOrReactivate() throws Exception {
        auth.login("jane", "jane123");
        ValidationException own = assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS002", bookings));
        assertEquals("Only an admin can deactivate customers.", own.getMessage());
        assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS001", bookings));
        assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS001"));
        auth.login("admin", "admin123");
        assertTrue(customer("CUS002").isActive() && customer("CUS001").isActive(), "nothing changed");
    }

    // ===== reactivateCustomer() =====

    @Test
    void reactivatedCustomerCanLogInAndBookAgain() throws Exception {
        auth.deactivateCustomer("CUS002", bookings);
        auth.reactivateCustomer("CUS002");

        assertNotNull(auth.login("jane", "jane123"));
        assertNotNull(bookings.createBooking("CUS002", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2)));
        startStore();
        assertTrue(customer("CUS002").isActive(), "reactivation is saved");
        assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS002"), "already active");
        assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS999"));
    }

    @Test
    void idsAreNeverReusedAndActionsAreAudited() throws Exception {
        auth.login("admin", "admin123");
        Customer added = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001");
        auth.deactivateCustomer(added.getUserId(), bookings);
        auth.reactivateCustomer(added.getUserId());
        Customer next = auth.addCustomer("Ana Reyes", "", "0917-000-0002", "N02-00-000002");
        assertNotEquals(added.getUserId(), next.getUserId());

        var actions = bookings.getAuditLog().entriesNewestFirst().stream().map(AuditLog.Entry::getAction).toList();
        assertTrue(actions.contains(AuditLog.CUSTOMER_DEACTIVATED), actions.toString());
        assertTrue(actions.contains(AuditLog.CUSTOMER_REACTIVATED), actions.toString());
    }
}
