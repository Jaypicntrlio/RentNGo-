package service;

import model.Booking;
import model.PromoCode;
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

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for promo codes (feature 3). */
class PromoCodeTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;
    private PaymentService payments;

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
    }

    /** VEH001 (Camry, ₱2,500/day) for 2 days = ₱5,000 before any discount. */
    private Booking bookCamry(int startOffset, String code) throws Exception {
        return bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 2), code);
    }

    private int usesLeft(String code) {
        return bookings.getPromoCodes().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow().getUsesLeft();
    }

    @Test
    void discountIsTakenOffAndSavedWithTheBooking() throws Exception {
        bookings.addPromoCode("weekend10", 10, TODAY.plusDays(30), 5);
        Booking b = bookCamry(1, " Weekend10 ");

        assertEquals("WEEKEND10", b.getPromoCode(), "codes are case-insensitive and stored in capitals");
        assertEquals(0, new BigDecimal("4500.00").compareTo(b.getTotalCost()));
        assertEquals(0, new BigDecimal("500.00").compareTo(b.getPromoDiscount()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(b.getRentalCostBeforeDiscount()));
        assertEquals(0, new BigDecimal("4500.00").compareTo(b.getBalanceDue()), "the customer pays the discounted amount");
        assertEquals(4, usesLeft("WEEKEND10"));

        open();
        auth.login("admin", "admin123");
        Booking loaded = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals("WEEKEND10", loaded.getPromoCode());
        assertEquals(0, new BigDecimal("500.00").compareTo(loaded.getPromoDiscount()));
        assertEquals(4, usesLeft("WEEKEND10"), "uses left are saved");
    }

    @Test
    void bookingWithoutCodeIsUnchanged() throws Exception {
        Booking b = bookCamry(1, "  ");
        assertNull(b.getPromoCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo(b.getTotalCost()));
    }

    @Test
    void unknownCodeStopsTheBooking() {
        BookingException e = assertThrows(BookingException.class, () -> bookCamry(1, "NOPE"));
        assertTrue(e.getMessage().contains("doesn't exist"));
        assertTrue(bookings.getAllBookings().isEmpty(), "nothing is booked");
    }

    @Test
    void usedUpCodeStopsTheBooking() throws Exception {
        bookings.addPromoCode("ONCE", 20, TODAY.plusDays(5), 1);
        bookCamry(1, "ONCE");
        BookingException e = assertThrows(BookingException.class, () -> bookCamry(10, "ONCE"));
        assertTrue(e.getMessage().contains("used up"));
        assertEquals(1, bookings.getAllBookings().size());
        assertEquals(0, usesLeft("ONCE"));
    }

    @Test
    void codeWorksOnItsExpiryDayButNotAfter() throws Exception {
        bookings.addPromoCode("TODAYONLY", 5, TODAY, 3);
        assertNotNull(bookCamry(1, "TODAYONLY").getPromoCode(), "the expiry date itself still counts");

        // a code that expired yesterday (written to the file, since the screen refuses past dates)
        File file = dataDir.resolve("promocodes.txt").toFile();
        Files.writeString(file.toPath(), Files.readString(file.toPath()) + "OLD10\t10\t" + TODAY.minusDays(1) + "\t50\n");
        open();
        auth.login("admin", "admin123");
        BookingException e = assertThrows(BookingException.class, () -> bookCamry(10, "OLD10"));
        assertTrue(e.getMessage().contains("expired"));
        assertEquals(50, usesLeft("OLD10"), "no use is spent on a refused booking");
    }

    @Test
    void cancellingGivesTheUseBack() throws Exception {
        bookings.addPromoCode("SAVE15", 15, TODAY.plusDays(30), 2);
        Booking b = bookCamry(5, "SAVE15");
        assertEquals(1, usesLeft("SAVE15"));
        payments.cancelBooking(b.getBookingId());
        assertEquals(2, usesLeft("SAVE15"));
    }

    @Test
    void deletedCodeKeepsOldBookingsDiscount() throws Exception {
        bookings.addPromoCode("GONE", 10, TODAY.plusDays(30), 2);
        Booking b = bookCamry(5, "GONE");
        bookings.deletePromoCode("gone");
        assertTrue(bookings.getPromoCodes().isEmpty());
        assertEquals(0, new BigDecimal("4500.00").compareTo(bookings.getBookingById(b.getBookingId()).orElseThrow().getTotalCost()));
        payments.cancelBooking(b.getBookingId()); // no code to give the use back to: still cancels fine
        assertThrows(BookingException.class, () -> bookCamry(10, "GONE"));
    }

    @Test
    void newCodesAreChecked() {
        assertMessage("3 to 20 letters", () -> bookings.addPromoCode("AB", 10, TODAY.plusDays(1), 1));
        assertMessage("3 to 20 letters", () -> bookings.addPromoCode("TEN OFF", 10, TODAY.plusDays(1), 1));
        assertMessage("1% to 90%", () -> bookings.addPromoCode("ZERO", 0, TODAY.plusDays(1), 1));
        assertMessage("1% to 90%", () -> bookings.addPromoCode("FREE", 91, TODAY.plusDays(1), 1));
        assertMessage("past", () -> bookings.addPromoCode("LATE", 10, TODAY.minusDays(1), 1));
        assertMessage("uses", () -> bookings.addPromoCode("NONE", 10, TODAY.plusDays(1), 0));
        assertTrue(bookings.getPromoCodes().isEmpty());
    }

    @Test
    void duplicateCodeIsRefusedWhateverTheCase() throws Exception {
        bookings.addPromoCode("SUMMER", 10, TODAY.plusDays(30), 10);
        assertMessage("already exists", () -> bookings.addPromoCode("summer", 20, TODAY.plusDays(30), 10));
    }

    @Test
    void customersCanUseButNotManageCodes() throws Exception {
        bookings.addPromoCode("FRIENDS", 25, TODAY.plusDays(30), 10);
        auth.logout();
        auth.login("john", "john123");

        PromoCode checked = bookings.checkPromoCode("friends");
        assertEquals(25, checked.getPercentOff());
        assertTrue(bookings.getPromoCodes().isEmpty(), "customers can't list the codes");
        assertThrows(BookingException.class, () -> bookings.addPromoCode("MINE", 90, TODAY.plusDays(30), 100));
        assertThrows(BookingException.class, () -> bookings.deletePromoCode("FRIENDS"));

        String johnId = auth.getCurrentUser().getUserId();
        Booking b = bookings.createBooking(johnId, "VEH002", TODAY.plusDays(1), TODAY.plusDays(2), "FRIENDS");
        assertEquals(0, new BigDecimal("2100.00").compareTo(b.getTotalCost()), "Accord ₱2,800 less 25%");
    }

    @Test
    void receiptShowsTheDiscount() throws Exception {
        bookings.addPromoCode("WEEKEND10", 10, TODAY.plusDays(30), 5);
        Booking b = bookCamry(1, "WEEKEND10");
        String receipt = gui.ReceiptDialog.buildReceipt(b, List.of(), null, "John Doe",
                vehicles.getVehicleById("VEH001").orElseThrow());
        assertTrue(receipt.contains("Promo WEEKEND10"), receipt);
        assertTrue(receipt.contains("5,000.00"), "rental before the discount: " + receipt);
        assertTrue(receipt.contains("-₱500.00") || receipt.contains("₱-500.00"), receipt);
        assertTrue(receipt.contains("4,500.00"), "total after the discount: " + receipt);
    }

    @Test
    void olderBookingFilesWithoutPromoColumnsStillLoad() throws Exception {
        Booking b = bookCamry(1, null);
        File file = dataDir.resolve("bookings.txt").toFile();
        // drop the two promo columns, as in a file saved before this feature
        StringBuilder older = new StringBuilder();
        for (String line : Files.readAllLines(file.toPath())) {
            if (!line.startsWith("#")) line = line.substring(0, line.lastIndexOf('\t', line.lastIndexOf('\t') - 1));
            older.append(line).append('\n');
        }
        Files.writeString(file.toPath(), older.toString());
        open();
        auth.login("admin", "admin123");
        assertNull(bookings.getLoadError());
        Booking loaded = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertNull(loaded.getPromoCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo(loaded.getTotalCost()));
    }

    private static void assertMessage(String fragment, org.junit.jupiter.api.function.Executable action) {
        BookingException e = assertThrows(BookingException.class, action);
        assertTrue(e.getMessage().toLowerCase().contains(fragment.toLowerCase()),
                "message '" + e.getMessage() + "' should mention '" + fragment + "'");
    }
}
