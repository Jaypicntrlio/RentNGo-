package service;

import model.Admin;
import model.Booking;
import model.Payment;
import model.exceptions.AuthenticationException;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for loading damaged data files: bad lines are skipped one at a time, demo data is
 * only created on the first run, unreadable files are never saved over, and booking/payment
 * numbers are never reused.
 */
class DataFileRecoveryTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private File file(String name) {
        return dataDir.resolve(name).toFile();
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(dataDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String accountRow(String role, String id, String username, String password, String name, String extra) {
        return String.join("\t", role, id, username, PasswordHasher.hash(password), name,
                username + "@example.com", "0917-000-0000", extra, "") + "\n";
    }

    private File[] backups(String name) {
        File[] found = dataDir.toFile().listFiles((d, n) -> n.startsWith(name + ".corrupt-"));
        return found == null ? new File[0] : found;
    }

    // ===== Accounts =====

    @Test
    void badSeqLineSkipsOnlyThatLineAndKeepsRealAccounts() throws Exception {
        write("accounts.txt", "# RentNGo accounts\n"
                + "#seq\tCUS\tnot-a-number\n"
                + "#seq\tADM\t5\n"
                + accountRow("ADMIN", "ADM002", "boss", "boss1234", "Real Boss", "SUPER_ADMIN")
                + accountRow("CUSTOMER", "CUS010", "maria", "maria123", "Maria Santos", "N01-23-456789"));

        AuthService auth = new AuthService(new AccountStore(file("accounts.txt")));

        assertFalse(auth.isReadOnly());
        assertNotNull(auth.getLoadError());
        assertTrue(auth.getLoadError().contains("accounts.txt line 2 skipped"), auth.getLoadError());
        assertInstanceOf(Admin.class, auth.login("boss", "boss1234"));
        assertEquals(2, auth.getAllUsers().size(), "both real accounts should load");
        assertThrows(AuthenticationException.class, () -> auth.login("admin", "admin123"), "demo admin must not exist");
        assertEquals(1, backups("accounts.txt").length, "the original file should be backed up");

        // the good #seq line still counts; the bad one falls back to the highest ID in the file
        assertEquals("ADM006", auth.registerAdmin("second", "secret12", "Second Admin", "s@example.com", "",
                "boss", "boss1234").getUserId());
        assertEquals("CUS011", auth.registerCustomer("pedro", "secret12", "Pedro Cruz", "p@example.com", "",
                "N02-00-000001").getUserId());
    }

    @Test
    void noAdminRowsMeansNoDemoAccountsAndFileUnchanged() throws Exception {
        write("accounts.txt", "# RentNGo accounts\n"
                + "#seq\tCUS\t3\n"
                + accountRow("CUSTOMER", "CUS003", "maria", "maria123", "Maria Santos", "N01-23-456789"));
        byte[] before = Files.readAllBytes(dataDir.resolve("accounts.txt"));

        DataStore store = Stores.asAdmin(dataDir);
        AuthService auth = store.getAuthService();

        assertTrue(auth.isReadOnly());
        assertTrue(store.accountsUnavailable());
        assertTrue(store.getLoadProblems().stream().anyMatch(p -> p.contains("no admin account")), store.getLoadProblems().toString());
        // nobody can log in, so check the stored records directly
        assertTrue(auth.findCustomerRecord("CUS003").isPresent(), "the real customer is loaded");
        assertTrue(auth.findCustomerRecord("CUS001").isEmpty(), "no demo accounts");
        assertThrows(AuthenticationException.class, () -> auth.login("admin", "admin123"));
        assertThrows(ValidationException.class, () -> auth.registerCustomer("new1", "secret12", "New Person",
                "n@example.com", "", "N09-99-999999"), "a read-only account file must refuse changes");
        assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS003"));

        assertArrayEquals(before, Files.readAllBytes(dataDir.resolve("accounts.txt")), "accounts.txt must not be rewritten");
        assertEquals(0, backups("accounts.txt").length, "nothing was skipped, so no backup is needed");
    }

    @Test
    void unreadableAccountsFileIsLeftAlone() throws Exception {
        byte[] garbage = {'#', ' ', 'x', '\n', (byte) 0xC3, (byte) 0x28, '\n'}; // invalid UTF-8
        Files.write(dataDir.resolve("accounts.txt"), garbage);

        AuthService auth = new AuthService(new AccountStore(file("accounts.txt")));

        assertTrue(auth.isReadOnly());
        assertTrue(auth.getLoadError().contains("could not be read"), auth.getLoadError());
        assertTrue(auth.getAllUsers().isEmpty());
        assertArrayEquals(garbage, Files.readAllBytes(dataDir.resolve("accounts.txt")));
    }

    @Test
    void demoAccountsAndVehiclesOnlyOnFirstRun() throws Exception {
        DataStore first = Stores.asAdmin(dataDir);
        assertTrue(first.getLoadProblems().isEmpty());
        assertNotNull(first.getAuthService().login("admin", "admin123"));
        assertEquals(7, first.getVehicleService().getAllVehicles().size());

        // a vehicles file that exists with only bad rows must not bring the demo fleet back
        write("vehicles.txt", "# RentNGo vehicles\nCar\tVEH001\tToyota\tCamry\tNOT-A-YEAR\t2500\tABC\ttrue\t\t4\tGas\n");
        DataStore second = Stores.asAdmin(dataDir);
        assertTrue(second.getVehicleService().getAllVehicles().isEmpty());
        assertTrue(second.getLoadProblems().stream().anyMatch(p -> p.contains("vehicles.txt line 2 skipped")));
    }

    // ===== Bookings and payments =====

    private static String bookingRow(String id, String status) {
        return String.join("\t", id, "CUS001", "VEH001", TODAY.plusDays(40).toString(), TODAY.plusDays(42).toString(),
                "5000.00", status, "", "0.00", "0.00", "0.00", "0.00") + "\n";
    }

    @Test
    void badStatusKeepsOtherBookingsAndContinuesNumbering() throws Exception {
        Stores.asAdmin(dataDir); // first run: demo accounts and vehicles
        write("bookings.txt", "# RentNGo bookings\n"
                + "#seq\tBKG\t12\n"
                + bookingRow("BKG0007", "CONFIRMED")
                + bookingRow("BKG0008", "BOGUS")
                + bookingRow("BKG0009", "COMPLETED"));

        DataStore store = Stores.asAdmin(dataDir);
        BookingService bookings = store.getBookingService();

        assertFalse(bookings.isReadOnly());
        assertEquals(2, bookings.getAllBookings().size(), "the two good bookings are kept");
        assertTrue(bookings.getBookingById("BKG0007").isPresent());
        assertTrue(bookings.getBookingById("BKG0009").isPresent());
        assertTrue(bookings.getLoadError().contains("bookings.txt line 4 skipped"), bookings.getLoadError());
        assertEquals(1, backups("bookings.txt").length);

        Booking next = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
        assertEquals("BKG0013", next.getBookingId(), "must continue after the saved #seq, never restart at BKG0001");

        // the number survives a restart through the saved #seq line
        String saved = Files.readString(dataDir.resolve("bookings.txt"));
        assertTrue(saved.contains("#seq\tBKG\t13"), saved);
        BookingService reopened = Stores.asAdmin(dataDir).getBookingService();
        assertEquals("BKG0014", reopened.createBooking("CUS002", "VEH003", TODAY.plusDays(1), TODAY.plusDays(2)).getBookingId());
    }

    @Test
    void skippedRowStillReservesItsNumberWithoutSeqLine() throws Exception {
        Stores.asAdmin(dataDir);
        // an older file without a #seq line, where the highest booking is the bad one
        write("bookings.txt", "# RentNGo bookings\n" + bookingRow("BKG0001", "CONFIRMED") + bookingRow("BKG0002", "BOGUS"));

        BookingService bookings = Stores.asAdmin(dataDir).getBookingService();
        Booking next = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
        assertEquals("BKG0003", next.getBookingId());
    }

    @Test
    void paymentNumbersAreNeverReused() throws Exception {
        Stores.asAdmin(dataDir);
        write("payments.txt", "# RentNGo payments\n"
                + "#seq\tPAY\t20\n"
                + String.join("\t", "PAY0019", "BKG0001", "CUS001", "2500.00", "Cash", "2500.00", "2026-01-05T10:00") + "\n"
                + String.join("\t", "PAY0020", "BKG0002", "CUS001", "not-money", "Cash", "2500.00", "2026-01-06T10:00") + "\n");

        DataStore store = Stores.asAdmin(dataDir);
        assertEquals(1, store.getPaymentService().getAllPayments().size());
        assertTrue(store.getPaymentService().getLoadError().contains("payments.txt line 4 skipped"));

        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2));
        Payment p = store.getPaymentService().processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        assertEquals("PAY0021", p.getPaymentId());
    }

    @Test
    void unreadableBookingsFileIsReadOnly() throws Exception {
        Stores.asAdmin(dataDir);
        byte[] garbage = {(byte) 0xC3, (byte) 0x28, '\n'};
        Files.write(dataDir.resolve("bookings.txt"), garbage);

        DataStore store = Stores.asAdmin(dataDir);
        BookingService bookings = store.getBookingService();

        assertTrue(bookings.isReadOnly());
        assertTrue(store.getLoadProblems().stream().anyMatch(p -> p.contains("bookings.txt could not be read")));
        assertThrows(BookingException.class, () -> bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2)));
        assertThrows(ValidationException.class, () -> store.getPaymentService().processPayment("BKG0001", "CUS001",
                java.math.BigDecimal.ONE, "Cash", java.math.BigDecimal.ONE));
        assertArrayEquals(garbage, Files.readAllBytes(dataDir.resolve("bookings.txt")));
    }
}
