package service;

import model.Booking;
import model.BookingStatus;
import model.Car;
import model.Customer;
import model.Money;
import model.Truck;
import model.User;
import model.exceptions.AuthenticationException;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the loopholes found in the second review: rental limits, pick-up checks,
 * no-shows, price changes during a rental, demo passwords, lockouts, failed saves and IDs.
 */
class LoopholeFixesTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private DataStore store;
    private BookingService bookings;

    @BeforeEach
    void setUp() {
        store = Stores.asAdmin(dataDir);
        bookings = store.getBookingService();
        TsvFile.setChangedListener(e -> { });
    }

    @AfterEach
    void tearDown() {
        TsvFile.setChangedListener(null);
        System.setProperty(AuthService.ALLOW_DEMO_PASSWORDS, "true");
        dataDir.resolve("bookings.txt").toFile().setWritable(true);
    }

    // ===== Rental limits =====

    @Test
    void farFutureEndDateIsRefusedInsteadOfOverflowing() {
        BookingException e = assertThrows(BookingException.class,
                () -> bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), LocalDate.of(999_999, 1, 1)));
        assertTrue(e.getMessage().contains("at most " + BookingService.MAX_RENTAL_DAYS + " days"), e.getMessage());
        assertThrows(BookingException.class,
                () -> bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(BookingService.MAX_DAYS_AHEAD + 1),
                        TODAY.plusDays(BookingService.MAX_DAYS_AHEAD + 2)));
        assertTrue(bookings.getAllBookings().isEmpty());
        assertDoesNotThrow(() -> bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(BookingService.MAX_RENTAL_DAYS)));
    }

    @Test
    void aCustomerCantHoldTheWholeFleet() throws Exception {
        store.getAuthService().logout();
        store.getAuthService().login("john", "john123");
        for (int i = 1; i <= BookingService.MAX_OPEN_BOOKINGS_PER_CUSTOMER; i++) {
            bookings.createBooking("CUS001", "VEH00" + i, TODAY.plusDays(1), TODAY.plusDays(2));
        }
        BookingException e = assertThrows(BookingException.class,
                () -> bookings.createBooking("CUS001", "VEH005", TODAY.plusDays(1), TODAY.plusDays(2)));
        assertTrue(e.getMessage().contains("upcoming or active rentals"), e.getMessage());
    }

    // ===== Pick-up =====

    @Test
    void cantPickUpWhileThePreviousRenterStillHasTheCar() throws Exception {
        Booking first = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        bookings.markPickedUp(first.getBookingId());
        Booking second = bookings.createBooking("CUS002", "VEH001", TODAY.plusDays(1), TODAY.plusDays(3));
        // pretend tomorrow came and the first car is overdue: second's start has arrived
        Booking live = bookings.find(second.getBookingId()).orElseThrow();
        live.setStartDate(TODAY);

        BookingException e = assertThrows(BookingException.class, () -> bookings.markPickedUp(second.getBookingId()));
        assertTrue(e.getMessage().contains(first.getBookingId()), e.getMessage());
        assertEquals(1, bookings.getBookingsByStatus(BookingStatus.ACTIVE).size());
    }

    @Test
    void cantPickUpAVehicleThatIsOutOfService() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH003", TODAY, TODAY.plusDays(2));
        store.getVehicleService().setVehicleInService("VEH003", false);
        BookingException e = assertThrows(BookingException.class, () -> bookings.markPickedUp(b.getBookingId()));
        assertTrue(e.getMessage().contains("out of service"), e.getMessage());
    }

    // ===== No-shows =====

    @Test
    void adminCanCancelANoShowButTheCustomerCant() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(3));
        bookings.find(b.getBookingId()).orElseThrow().setStartDate(TODAY.minusDays(2)); // start passed, never picked up

        store.getAuthService().logout();
        store.getAuthService().login("john", "john123");
        assertThrows(BookingException.class, () -> bookings.cancelBooking(b.getBookingId()));

        store.getAuthService().logout();
        store.getAuthService().login("admin", "admin123");
        bookings.cancelBooking(b.getBookingId());
        assertEquals(BookingStatus.CANCELLED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
        assertTrue(bookings.isVehicleAvailableToday("VEH001"), "the no-show no longer blocks the car");
    }

    // ===== Vehicle edits during a rental =====

    @Test
    void priceAndTypeAreLockedWhileBooked() throws Exception {
        VehicleService vehicles = store.getVehicleService();
        bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(1), TODAY.plusDays(3));
        assertThrows(ValidationException.class, () -> vehicles.updateVehicle("VEH003",
                new Car("VEH003", "Tesla", "Model 3", 2024, Money.of(45000), "GHI-9012", 4, "Electric")));
        assertThrows(ValidationException.class, () -> vehicles.updateVehicle("VEH003",
                new Truck("VEH003", "Tesla", "Cybertruck", 2024, Money.of(4500), "GHI-9012", 3.0, true)));
        assertDoesNotThrow(() -> vehicles.updateVehicle("VEH003",
                new Car("VEH003", "Tesla", "Model 3 Long Range", 2024, Money.of(4500), "GHI-9012", 4, "Electric")),
                "other details can still change");
        assertEquals(0, Money.of(4500).compareTo(vehicles.getVehicleById("VEH003").orElseThrow().getPricePerDay()));
    }

    // ===== Demo passwords and admin sign-up =====

    @Test
    void demoPasswordsMustBeChangedAndCantApproveAdmins() throws Exception {
        System.setProperty(AuthService.ALLOW_DEMO_PASSWORDS, "false");
        AuthService auth = store.getAuthService();
        auth.logout();

        ValidationException refused = assertThrows(ValidationException.class, () -> auth.registerAdmin(
                "sneaky", "sneaky123", "Sneaky Person", "s@example.com", "", "admin", "admin123"));
        assertTrue(refused.getMessage().contains("change their password first"), refused.getMessage());
        assertTrue(auth.getAllUsers().isEmpty() && !auth.isUsernameTaken("sneaky"), "no admin was created");

        User admin = auth.login("admin", "admin123");
        assertTrue(admin.isMustChangePassword(), "the printed demo password must be changed");
        assertThrows(ValidationException.class, () -> auth.changePassword(admin.getUserId(), "admin123", "john123"));
        auth.changePassword(admin.getUserId(), "admin123", "N3w-admin-pass");

        auth.logout();
        assertNotNull(auth.registerAdmin("helper", "helper123", "Helper Admin", "h@example.com", "",
                "admin", "N3w-admin-pass"), "an admin with a real password can still approve");
    }

    // ===== Lockout =====

    @Test
    void lockoutSurvivesARestartAndLookAlikeNames() throws Exception {
        AuthService auth = store.getAuthService();
        auth.logout();
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThrows(AuthenticationException.class, () -> auth.login("admin", "wrong-password"));
        }
        // dotless i: equalsIgnoreCase matches "admin", so it must share admin's lock
        assertThrows(AuthenticationException.class, () -> auth.login("admın", "admin123"));

        DataStore restarted = new DataStore(dataDir.toFile(), null);
        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> restarted.getAuthService().login("admin", "admin123"));
        assertTrue(e.getMessage().startsWith("Too many attempts"), e.getMessage());
    }

    // ===== Failed saves =====

    @Test
    void aBookingThatCantBeWrittenIsUndone() throws Exception {
        File file = dataDir.resolve("bookings.txt").toFile();
        bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2)); // creates the file
        assertTrue(file.setReadOnly());

        BookingException e = assertThrows(BookingException.class,
                () -> bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2)));
        assertTrue(e.getMessage().startsWith("Could not save bookings"), e.getMessage());
        assertEquals(1, bookings.getAllBookings().size(), "the unsaved booking isn't shown");
        assertFalse(bookings.isReadOnly(), "a locked file isn't 'changed elsewhere': the next save can work");

        assertTrue(file.setWritable(true));
        Booking next = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
        assertEquals("BKG0002", next.getBookingId(), "the refused number is reused only because it was never saved");
    }

    // ===== IDs =====

    @Test
    void skippedAccountRowsStillReserveTheirId() throws Exception {
        AuthService auth = store.getAuthService();
        Customer added = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001"); // CUS003
        Path accounts = dataDir.resolve("accounts.txt");
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(accounts, StandardCharsets.UTF_8)) {
            if (line.startsWith("#")) continue;                       // an old file without #seq lines
            if (line.contains("\t" + added.getUserId() + "\t")) {
                line = line.substring(0, line.lastIndexOf('\t'));    // damage the newest customer's row
                line = line.substring(0, line.indexOf('\t', line.indexOf('\t') + 1) + 1) + "x";
            }
            lines.add(line);
        }
        Files.write(accounts, lines, StandardCharsets.UTF_8);

        DataStore restarted = Stores.asAdmin(dataDir);
        Customer next = restarted.getAuthService().addCustomer("Ana Reyes", "", "0917-000-0002", "N02-00-000002");
        assertNotEquals(added.getUserId(), next.getUserId(), "a skipped row's ID is never handed out again");
    }

    // ===== Input =====

    @Test
    void namesCantCarryHtml() {
        AuthService auth = store.getAuthService();
        assertThrows(ValidationException.class, () -> auth.addCustomer(
                "<html><img src='http://example.com/x.png'>Ana", "", "0917-000-0001", "N02-00-000001"));
        assertThrows(ValidationException.class, () -> auth.registerCustomer(
                "ana", "anaPass123", "<html><b>VIP</b>", "ana@example.com", "", "N02-00-000002"));
    }

    @Test
    void amountsAcceptThePesoSignButNotExponents() {
        assertEquals(new java.math.BigDecimal("1500.00"), Money.parse("₱1,500"));
        assertEquals(new java.math.BigDecimal("1500.50"), Money.parse(" ₱ 1,500.50 "));
        assertThrows(NumberFormatException.class, () -> Money.parse("1e5000000"));
        assertThrows(NumberFormatException.class, () -> Money.parse("1500 php"));
    }

    @Test
    void vehicleIdsAreSafeFileNamesAndUniqueIgnoringCase() {
        VehicleService vehicles = store.getVehicleService();
        for (String bad : new String[]{"..\\..\\outside", "profiles\\CUS001", "VEH 9", "a.b"}) {
            assertThrows(ValidationException.class, () -> vehicles.addVehicle(
                    new Car(bad, "Kia", "Rio", 2023, Money.of(2000), "NEW-" + bad.length(), 4, "Gasoline")), bad);
        }
        assertThrows(ValidationException.class, () -> vehicles.addVehicle(
                new Car("veh001", "Kia", "Rio", 2023, Money.of(2000), "NEW-0001", 4, "Gasoline")));
        assertDoesNotThrow(() -> vehicles.addVehicle(
                new Car("VEH-100", "Kia", "Rio", 2023, Money.of(2000), "NEW-0002", 4, "Gasoline")));
    }
}
