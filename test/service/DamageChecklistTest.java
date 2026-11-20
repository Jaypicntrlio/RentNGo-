package service;

import model.Booking;
import model.BookingStatus;
import model.VehicleCondition;
import model.VehicleCondition.Fuel;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for the damage checklist at pick-up and return (feature 8). */
class DamageChecklistTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final VehicleCondition PERFECT = new VehicleCondition(false, false, Fuel.FULL, true, null);
    private static final VehicleCondition SCRATCHED_HALF_TANK =
            new VehicleCondition(true, false, Fuel.HALF, true, "long scratch on the driver's door");

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
    }

    /** Opens the services on dataDir, like starting the app again, and logs in as admin. */
    private void open() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
        auth.login("admin", "admin123");
    }

    /** VEH001 (Camry, ₱2,500/day) from today for 2 days, prepaid, picked up with the given checklist. */
    private Booking pickedUp(VehicleCondition atPickUp) throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        return bookings.markPickedUp(b.getBookingId(), atPickUp);
    }

    private List<AuditLog.Entry> entries(String action) {
        return audit.entriesNewestFirst().stream().filter(e -> e.getAction().equals(action)).collect(Collectors.toList());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertAmount(expected, actual, "");
    }

    private static void assertAmount(String expected, BigDecimal actual, String why) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), why + " (was " + actual + ")");
    }

    // ===== The checklist itself =====

    @Test
    void newDamageAndSuggestedCharges() {
        assertEquals(List.of(), VehicleCondition.newProblems(PERFECT, PERFECT));
        assertAmount("0", VehicleCondition.suggestedCharge(PERFECT, PERFECT));

        VehicleCondition everythingWorse = new VehicleCondition(true, true, Fuel.QUARTER, false, null);
        assertEquals(List.of("New scratches", "New dents", "Fuel lower: Full → 1/4", "Needs cleaning"),
                VehicleCondition.newProblems(PERFECT, everythingWorse));
        // ₱1,500 scratch + ₱3,000 dent + 3 quarters x ₱500 + ₱500 cleaning
        assertAmount("6500", VehicleCondition.suggestedCharge(PERFECT, everythingWorse));

        VehicleCondition alreadyScratchedHalfTank = new VehicleCondition(true, false, Fuel.HALF, true, null);
        assertEquals(List.of(), VehicleCondition.newProblems(alreadyScratchedHalfTank, SCRATCHED_HALF_TANK),
                "damage that was there at pick-up is not charged again");
        assertAmount("0", VehicleCondition.suggestedCharge(alreadyScratchedHalfTank, PERFECT), "more fuel is not a refund");
        assertAmount("0", VehicleCondition.suggestedCharge(null, everythingWorse), "nothing to compare with");
    }

    @Test
    void checklistIsCheckedAndSavedSafely() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleCondition(false, false, null, true, null),
                "the fuel level must be checked");
        assertThrows(IllegalArgumentException.class,
                () -> new VehicleCondition(false, false, Fuel.FULL, true, "x".repeat(VehicleCondition.MAX_NOTES_LENGTH + 1)));

        VehicleCondition tricky = new VehicleCondition(true, false, Fuel.THREE_QUARTERS, false, " chip | crack\tnear\nmirror ");
        assertEquals("chip crack near mirror", tricky.getNotes(), "tabs, line breaks and | can't break the data file");
        assertEquals(tricky, VehicleCondition.decode(tricky.encode()));
        assertEquals(PERFECT, VehicleCondition.decode(PERFECT.encode()));
        assertNull(VehicleCondition.decode(""));
        assertEquals("Full tank, clean, no damage", PERFECT.summary());
        assertEquals("1/2 tank, clean, scratches", SCRATCHED_HALF_TANK.summary());
    }

    // ===== Pick-up and return =====

    @Test
    void bothChecklistsAreSavedWithTheBooking() throws Exception {
        Booking b = pickedUp(PERFECT);
        assertEquals(PERFECT, b.getPickUpCondition());
        bookings.returnBooking(b.getBookingId(), TODAY, new BigDecimal("2500"), null, SCRATCHED_HALF_TANK);

        open(); // restart
        Booking saved = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(PERFECT, saved.getPickUpCondition());
        assertEquals(SCRATCHED_HALF_TANK, saved.getReturnCondition());
        assertEquals(BookingStatus.COMPLETED, saved.getStatus());
        assertTrue(entries(AuditLog.PICKED_UP).get(0).getDetails().contains("checklist: Full tank, clean, no damage"));
    }

    @Test
    void returnCantBeFinishedWithoutTheChecklist() throws Exception {
        Booking b = pickedUp(PERFECT);
        BookingException e = assertThrows(BookingException.class,
                () -> bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO));
        assertEquals(BookingService.RETURN_CHECKLIST_REQUIRED, e.getMessage());
        assertThrows(BookingException.class,
                () -> payments.completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, "Cash", BigDecimal.ZERO));
        assertThrows(BookingException.class,
                () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.COMPLETED), "no way around it");
        assertEquals(BookingStatus.ACTIVE, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());

        payments.completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, null, PERFECT, "Cash", BigDecimal.ZERO);
        assertEquals(BookingStatus.COMPLETED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void rentalsPickedUpBeforeThisFeatureCanStillBeReturned() throws Exception {
        Booking b = pickedUp(null); // no pick-up checklist, like a rental already out when the update was installed
        Booking done = bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        assertEquals(BookingStatus.COMPLETED, done.getStatus());
        assertNull(done.getReturnCondition());
    }

    // ===== Damage charge and audit =====

    @Test
    void newDamageIsChargedAndAudited() throws Exception {
        Booking b = pickedUp(PERFECT);
        BigDecimal suggested = VehicleCondition.suggestedCharge(PERFECT, SCRATCHED_HALF_TANK); // ₱1,500 + 2 x ₱500
        assertAmount("2500", suggested);

        payments.completeReturn(b.getBookingId(), TODAY, suggested, null, SCRATCHED_HALF_TANK, "Cash", suggested);
        Booking done = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertAmount("2500", done.getAdditionalCharge());
        assertTrue(done.isPaid());

        List<AuditLog.Entry> damage = entries(AuditLog.DAMAGE_FOUND);
        assertEquals(1, damage.size());
        String details = damage.get(0).getDetails();
        assertEquals(b.getBookingId(), damage.get(0).getBookingId());
        assertTrue(details.contains("New scratches"), details);
        assertTrue(details.contains("Fuel lower: Full → 1/2"), details);
        assertTrue(details.contains("suggested charge ₱2,500.00"), details);
        assertTrue(details.contains("additional charge ₱2,500.00"), details);
        assertTrue(details.contains("long scratch on the driver's door"), details);
    }

    @Test
    void adminCanChargeLessAndItIsStillAudited() throws Exception {
        Booking b = pickedUp(PERFECT);
        bookings.returnBooking(b.getBookingId(), TODAY, new BigDecimal("1000"), null, SCRATCHED_HALF_TANK);
        String details = entries(AuditLog.DAMAGE_FOUND).get(0).getDetails();
        assertTrue(details.contains("suggested charge ₱2,500.00, additional charge ₱1,000.00"), details);
    }

    @Test
    void noDamageMeansNoDamageEntry() throws Exception {
        Booking b = pickedUp(SCRATCHED_HALF_TANK);
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO, null, SCRATCHED_HALF_TANK);
        assertTrue(entries(AuditLog.DAMAGE_FOUND).isEmpty());
    }

    @Test
    void returnTakenBackWhenPaymentFailsDropsTheChecklist() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1)); // not paid yet
        bookings.markPickedUp(b.getBookingId(), PERFECT);
        Path p = dataDir.resolve("payments.txt");
        Files.writeString(p, (Files.exists(p) ? Files.readString(p) : "") + "# changed elsewhere\n"); // the payment can't be saved
        assertThrows(ValidationException.class, () -> payments.completeReturn(b.getBookingId(), TODAY, new BigDecimal("2500"),
                null, SCRATCHED_HALF_TANK, "Cash", new BigDecimal("5000")));
        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.ACTIVE, after.getStatus());
        assertNull(after.getReturnCondition(), "the return, and its checklist, were taken back");
        assertEquals(PERFECT, after.getPickUpCondition());
    }

    // ===== Receipt and older files =====

    @Test
    void receiptShowsBothChecklistsSideBySide() throws Exception {
        Booking b = pickedUp(PERFECT);
        Booking done = bookings.returnBooking(b.getBookingId(), TODAY, new BigDecimal("2500"), null, SCRATCHED_HALF_TANK);
        String receipt = gui.ReceiptDialog.buildReceipt(done, payments.getPaymentsByBookingId(b.getBookingId()), null,
                "John Doe", vehicles.getVehicleById("VEH001").orElseThrow());
        assertTrue(receipt.contains("Condition       Pick-up     Return"), receipt);
        assertTrue(receipt.contains("Scratches       No          Yes *"), receipt);
        assertTrue(receipt.contains("Dents           No          No"), receipt);
        assertTrue(receipt.contains("Fuel            Full        1/2 *"), receipt);
        assertTrue(receipt.contains("* new since pick-up"), receipt);
        assertTrue(receipt.contains("Return note: long scratch on the driver's"), receipt);
        assertTrue(receipt.contains("  door"), receipt);
    }

    @Test
    void receiptWithoutChecklistsHasNoConditionSection() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(3), TODAY.plusDays(4));
        String receipt = gui.ReceiptDialog.buildReceipt(b, List.of(), null, "John Doe", null);
        assertFalse(receipt.contains("Pick-up"), receipt);
    }

    @Test
    void olderBookingFilesWithoutChecklistColumnsStillLoad() throws Exception {
        Booking b = pickedUp(PERFECT);
        File file = dataDir.resolve("bookings.txt").toFile();
        StringBuilder older = new StringBuilder();
        for (String line : Files.readAllLines(file.toPath())) {
            if (!line.startsWith("#")) {
                for (int i = 0; i < 2; i++) line = line.substring(0, line.lastIndexOf('\t')); // the 2 checklist columns
            }
            older.append(line).append('\n');
        }
        Files.writeString(file.toPath(), older.toString());
        open();
        assertNull(bookings.getLoadError());
        Booking loaded = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertNull(loaded.getPickUpCondition());
        assertEquals(BookingStatus.ACTIVE, loaded.getStatus());
    }
}
