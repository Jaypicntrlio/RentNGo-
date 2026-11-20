package service;

import model.Booking;
import model.BookingStatus;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for loyalty points (feature 4). */
class LoyaltyPointsTest {

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

    /** Opens the services on dataDir, like starting the app again. */
    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
    }

    private int johnsPoints() {
        return auth.findCustomerRecord("CUS001").orElseThrow().getLoyaltyPoints();
    }

    private void pay(Booking b) throws Exception {
        Booking now = bookings.getBookingById(b.getBookingId()).orElseThrow();
        payments.processPayment(b.getBookingId(), "CUS001", now.getBalanceDue(), "Cash", now.getBalanceDue());
    }

    /** VEH001 (Camry, ₱2,500/day) for {@code days} days from today. */
    private Booking bookCamry(int days, int points) throws Exception {
        return bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(days), null, points, JOHNS_PASSWORD);
    }

    private List<String> auditActions() {
        return audit.entriesNewestFirst().stream().map(AuditLog.Entry::getAction).collect(Collectors.toList());
    }

    @Test
    void earningAndSpendingRules() {
        assertEquals(0, BookingService.pointsEarnedFor(new BigDecimal("99.99")));
        assertEquals(1, BookingService.pointsEarnedFor(new BigDecimal("100")));
        assertEquals(45, BookingService.pointsEarnedFor(new BigDecimal("4599.50")));
        assertEquals(400, BookingService.maxPointsUsable(450, new BigDecimal("5000")), "steps of 100, at most the balance");
        assertEquals(200, BookingService.maxPointsUsable(450, new BigDecimal("299.99")), "never more than the cost");
        assertEquals(0, BookingService.maxPointsUsable(99, new BigDecimal("5000")));
    }

    @Test
    void prepaidRentalEarnsPointsWhenReturned() throws Exception {
        Booking b = bookCamry(2, 0); // ₱5,000
        assertEquals(0, johnsPoints(), "nothing is earned at booking");
        pay(b);
        assertEquals(0, johnsPoints(), "nothing is earned before the rental is completed");
        bookings.markPickedUp(b.getBookingId());
        Booking returned = bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);

        assertEquals(50, returned.getLoyaltyPointsEarned());
        assertEquals(50, johnsPoints());
        assertTrue(auditActions().contains(AuditLog.LOYALTY_EARNED));

        open();
        assertEquals(50, johnsPoints(), "points are saved with the account");
        assertEquals(50, bookings.find(b.getBookingId()).orElseThrow().getLoyaltyPointsEarned());
    }

    @Test
    void rentalReturnedUnpaidEarnsWhenPaidAndOnlyOnce() throws Exception {
        Booking b = bookCamry(1, 0); // ₱2,500
        bookings.markPickedUp(b.getBookingId());
        bookings.returnBooking(b.getBookingId(), TODAY, new BigDecimal("650")); // + extra charge, not paid yet
        assertEquals(0, johnsPoints(), "completed but not paid: no points yet");

        pay(b); // ₱3,150 paid
        assertEquals(31, johnsPoints());
        bookings.awardLoyaltyPointsIfDue(b.getBookingId());
        assertEquals(31, johnsPoints(), "never given twice");
    }

    @Test
    void spendingPointsLowersTheCostAndCancellingGivesThemBack() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 450);
        Booking b = bookCamry(2, 300);

        assertEquals(0, new BigDecimal("4700.00").compareTo(b.getTotalCost()));
        assertEquals(300, b.getLoyaltyPointsUsed());
        assertEquals(0, new BigDecimal("5000.00").compareTo(b.getRentalCostBeforeDiscount()));
        assertEquals(150, johnsPoints());
        assertTrue(auditActions().contains(AuditLog.LOYALTY_SPENT));

        payments.cancelBooking(b.getBookingId());
        assertEquals(450, johnsPoints());
        assertTrue(auditActions().contains(AuditLog.LOYALTY_RETURNED));
    }

    @Test
    void badPointAmountsStopTheBookingAndSpendNothing() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 450);
        assertMessage("steps of 100", () -> bookCamry(2, 150));
        assertMessage("only 450", () -> bookCamry(2, 500));
        assertMessage("steps of 100", () -> bookCamry(2, -100));
        assertTrue(bookings.getAllBookings().isEmpty());
        assertEquals(450, johnsPoints());

        auth.changeLoyaltyPoints("CUS001", 5000);
        assertMessage("at most 2500", () -> bookCamry(1, 2600)); // costs ₱2,500
    }

    @Test
    void promoComesFirstThenPoints() throws Exception {
        bookings.addPromoCode("TEN", 10, TODAY.plusDays(10), 5);
        auth.changeLoyaltyPoints("CUS001", 500);
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2), "TEN", 500, JOHNS_PASSWORD);
        // ₱5,000 - 10% = ₱4,500, then - ₱500 of points
        assertEquals(0, new BigDecimal("4000.00").compareTo(b.getTotalCost()));
        String receipt = gui.ReceiptDialog.buildReceipt(b, List.of(), null, "John Doe", vehicles.getVehicleById("VEH001").orElseThrow());
        assertTrue(receipt.contains("Loyalty points (500)"), receipt);
        assertTrue(receipt.contains("5,000.00"), receipt);
    }

    @Test
    void rentalPaidFullyWithPointsCanBeReturned() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 2500);
        Booking b = bookCamry(1, 2500);
        assertEquals(0, b.getTotalCost().signum());
        bookings.markPickedUp(b.getBookingId());
        Booking returned = bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        assertEquals(BookingStatus.COMPLETED, returned.getStatus());
        assertEquals(0, returned.getLoyaltyPointsEarned(), "nothing paid, nothing earned");
        assertEquals(0, johnsPoints());
    }

    @Test
    void customersSeeOnlyTheirOwnPoints() throws Exception {
        auth.changeLoyaltyPoints("CUS001", 700);
        auth.logout();
        auth.login("john", "john123");
        assertEquals(700, bookings.getLoyaltyPoints("CUS001"));
        assertEquals(0, bookings.getLoyaltyPoints("CUS002"), "another customer's balance is not shown");

        Booking own = bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2), null, 200);
        assertEquals(200, own.getLoyaltyPointsUsed(), "customers can spend their own points");
        assertEquals(500, bookings.getLoyaltyPoints("CUS001"));
    }

    @Test
    void olderFilesWithoutPointColumnsStillLoad() throws Exception {
        Booking b = bookCamry(1, 0);
        for (String name : new String[]{"accounts.txt", "bookings.txt"}) {
            File file = dataDir.resolve(name).toFile();
            int drop = name.equals("accounts.txt") ? 1 : 2; // the new columns are the last ones
            StringBuilder older = new StringBuilder();
            for (String line : Files.readAllLines(file.toPath())) {
                if (!line.startsWith("#")) {
                    for (int i = 0; i < drop; i++) line = line.substring(0, line.lastIndexOf('\t'));
                }
                older.append(line).append('\n');
            }
            Files.writeString(file.toPath(), older.toString());
        }
        open();
        assertNull(auth.getLoadError());
        assertNull(bookings.getLoadError());
        auth.login("admin", "admin123");
        assertEquals(0, johnsPoints());
        assertEquals(0, bookings.getBookingById(b.getBookingId()).orElseThrow().getLoyaltyPointsUsed());
    }

    private static void assertMessage(String fragment, org.junit.jupiter.api.function.Executable action) {
        BookingException e = assertThrows(BookingException.class, action);
        assertTrue(e.getMessage().toLowerCase().contains(fragment.toLowerCase()),
                "message '" + e.getMessage() + "' should mention '" + fragment + "'");
    }
}
