package service;

import model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #10: revenue is counted one way everywhere, by the date the money was received (payments
 * minus refunds), never by the month the rental starts. The Dashboard reads these same methods,
 * so it shows the same numbers as Reports.
 */
class RevenueByPaymentDateTest {

    private static final YearMonth THIS_MONTH = YearMonth.now();
    private static final YearMonth LAST_MONTH = THIS_MONTH.minusMonths(1);
    private static final YearMonth NEXT_MONTH = THIS_MONTH.plusMonths(1);

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;
    private PaymentService payments;
    private ReportService reports;

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
        reports = new ReportService(bookings, vehicles, payments);
        auth.login("admin", "admin123");
    }

    private Booking bookAndPay(String vehicleId, LocalDate start) throws Exception {
        Booking b = bookings.createBooking("CUS001", vehicleId, start, start.plusDays(1));
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        return b;
    }

    /** Changes the date of the booking's payments in payments.txt, as if they were taken that day. */
    private void paidOn(Booking b, LocalDate day) throws Exception {
        Path file = dataDir.resolve("payments.txt");
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String[] f = line.split("\t", -1);
            if (!line.startsWith("#") && f.length > 6 && f[1].equals(b.getBookingId())) {
                f[6] = day.atTime(10, 0).toString();
                line = String.join("\t", f);
            }
            lines.add(line);
        }
        Files.write(file, lines);
        open();
    }

    private static void assertAmount(String expected, BigDecimal actual, String why) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), why + " (was " + actual + ")");
    }

    @Test
    void rentalPaidNowForNextMonthCountsThisMonth() throws Exception {
        bookAndPay("VEH001", NEXT_MONTH.atDay(10)); // Camry ₱2,500, paid today

        Map<YearMonth, BigDecimal> byMonth = reports.getRevenueByMonth(THIS_MONTH, NEXT_MONTH);
        assertAmount("2500", byMonth.get(THIS_MONTH), "the money came in this month");
        assertAmount("0", byMonth.get(NEXT_MONTH), "nothing is received next month");
        assertAmount("2500", reports.getRevenueForMonth(THIS_MONTH), "same as the chart");
    }

    @Test
    void refundCountsInTheMonthItWasMade() throws Exception {
        bookAndPay("VEH001", NEXT_MONTH.atDay(10));                  // ₱2,500 paid this month
        Booking b = bookAndPay("VEH004", NEXT_MONTH.atDay(20));      // Explorer ₱4,025
        paidOn(b, LAST_MONTH.atDay(15));                              // ...paid last month
        BigDecimal refund = payments.cancelBooking(b.getBookingId()); // refunded today
        assertTrue(refund.signum() > 0);

        assertAmount("4025", reports.getRevenueForMonth(LAST_MONTH), "last month keeps what it received");
        assertAmount(new BigDecimal("2500").subtract(refund).toPlainString(), reports.getRevenueForMonth(THIS_MONTH),
                "the refund comes off this month");
        Map<YearMonth, BigDecimal> byMonth = reports.getRevenueByMonth(LAST_MONTH, THIS_MONTH);
        assertEquals(reports.getRevenueForMonth(LAST_MONTH), byMonth.get(LAST_MONTH));
        assertEquals(reports.getRevenueForMonth(THIS_MONTH), byMonth.get(THIS_MONTH));
    }

    @Test
    void revenueByTypeAddsUpToTotalRevenue() throws Exception {
        bookAndPay("VEH001", NEXT_MONTH.atDay(1));                  // Car ₱2,500
        Booking suv = bookAndPay("VEH004", NEXT_MONTH.atDay(5));    // SUV ₱4,025
        BigDecimal refund = payments.cancelBooking(suv.getBookingId());

        Map<String, BigDecimal> byType = reports.getRevenueByVehicleType();
        assertEquals(List.of("Car", "SUV", "Truck", "Van", "EV"), new ArrayList<>(byType.keySet()));
        assertAmount("2500", byType.get("Car"), "Car");
        assertAmount(new BigDecimal("4025").subtract(refund).toPlainString(), byType.get("SUV"), "SUV keeps the fee");
        BigDecimal sum = byType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertAmount(reports.getTotalRevenue().toPlainString(), sum, "the bars add up to Total Revenue");
    }

    @Test
    void averageRentalIsThePriceOfThisMonthsRentals() throws Exception {
        bookAndPay("VEH001", THIS_MONTH.plusMonths(1).atDay(1)); // next month: not counted
        Booking unpaid = bookings.createBooking("CUS001", "VEH004", NEXT_MONTH.atDay(5), NEXT_MONTH.atDay(6));
        Booking paid = bookAndPay("VEH001", NEXT_MONTH.atDay(10));
        assertAmount("0", reports.getAverageRentalValueForMonth(THIS_MONTH), "no rentals start this month");

        // ₱4,025 unpaid and ₱2,500 + ₱2,500 paid: average of the prices, paid or not
        BigDecimal expected = unpaid.getFinalAmount().add(paid.getFinalAmount()).add(new BigDecimal("2500"))
                .divide(new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP);
        assertAmount(expected.toPlainString(), reports.getAverageRentalValueForMonth(NEXT_MONTH), "average price");

        bookings.cancelBooking(unpaid.getBookingId());
        assertAmount("2500", reports.getAverageRentalValueForMonth(NEXT_MONTH), "cancelled rentals are left out");
    }
}
