package service;

import model.Booking;
import model.PromoCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #8 from the loophole review: every promo code has a permanent ID that bookings remember, so a
 * code deleted and created again with the same name is a different code. Cancelling an old booking
 * no longer gives a use to the new code, and a customer who used the old code can use the new one.
 */
class PromoCodeIdTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("admin", "admin123");
    }

    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
    }

    private PromoCode code(String name) {
        return bookings.getPromoCodes().stream().filter(c -> c.getCode().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void codesGetIdsThatAreNeverReused() throws Exception {
        assertEquals("PRM0001", bookings.addPromoCode("FLASH", 20, TODAY.plusDays(30), 1).getId());
        bookings.deletePromoCode("FLASH");
        assertEquals("PRM0002", bookings.addPromoCode("FLASH", 50, TODAY.plusDays(30), 1).getId(), "a new code, not the old one");
        open();
        auth.login("admin", "admin123");
        assertEquals("PRM0002", code("FLASH").getId(), "kept after a restart");
        assertEquals("PRM0003", bookings.addPromoCode("OTHER", 5, TODAY.plusDays(30), 1).getId());
    }

    @Test
    void bookingRemembersTheCodesId() throws Exception {
        bookings.addPromoCode("SAVE10", 10, TODAY.plusDays(30), 5);
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(6), "SAVE10");
        assertEquals("PRM0001", b.getPromoId());
        open();
        auth.login("admin", "admin123");
        assertEquals("PRM0001", bookings.getBookingById(b.getBookingId()).orElseThrow().getPromoId());
    }

    @Test
    void cancellingAnOldBookingDoesntGiveAUseToARecreatedCode() throws Exception {
        bookings.addPromoCode("FLASH", 20, TODAY.plusDays(30), 1);
        Booking old = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(6), "FLASH");
        bookings.deletePromoCode("FLASH");
        bookings.addPromoCode("FLASH", 50, TODAY.plusDays(30), 1);

        payments.cancelBooking(old.getBookingId());
        assertEquals(1, code("FLASH").getUsesLeft(), "the new FLASH keeps the 1 use it was created with (was 2 before the fix)");
    }

    @Test
    void cancellingStillGivesTheUseBackToTheSameCode() throws Exception {
        bookings.addPromoCode("FLASH", 20, TODAY.plusDays(30), 1);
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(6), "FLASH");
        assertEquals(0, code("FLASH").getUsesLeft());
        payments.cancelBooking(b.getBookingId());
        assertEquals(1, code("FLASH").getUsesLeft());
    }

    @Test
    void customerWhoUsedTheOldCodeCanUseTheNewOne() throws Exception {
        bookings.addPromoCode("FLASH", 20, TODAY.plusDays(30), 5);
        bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(6), "FLASH");
        bookings.deletePromoCode("FLASH");
        bookings.addPromoCode("FLASH", 50, TODAY.plusDays(30), 5);

        Booking again = bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(8), TODAY.plusDays(9), "FLASH");
        assertEquals("PRM0002", again.getPromoId(), "the one-use-per-customer rule (fix #7) is per code, not per name");
    }

    @Test
    void codesSavedBeforeIdsGetOneAndOldBookingsStillWork() throws Exception {
        // a promocodes.txt and a booking from before this fix: no ID column anywhere
        Files.writeString(dataDir.resolve("promocodes.txt"),
                "# RentNGo promo codes - code\tpercentOff\texpiryDate\tusesLeft\nOLD10\t10\t" + TODAY.plusDays(30) + "\t3\n");
        open();
        auth.login("admin", "admin123");
        assertEquals("PRM0001", code("OLD10").getId());
        assertNull(auth.getLoadError());

        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(6), "OLD10");
        Path f = dataDir.resolve("bookings.txt"); // drop the promoId column and later ones, as in an older file
        StringBuilder older = new StringBuilder();
        for (String line : Files.readAllLines(f)) {
            // keep the first 20 columns: promoId (column 21) and everything added after it are dropped
            older.append(line.startsWith("#") ? line
                    : String.join("\t", java.util.Arrays.copyOf(line.split("\t", -1), 20))).append('\n');
        }
        Files.writeString(f, older.toString());
        open();
        auth.login("admin", "admin123");
        assertNull(bookings.getBookingById(b.getBookingId()).orElseThrow().getPromoId());
        payments.cancelBooking(b.getBookingId());
        assertEquals(3, code("OLD10").getUsesLeft(), "an older booking still gives its use back, by name");
    }

    @Test
    void duplicateNamesInAnOldFileAreReported() throws Exception {
        Files.writeString(dataDir.resolve("promocodes.txt"), "# RentNGo promo codes\nDUP\t10\t" + TODAY.plusDays(9)
                + "\t1\nDUP\t20\t" + TODAY.plusDays(9) + "\t1\n");
        open();
        assertNotNull(bookings.getLoadError());
        assertTrue(bookings.getLoadError().contains("listed twice"));
    }
}
