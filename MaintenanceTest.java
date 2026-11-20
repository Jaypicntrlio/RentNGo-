package service;

import model.MaintenanceRecord;
import model.Money;
import model.Vehicle;
import model.exceptions.ValidationException;
import model.exceptions.VehicleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for the vehicle maintenance history (feature 7). */
class MaintenanceTest {

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

    private MaintenanceRecord add(String vehicleId, LocalDate date, String work, String cost, LocalDate next) throws Exception {
        return vehicles.addMaintenanceRecord(vehicleId, date, work, new BigDecimal(cost), next, false);
    }

    @Test
    void recordIsSavedAndLoadedAfterRestart() throws Exception {
        MaintenanceRecord r = add("VEH001", TODAY.minusDays(3), "Oil change\tand filter", "1500", TODAY.plusDays(90));
        assertEquals("MNT0001", r.getRecordId());

        open();
        auth.login("admin", "admin123");
        List<MaintenanceRecord> loaded = vehicles.getMaintenanceRecords("VEH001");
        assertEquals(1, loaded.size());
        MaintenanceRecord back = loaded.get(0);
        assertEquals("Oil change\tand filter", back.getDescription(), "tabs in the text survive saving");
        assertEquals(0, new BigDecimal("1500.00").compareTo(back.getCost()));
        assertEquals(TODAY.minusDays(3), back.getServiceDate());
        assertEquals(TODAY.plusDays(90), back.getNextServiceDate());
        assertEquals("MNT0002", add("VEH001", TODAY, "Car wash", "200", null).getRecordId(), "numbers continue after a restart");
    }

    @Test
    void historyIsNewestFirstAndPerVehicle() throws Exception {
        add("VEH001", TODAY.minusDays(30), "Old service", "1000", null);
        add("VEH001", TODAY.minusDays(1), "New service", "2000", TODAY.plusDays(60));
        add("VEH002", TODAY, "Other car", "500", null);

        List<String> work = vehicles.getMaintenanceRecords("VEH001").stream()
                .map(MaintenanceRecord::getDescription).collect(Collectors.toList());
        assertEquals(List.of("New service", "Old service"), work);
        assertEquals(TODAY.plusDays(60), vehicles.getNextServiceDate("VEH001").orElseThrow(), "next service comes from the latest record");
        assertTrue(vehicles.getNextServiceDate("VEH003").isEmpty());
    }

    @Test
    void badInputIsRejected() {
        assertThrows(VehicleNotFoundException.class, () -> add("NOPE", TODAY, "x", "1", null));
        assertMessage("future", () -> add("VEH001", TODAY.plusDays(1), "Oil change", "100", null));
        assertMessage("work done", () -> add("VEH001", TODAY, "   ", "100", null));
        assertMessage("at most", () -> add("VEH001", TODAY, "x".repeat(201), "100", null));
        assertMessage("negative", () -> add("VEH001", TODAY, "Oil change", "-1", null));
        assertMessage("at most", () -> add("VEH001", TODAY, "Oil change", "1000000.01", null));
        assertMessage("after the service date", () -> add("VEH001", TODAY, "Oil change", "100", TODAY));
        assertTrue(vehicles.getMaintenanceRecords("VEH001").isEmpty(), "nothing is saved when a rule fails");
    }

    @Test
    void freeServiceIsAllowed() throws Exception {
        assertEquals(0, add("VEH001", TODAY, "Warranty repair", "0", null).getCost().signum());
    }

    @Test
    void canTakeVehicleOutOfService() throws Exception {
        vehicles.addMaintenanceRecord("VEH002", TODAY, "Brake repair", new BigDecimal("8000"), null, true);
        assertFalse(vehicles.getVehicleById("VEH002").orElseThrow().isAvailable());

        open();
        assertFalse(vehicles.getVehicleById("VEH002").orElseThrow().isAvailable(), "the switch is saved");
    }

    @Test
    void onlyAdminsCanRecordOrSeeMaintenance() throws Exception {
        add("VEH001", TODAY, "Oil change", "1500", null);
        auth.logout();
        auth.login("john", "john123");
        assertThrows(ValidationException.class, () -> add("VEH001", TODAY, "Oil change", "1500", null));
        assertTrue(vehicles.getAllMaintenanceRecords().isEmpty(), "customers never see maintenance costs");
        assertTrue(vehicles.getVehiclesDueForService(TODAY.plusYears(1)).isEmpty());
    }

    @Test
    void vehiclesDueForServiceAreListedMostOverdueFirst() throws Exception {
        add("VEH001", TODAY.minusDays(100), "Service", "1000", TODAY.minusDays(10)); // overdue
        add("VEH002", TODAY.minusDays(100), "Service", "1000", TODAY.minusDays(2));  // overdue, less
        add("VEH003", TODAY.minusDays(100), "Service", "1000", TODAY.plusDays(5));   // not yet
        add("VEH004", TODAY.minusDays(100), "Service", "1000", TODAY.minusDays(1));  // was due...
        add("VEH004", TODAY, "Serviced again", "1000", TODAY.plusDays(90));          // ...but done now

        List<String> due = vehicles.getVehiclesDueForService(TODAY).stream()
                .map(Vehicle::getVehicleId).collect(Collectors.toList());
        assertEquals(List.of("VEH001", "VEH002"), due);
    }

    @Test
    void reportsShowMaintenanceCost() throws Exception {
        ReportService reports = new ReportService(bookings, vehicles, payments);
        add("VEH001", TODAY, "Oil change", "1500", null);
        add("VEH002", TODAY, "Tires", "6000.50", null);
        add("VEH003", TODAY.minusMonths(2), "Battery", "4000", null);

        assertEquals(0, new BigDecimal("11500.50").compareTo(reports.getTotalMaintenanceCost()));
        assertEquals(0, new BigDecimal("7500.50").compareTo(reports.getMaintenanceCostForMonth(YearMonth.from(TODAY))));
    }

    @Test
    void deletedVehicleKeepsItsHistoryInReports() throws Exception {
        add("VEH007", TODAY, "Engine repair", "20000", null);
        vehicles.deleteVehicle("VEH007", bookings.getBookingsByVehicle("VEH007"));
        ReportService reports = new ReportService(bookings, vehicles, payments);
        assertEquals(0, Money.of(20000).compareTo(reports.getTotalMaintenanceCost()));
    }

    @Test
    void damagedLineIsSkippedAndReported() throws Exception {
        add("VEH001", TODAY, "Oil change", "1500", null);
        File file = dataDir.resolve("maintenance.txt").toFile();
        Files.writeString(file.toPath(), Files.readString(file.toPath()) + "MNT0009\tVEH002\tnot-a-date\tX\t1\t\n");

        open();
        auth.login("admin", "admin123");
        assertEquals(1, vehicles.getAllMaintenanceRecords().size());
        assertNotNull(vehicles.getLoadError());
        assertTrue(vehicles.getLoadError().contains("maintenance.txt line"));
        assertEquals("MNT0010", add("VEH001", TODAY, "Wash", "100", null).getRecordId(), "a skipped line's number is not reused");
    }

    private static void assertMessage(String fragment, org.junit.jupiter.api.function.Executable action) {
        ValidationException e = assertThrows(ValidationException.class, action);
        assertTrue(e.getMessage().toLowerCase().contains(fragment.toLowerCase()),
                "message '" + e.getMessage() + "' should mention '" + fragment + "'");
    }
}
