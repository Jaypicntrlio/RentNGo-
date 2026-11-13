package service;

import model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for the figures on the admin Reports view. */
class ReportServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private BookingService bookings;
    private PaymentService payments;
    private ReportService reports;

    @BeforeEach
    void setUp() throws Exception {
        AuthService auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        reports = new ReportService(bookings, vehicles, payments);
        auth.login("admin", "admin123"); // reports are for admins
    }

    private Booking bookAndPay(String vehicleId, int startOffset) throws Exception {
        Booking b = bookings.createBooking("CUS001", vehicleId, TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 1));
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        return b;
    }

    @Test
    void emptyDataGivesZeroes() {
        assertEquals(0, reports.getTotalRevenue().signum());
        assertEquals(0, reports.getRevenueForMonth(YearMonth.now()).signum());
        assertEquals(0, reports.getActiveRentalCount());
        assertTrue(reports.getMostRentedVehicle().isEmpty());
        assertEquals(Map.of("Car", 0L, "SUV", 0L, "Truck", 0L), reports.getBookingsByVehicleType());
    }

    @Test
    void revenueIsPaymentsMinusRefunds() throws Exception {
        bookAndPay("VEH001", 1);                    // Camry 2,500
        Booking refunded = bookAndPay("VEH004", 3); // Explorer 3,500 x 1.15 = 4,025
        payments.cancelBooking(refunded.getBookingId());

        assertEquals(0, new BigDecimal("2500.00").compareTo(reports.getTotalRevenue()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(reports.getRevenueForMonth(YearMonth.now())));
        assertEquals(0, reports.getRevenueForMonth(YearMonth.now().minusMonths(1)).signum());
    }

    @Test
    void mostRentedVehicleAndTypeCountsIgnoreCancelled() throws Exception {
        bookAndPay("VEH001", 1);
        bookAndPay("VEH001", 5);
        bookAndPay("VEH004", 1);
        Booking cancelled1 = bookings.createBooking("CUS002", "VEH006", TODAY.plusDays(1), TODAY.plusDays(2));
        Booking cancelled2 = bookings.createBooking("CUS002", "VEH006", TODAY.plusDays(5), TODAY.plusDays(6));
        Booking cancelled3 = bookings.createBooking("CUS002", "VEH006", TODAY.plusDays(9), TODAY.plusDays(10));
        for (Booking b : new Booking[]{cancelled1, cancelled2, cancelled3}) bookings.cancelBooking(b.getBookingId());

        ReportService.VehicleRentals top = reports.getMostRentedVehicle().orElseThrow();
        assertEquals("VEH001", top.getVehicleId());
        assertEquals("Toyota Camry", top.getName());
        assertEquals(2, top.getRentals());
        assertEquals(Map.of("Car", 2L, "SUV", 1L, "Truck", 0L), reports.getBookingsByVehicleType());
    }

    @Test
    void activeRentalsCountsPickedUpVehicles() throws Exception {
        Booking today = bookings.createBooking("CUS001", "VEH002", TODAY, TODAY.plusDays(2));
        bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(3), TODAY.plusDays(4));
        assertEquals(0, reports.getActiveRentalCount());
        bookings.markPickedUp(today.getBookingId());
        assertEquals(1, reports.getActiveRentalCount());
    }
}
