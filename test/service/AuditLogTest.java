package service;

import model.Booking;
import model.BookingStatus;
import model.Car;
import model.Money;
import model.Payment;
import model.exceptions.BookingException;
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
 * JUnit 5 tests for the audit log and the backdated-return rule: a return dated other than today
 * needs a reason, and every return records who processed it.
 */
class AuditLogTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String REASON = "Customer left the car at the branch on the end date; key box receipt #4471";

    @TempDir
    Path dataDir;

    /** A store with an overdue rental: VEH001 (Toyota Camry, 2,500/day) ended 10 days ago and is still out. */
    private DataStore storeWithOverdueRental() throws Exception {
        Stores.asAdmin(dataDir); // first run: demo accounts and vehicles
        Files.writeString(dataDir.resolve("bookings.txt"), "# bookings\n"
                + String.join("\t", "BKG0001", "CUS001", "VEH001", TODAY.minusDays(13).toString(), TODAY.minusDays(10).toString(),
                "7500.00", "ACTIVE", "", "0.00", "0.00", "0.00", "0.00") + "\n");
        DataStore store = Stores.asAdmin(dataDir);
        store.getAuthService().login("admin", "admin123");
        return store;
    }

    private List<AuditLog.Entry> entries(DataStore store, String action) {
        return store.getBookingService().getAuditLog().entriesNewestFirst().stream()
                .filter(e -> e.getAction().equals(action))
                .collect(Collectors.toList());
    }

    // ===== Backdated returns =====

    @Test
    void backdatedReturnWithoutReasonFails() throws Exception {
        DataStore store = storeWithOverdueRental();
        BookingService bookings = store.getBookingService();
        LocalDate endDate = TODAY.minusDays(10);

        for (String reason : new String[]{null, "", "   ", "too short"}) { // "too short" is 9 characters
            BookingException e = assertThrows(BookingException.class,
                    () -> bookings.returnBooking("BKG0001", endDate, BigDecimal.ZERO, reason));
            assertTrue(e.getMessage().contains("reason"), e.getMessage());
        }
        assertThrows(BookingException.class, () -> bookings.returnBooking("BKG0001", endDate, BigDecimal.ZERO),
                "the old 3-argument call has no reason either");

        Booking b = bookings.getBookingById("BKG0001").orElseThrow();
        assertEquals(BookingStatus.ACTIVE, b.getStatus(), "nothing may change when the reason is missing");
        assertEquals(0, b.getLateFee().signum());
        assertTrue(entries(store, AuditLog.RETURNED).isEmpty());
    }

    @Test
    void backdatedReturnWithReasonWorksAndIsLogged() throws Exception {
        DataStore store = storeWithOverdueRental();
        LocalDate endDate = TODAY.minusDays(10);

        Booking b = store.getBookingService().returnBooking("BKG0001", endDate, BigDecimal.ZERO, "  " + REASON + "  ");

        assertEquals(BookingStatus.COMPLETED, b.getStatus());
        assertEquals(0, b.getLateFee().signum(), "returned on the end date, so no late fee");
        assertEquals("ADM001", b.getProcessedBy());
        assertEquals(TODAY, b.getProcessedAt().toLocalDate());
        assertEquals(REASON, b.getReturnNote(), "the reason is saved, trimmed");

        List<AuditLog.Entry> returned = entries(store, AuditLog.RETURNED);
        assertEquals(1, returned.size());
        AuditLog.Entry line = returned.get(0);
        assertEquals("ADM001", line.getUserId());
        assertEquals("BKG0001", line.getBookingId());
        assertTrue(line.getDetails().contains("return date " + endDate), line.getDetails());
        assertTrue(line.getDetails().contains("processed on " + TODAY), line.getDetails());
        assertTrue(line.getDetails().contains("late fee ₱0.00"), line.getDetails());
        assertTrue(line.getDetails().contains("reason: " + REASON), line.getDetails());

        // processedBy, processedAt and the reason survive a restart
        Booking reloaded = Stores.asAdmin(dataDir).getBookingService().getBookingById("BKG0001").orElseThrow();
        assertEquals("ADM001", reloaded.getProcessedBy());
        assertEquals(b.getProcessedAt(), reloaded.getProcessedAt());
        assertEquals(REASON, reloaded.getReturnNote());
    }

    @Test
    void returnTodayNeedsNoReasonAndLogsTheFullLateFee() throws Exception {
        DataStore store = storeWithOverdueRental();

        Booking b = store.getBookingService().returnBooking("BKG0001", TODAY, new BigDecimal("500"), null);

        assertMoney("37500.00", b.getLateFee()); // 10 days x 1.5 x 2,500
        assertNull(b.getReturnNote());
        String details = entries(store, AuditLog.RETURNED).get(0).getDetails();
        assertTrue(details.contains("late fee ₱37,500.00 (10 days late)"), details);
        assertTrue(details.contains("additional charge ₱500.00"), details);
        assertFalse(details.contains("reason"), details);
    }

    @Test
    void returnWithPaymentPassesTheReasonAndLogsThePayment() throws Exception {
        DataStore store = storeWithOverdueRental();
        LocalDate dropOff = TODAY.minusDays(8); // 2 days late
        BigDecimal due = store.getBookingService().previewAmountDue(
                store.getBookingService().getBookingById("BKG0001").orElseThrow(), dropOff, BigDecimal.ZERO);

        assertThrows(BookingException.class, () ->
                store.getPaymentService().completeReturn("BKG0001", dropOff, BigDecimal.ZERO, "Cash", due));
        assertTrue(store.getPaymentService().getAllPayments().isEmpty(), "no payment without a reason");

        Payment p = store.getPaymentService().completeReturn("BKG0001", dropOff, BigDecimal.ZERO, REASON, "Cash", due);

        assertNotNull(p);
        assertEquals(REASON, store.getBookingService().getBookingById("BKG0001").orElseThrow().getReturnNote());
        AuditLog.Entry payment = entries(store, AuditLog.PAYMENT).get(0);
        assertEquals("ADM001", payment.getUserId());
        assertTrue(payment.getDetails().startsWith(p.getPaymentId() + " ₱"), payment.getDetails());
    }

    @Test
    void oldBookingsFileWithoutNewColumnsStillLoads() throws Exception {
        DataStore store = storeWithOverdueRental(); // its file has only the 12 original columns
        Booking b = store.getBookingService().getBookingById("BKG0001").orElseThrow();
        assertNull(b.getProcessedBy());
        assertNull(b.getProcessedAt());
        assertNull(b.getReturnNote());
        assertNull(store.getBookingService().getLoadError());
    }

    // ===== Other actions =====

    @Test
    void everyAdminAndBookingActionIsLogged() throws Exception {
        DataStore store = Stores.asAdmin(dataDir);
        AuthService auth = store.getAuthService();
        VehicleService vehicles = store.getVehicleService();
        BookingService bookings = store.getBookingService();
        PaymentService payments = store.getPaymentService();

        auth.registerAdmin("second", "secret12", "Second Admin", "s@example.com", "", "admin", "admin123");
        auth.registerCustomer("maria", "secret12", "Maria Santos", "m@example.com", "", "N01-23-456789");

        auth.login("admin", "admin123");
        vehicles.addVehicle(new Car("VEH100", "Toyota", "Vios", 2024, Money.of(1800), "NEW-0001", 4, "Gasoline"));
        vehicles.updateVehicle("VEH100", new Car("VEH100", "Toyota", "Vios", 2024, Money.of(1500), "NEW-0001", 4, "Gasoline"));
        vehicles.deleteVehicle("VEH100", List.of());
        var added = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001");
        auth.updateCustomer(added.getUserId(), "Pedro Cruz Jr.", "", "0917-000-0001", "N02-00-000001");
        auth.deactivateCustomer(added.getUserId(), bookings);
        auth.reactivateCustomer(added.getUserId());

        Booking now = bookings.createBooking("CUS001", "VEH002", TODAY, TODAY.plusDays(1));
        payments.processPayment(now.getBookingId(), "CUS001", now.getBalanceDue(), "Cash", now.getBalanceDue());
        bookings.markPickedUp(now.getBookingId());
        bookings.returnBooking(now.getBookingId(), TODAY, BigDecimal.ZERO, null);

        auth.logout();
        auth.login("john", "john123");
        Booking later = bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(5), TODAY.plusDays(6)); // john books
        auth.login("admin", "admin123");
        payments.processPayment(later.getBookingId(), "CUS001", later.getBalanceDue(), "GCash", later.getBalanceDue());
        auth.login("john", "john123");
        payments.cancelBooking(later.getBookingId()); // john cancels his own booking

        List<AuditLog.Entry> log = bookings.getAuditLog().entriesNewestFirst();
        List<String> actions = log.stream().map(AuditLog.Entry::getAction).collect(Collectors.toList());
        for (String action : new String[]{AuditLog.ADMIN_CREATED, AuditLog.CUSTOMER_REGISTERED, AuditLog.VEHICLE_ADDED,
                AuditLog.VEHICLE_EDITED, AuditLog.VEHICLE_DELETED, AuditLog.CUSTOMER_ADDED, AuditLog.CUSTOMER_EDITED,
                AuditLog.CUSTOMER_DEACTIVATED, AuditLog.CUSTOMER_REACTIVATED, AuditLog.BOOKING_CREATED, AuditLog.PAYMENT, AuditLog.PICKED_UP,
                AuditLog.RETURNED, AuditLog.CANCELLED, AuditLog.REFUND}) {
            assertTrue(actions.contains(action), action + " missing from " + actions);
        }
        assertEquals(AuditLog.REFUND, log.get(0).getAction(), "newest first");

        AuditLog.Entry adminCreated = log.stream().filter(e -> e.getAction().equals(AuditLog.ADMIN_CREATED)).findFirst().orElseThrow();
        assertEquals("ADM001", adminCreated.getUserId(), "recorded under the approving admin");
        AuditLog.Entry edited = log.stream().filter(e -> e.getAction().equals(AuditLog.VEHICLE_EDITED)).findFirst().orElseThrow();
        assertTrue(edited.getDetails().contains("price/day 1800.00 -> 1500.00"), edited.getDetails());
        AuditLog.Entry customerBooking = log.stream()
                .filter(e -> e.getAction().equals(AuditLog.BOOKING_CREATED) && e.getBookingId().equals(later.getBookingId()))
                .findFirst().orElseThrow();
        assertEquals("CUS001", customerBooking.getUserId(), "a customer's own booking is recorded under the customer");
    }

    @Test
    void auditFileIsOnlyAppendedTo() throws Exception {
        DataStore store = Stores.asAdmin(dataDir);
        store.getAuthService().login("admin", "admin123");
        store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2));
        String before = Files.readString(dataDir.resolve("audit.txt"));

        store.getBookingService().createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
        Stores.asAdmin(dataDir); // restarting must not rewrite it
        String after = Files.readString(dataDir.resolve("audit.txt"));

        assertTrue(after.startsWith(before), "earlier lines must be kept exactly as they were");
        assertTrue(after.length() > before.length());
        assertEquals(1, after.lines().filter(l -> l.startsWith("# ")).count(), "one header only");
    }

    @Test
    void tabsAndNewlinesInDetailsAreEscaped() throws Exception {
        AuditLog log = new AuditLog(dataDir.resolve("audit.txt").toFile(), () -> null);
        log.recordAs("ADM001", AuditLog.RETURNED, "BKG0009", "line one\nline two\twith tab");
        assertEquals(2, Files.readAllLines(dataDir.resolve("audit.txt")).size(), "header + one line");
        assertEquals("line one\nline two\twith tab", log.entriesNewestFirst().get(0).getDetails());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
