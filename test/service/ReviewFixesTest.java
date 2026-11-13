package service;

import model.Booking;
import model.BookingStatus;
import model.Car;
import model.Customer;
import model.Money;
import model.Payment;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the fixes from the code review: a refused save never leaves money or a
 * return half-recorded, vehicle deletes can't be tricked, and new customers get random passwords.
 */
class ReviewFixesTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dir;

    private Path dataDir;
    private Path imagesDir;
    private DataStore store;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = Files.createDirectories(dir.resolve("data"));
        imagesDir = Files.createDirectories(dir.resolve("images"));
        store = open();
        TsvFile.setChangedListener(e -> { }); // keep the "changed on disk" message out of the test output
    }

    @AfterEach
    void resetListener() {
        TsvFile.setChangedListener(null);
    }

    private DataStore open() {
        DataStore s = new DataStore(dataDir.toFile(), imagesDir.toFile());
        try {
            s.getAuthService().login("admin", "admin123");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return s;
    }

    /** Another program writes to the file, so this copy's next save of it is refused. */
    private void editedElsewhere(String file) throws IOException {
        Files.writeString(dataDir.resolve(file), "# edited elsewhere\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Booking paidBooking() throws Exception {
        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7));
        store.getPaymentService().processPayment(b.getBookingId(), "CUS001", b.getTotalCost(), "Cash", b.getTotalCost());
        return b;
    }

    private Booking current(String bookingId) {
        return store.getBookingService().getBookingById(bookingId).orElseThrow();
    }

    // ===== Refunds and payments stay in step with the booking =====

    @Test
    void cancelRefusedOnBookingsFileRecordsNoRefund() throws Exception {
        Booking b = paidBooking();
        editedElsewhere("bookings.txt");

        assertThrows(BookingException.class, () -> store.getPaymentService().cancelBooking(b.getBookingId()));
        assertEquals(BookingStatus.CONFIRMED, current(b.getBookingId()).getStatus(), "the cancel is undone");
        assertTrue(store.getPaymentService().getAllPayments().stream().noneMatch(p -> p.getAmount().signum() < 0),
                "no refund row for a booking that is still open");
        assertFalse(Files.readString(dataDir.resolve("payments.txt")).contains(PaymentService.REFUND_METHOD));
    }

    @Test
    void cancelRefusedOnPaymentsFilePutsTheBookingBack() throws Exception {
        Booking b = paidBooking();
        editedElsewhere("payments.txt");

        assertThrows(BookingException.class, () -> store.getPaymentService().cancelBooking(b.getBookingId()));
        Booking now = current(b.getBookingId());
        assertEquals(BookingStatus.CONFIRMED, now.getStatus());
        assertEquals(Money.ZERO, now.getAmountRefunded());

        DataStore restarted = open();
        Booking saved = restarted.getBookingService().getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, saved.getStatus(), "bookings.txt was put back too");
        assertEquals(Money.ZERO, saved.getAmountRefunded());
    }

    @Test
    void paymentRefusedOnPaymentsFileLeavesTheBookingUnpaid() throws Exception {
        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7));
        editedElsewhere("payments.txt");

        assertThrows(ValidationException.class, () -> store.getPaymentService()
                .processPayment(b.getBookingId(), "CUS001", b.getTotalCost(), "Cash", b.getTotalCost()));
        assertEquals(Money.ZERO, current(b.getBookingId()).getAmountPaid());
        assertTrue(store.getPaymentService().getAllPayments().isEmpty());
        assertEquals(Money.ZERO, open().getBookingService().getBookingById(b.getBookingId()).orElseThrow().getAmountPaid(),
                "bookings.txt doesn't claim a payment payments.txt doesn't have");
    }

    @Test
    void paymentRefusedOnBookingsFileIsNotRecorded() throws Exception {
        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7));
        editedElsewhere("bookings.txt");

        assertThrows(ValidationException.class, () -> store.getPaymentService()
                .processPayment(b.getBookingId(), "CUS001", b.getTotalCost(), "Cash", b.getTotalCost()));
        assertEquals(Money.ZERO, current(b.getBookingId()).getAmountPaid());
        assertTrue(store.getPaymentService().getAllPayments().isEmpty());
    }

    @Test
    void returnIsTakenBackWhenItsPaymentCannotBeSaved() throws Exception {
        BookingService bookings = store.getBookingService();
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
        bookings.markPickedUp(b.getBookingId());
        editedElsewhere("payments.txt");

        BigDecimal due = b.getTotalCost();
        assertThrows(ValidationException.class, () -> store.getPaymentService()
                .completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, "Cash", due));
        Booking now = current(b.getBookingId());
        assertEquals(BookingStatus.ACTIVE, now.getStatus(), "never returned but unpaid");
        assertNull(now.getActualReturnDate());
        assertEquals(BookingStatus.ACTIVE, open().getBookingService().getBookingById(b.getBookingId()).orElseThrow().getStatus());
    }

    @Test
    void statusChangeRefusedWhenBookingsCannotBeSaved() throws Exception {
        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7));
        editedElsewhere("bookings.txt");
        assertThrows(BookingException.class, () -> store.getBookingService().markPickedUp(b.getBookingId()));
        assertThrows(BookingException.class,
                () -> store.getBookingService().updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED));
        assertEquals(BookingStatus.CONFIRMED, current(b.getBookingId()).getStatus());
    }

    // ===== Vehicles =====

    @Test
    void deleteChecksTheServicesOwnBookingsNotTheCallersList() throws Exception {
        store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(5), TODAY.plusDays(7));
        ValidationException e = assertThrows(ValidationException.class,
                () -> store.getVehicleService().deleteVehicle("VEH001", new ArrayList<>()));
        assertTrue(e.getMessage().contains("1 upcoming or active booking"), e.getMessage());
        assertTrue(store.getVehicleService().getVehicleById("VEH001").isPresent());
    }

    @Test
    void deleteNeverRemovesFilesOutsideTheImagesFolder() throws Exception {
        Path secret = Files.writeString(dataDir.resolve("secret.txt"), "keep me");
        Path photo = Files.writeString(imagesDir.resolve("VEH007.png"), "png");
        // a vehicles.txt row whose photo path points out of images/
        Path vehiclesFile = dataDir.resolve("vehicles.txt");
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(vehiclesFile, StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            if (f.length > 8 && f[1].equals("VEH007")) {
                f[8] = "../data/secret.txt";
                line = String.join("\t", f);
            }
            lines.add(line);
        }
        Files.write(vehiclesFile, lines, StandardCharsets.UTF_8);
        store = open();

        store.getVehicleService().deleteVehicle("VEH007", new ArrayList<>());
        assertTrue(Files.exists(secret), "a file outside images/ must survive");
        assertFalse(Files.exists(photo), "the vehicle's own photo is still removed");
    }

    @Test
    void photoMustBeAPlainFileName() {
        VehicleService vehicles = store.getVehicleService();
        assertThrows(ValidationException.class, () -> vehicles.setVehicleImage("VEH003", "../data/accounts.txt"));
        assertThrows(ValidationException.class, () -> vehicles.setVehicleImage("VEH003", "C:\\Windows\\win.ini"));
        assertDoesNotThrow(() -> vehicles.setVehicleImage("VEH003", "VEH003.png"));
    }

    @Test
    void refusedDeleteKeepsTheVehicleAndItsPhoto() throws Exception {
        Path photo = Files.writeString(imagesDir.resolve("VEH007.png"), "png");
        editedElsewhere("vehicles.txt");
        assertThrows(ValidationException.class, () -> store.getVehicleService().deleteVehicle("VEH007", new ArrayList<>()));
        assertTrue(store.getVehicleService().getVehicleById("VEH007").isPresent());
        assertTrue(Files.exists(photo));
    }

    @Test
    void refusedServiceChangeIsUndoneAndLaterChangesAreRefused() throws Exception {
        VehicleService vehicles = store.getVehicleService();
        editedElsewhere("vehicles.txt");
        assertThrows(ValidationException.class, () -> vehicles.setVehicleInService("VEH001", false));
        assertTrue(vehicles.getVehicleById("VEH001").orElseThrow().isAvailable(), "the app doesn't show an unsaved change");
        assertThrows(ValidationException.class, () -> vehicles.setVehicleImage("VEH001", "VEH001.png"));
        assertNotEquals("VEH001.png", vehicles.getVehicleById("VEH001").orElseThrow().getImagePath(), "photo unchanged");
    }

    @Test
    void licensePlatesAreUniqueIgnoringCase() {
        VehicleService vehicles = store.getVehicleService();
        assertThrows(ValidationException.class, () ->
                vehicles.addVehicle(new Car("VEH099", "Kia", "Rio", 2023, Money.of(2000), "abc-1234", 4, "Gasoline")));
        assertThrows(ValidationException.class, () ->
                vehicles.updateVehicle("VEH002", new Car("VEH002", "Honda", "Accord", 2023, Money.of(2800), " ghi-9012 ", 4, "Gasoline")));
        assertDoesNotThrow(() ->
                vehicles.updateVehicle("VEH001", new Car("VEH001", "Toyota", "Camry", 2023, Money.of(2500), "abc-1234", 4, "Gasoline")),
                "a vehicle may keep its own plate in a different case");
    }

    // ===== Starting passwords =====

    @Test
    void addedCustomersGetRandomPasswordsShownOnce() throws Exception {
        AuthService auth = store.getAuthService();
        Customer pedro = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001");
        Customer ana = auth.addCustomer("Ana Reyes", "", "0917-000-0002", "N02-00-000002");

        String pedroPassword = auth.takeTemporaryPassword(pedro.getUserId());
        String anaPassword = auth.takeTemporaryPassword(ana.getUserId());
        assertNotEquals(pedroPassword, anaPassword);
        assertDoesNotThrow(() -> AuthService.validatePasswordStrength(pedroPassword));
        assertNull(auth.takeTemporaryPassword(pedro.getUserId()), "shown only once");

        auth.logout();
        assertThrows(ValidationException.class, () -> auth.takeTemporaryPassword(ana.getUserId()), "admins only");
        assertThrows(Exception.class, () -> auth.login(pedro.getUsername(), "changeme123"), "no shared default password");
        assertTrue(auth.login(pedro.getUsername(), pedroPassword).isMustChangePassword());
    }

    @Test
    void randomPasswordsAlwaysMeetThePasswordRule() {
        for (int i = 0; i < 2000; i++) {
            String p = AuthService.randomTemporaryPassword();
            assertEquals(AuthService.TEMPORARY_PASSWORD_LENGTH, p.length());
            assertDoesNotThrow(() -> AuthService.validatePasswordStrength(p), p);
        }
    }
}
