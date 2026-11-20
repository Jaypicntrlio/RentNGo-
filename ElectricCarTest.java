package service;

import model.Booking;
import model.Car;
import model.ElectricCar;
import model.Vehicle;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for the Electric Car (EV) vehicle type. */
class ElectricCarTest {

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
    }

    /** Opens the services on dataDir, like starting the app again, and logs in as admin. */
    private void open() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        auth.login("admin", "admin123");
    }

    private static ElectricCar ev(String id, int rangeKm, boolean fastCharging) {
        return new ElectricCar(id, "BYD", "Dolphin", 2025, new BigDecimal("3000"), "EV-" + id, rangeKm, fastCharging);
    }

    @Test
    void evGetsTheEcoDiscount() throws Exception {
        vehicles.addVehicle(ev("VEH201", 400, true));
        Booking b = bookings.createBooking("CUS001", "VEH201", TODAY.plusDays(1), TODAY.plusDays(4));
        // ₱3,000 x 3 days = ₱9,000, less 10%
        assertEquals(0, new BigDecimal("8100.00").compareTo(b.getTotalCost()), b.getTotalCost().toPlainString());
        assertEquals(0, new BigDecimal("2700.00").compareTo(ev("VEH202", 300, false).calculateRentalCost(1)),
                "one day: ₱2,700, the same 10% off as an electric Car");
    }

    @Test
    void evIsSavedAndLoadedWithItsDetails() throws Exception {
        vehicles.addVehicle(ev("VEH201", 420, true));
        vehicles.addVehicle(ev("VEH202", 270, false));
        open(); // restart

        Vehicle loaded = vehicles.getVehicleById("VEH201").orElseThrow();
        assertTrue(loaded instanceof ElectricCar, "a saved EV loads back as an EV");
        assertEquals("EV", loaded.getType());
        assertEquals(420, ((ElectricCar) loaded).getBatteryRangeKm());
        assertTrue(((ElectricCar) loaded).hasFastCharging());
        assertFalse(((ElectricCar) vehicles.getVehicleById("VEH202").orElseThrow()).hasFastCharging());
        assertEquals(List.of("420 km range", "Fast charging"), loaded.getFeatures());
    }

    @Test
    void batteryRangeIsChecked() throws Exception {
        for (int bad : new int[]{0, ElectricCar.MIN_RANGE_KM - 1, ElectricCar.MAX_RANGE_KM + 1}) {
            ValidationException e = assertThrows(ValidationException.class, () -> vehicles.addVehicle(ev("VEH210", bad, true)));
            assertTrue(e.getMessage().contains("battery range"), e.getMessage());
        }
        vehicles.addVehicle(ev("VEH211", ElectricCar.MIN_RANGE_KM, false));
        vehicles.addVehicle(ev("VEH212", ElectricCar.MAX_RANGE_KM, true));
        assertTrue(vehicles.getVehicleById("VEH210").isEmpty(), "nothing saved for a bad range");
    }

    @Test
    void editingAnEvKeepsItAnEv() throws Exception {
        vehicles.addVehicle(ev("VEH201", 400, false));
        vehicles.updateVehicle("VEH201", ev("VEH201", 500, true));
        open();
        ElectricCar edited = (ElectricCar) vehicles.getVehicleById("VEH201").orElseThrow();
        assertEquals(500, edited.getBatteryRangeKm());
        assertTrue(edited.hasFastCharging());

        ElectricCar copy = edited.copy();
        copy.setBatteryRangeKm(999);
        assertEquals(500, vehicles.getVehicleById("VEH201").map(v -> ((ElectricCar) v).getBatteryRangeKm()).orElseThrow(),
                "copies never change the saved vehicle");
    }

    @Test
    void demoFleetHasTwoEvsAndTheOldElectricCarStaysACar() {
        List<Vehicle> evs = vehicles.getAllVehicles().stream().filter(v -> v instanceof ElectricCar).collect(Collectors.toList());
        assertEquals(List.of("VEH008", "VEH009"), evs.stream().map(Vehicle::getVehicleId).collect(Collectors.toList()));
        assertTrue(evs.stream().allMatch(Vehicle::isAvailable), "both are in service");
        assertTrue(vehicles.getVehicleById("VEH003").orElseThrow() instanceof Car,
                "the Tesla saved as an electric Car is left as it was");
    }

    @Test
    void reportsCountEvsAsTheirOwnType() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH008", TODAY.plusDays(2), TODAY.plusDays(3)); // BYD Atto 3
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        ReportService reports = new ReportService(bookings, vehicles, payments);

        assertEquals(1L, reports.getBookingsByVehicleType().get("EV"));
        assertEquals(0, new BigDecimal("2700.00").compareTo(reports.getRevenueByVehicleType().get("EV")));
        assertEquals(0L, reports.getBookingsByVehicleType().get("Car"));
    }
}
