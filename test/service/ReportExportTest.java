package service;

import model.Booking;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for exporting the Reports view to CSV (feature 6). */
class ReportExportTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;
    private PaymentService payments;
    private ReportService reports;
    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        reports = new ReportService(bookings, vehicles, payments);
        audit = new AuditLog(dataDir.resolve("audit.txt").toFile(), auth::getCurrentUser);
        bookings.setAuditLog(audit);
        auth.login("admin", "admin123");
    }

    private File export() throws Exception {
        File file = dataDir.resolve("report.csv").toFile();
        reports.exportCsv(file);
        return file;
    }

    private static String month(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    @Test
    void exportHasSummaryMonthsAndVehicleTypes() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(3)); // Camry ₱5,000
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        vehicles.addMaintenanceRecord("VEH002", TODAY, "Oil change", new BigDecimal("1500"), null, false);

        byte[] bytes = Files.readAllBytes(export().toPath());
        assertEquals((byte) 0xEF, bytes[0], "starts with the UTF-8 byte-order mark, so Excel shows ₱");
        String csv = new String(bytes, StandardCharsets.UTF_8).substring(1);
        List<String> lines = List.of(csv.split("\r\n", -1));

        assertTrue(lines.get(0).startsWith("\"RentNGo Report\""));
        assertTrue(lines.contains("\"Total revenue (₱)\",\"5000.00\""), csv);
        assertTrue(lines.contains("\"Total maintenance cost (₱)\",\"1500.00\""), csv);
        assertTrue(lines.contains("\"Revenue minus maintenance (₱)\",\"3500.00\""), csv);
        assertTrue(lines.contains("\"Year\",\"Month\",\"Revenue (₱)\",\"Maintenance (₱)\",\"Revenue minus maintenance (₱)\",\"Bookings\""), csv);

        YearMonth now = YearMonth.from(TODAY);
        String thisMonthRow = "\"" + now.getYear() + "\",\"" + month(now) + "\",\"5000.00\",\"1500.00\",\"3500.00\"";
        assertTrue(lines.stream().anyMatch(l -> l.startsWith(thisMonthRow)), "this month's row: " + csv);
        YearMonth first = now.minusMonths(ReportService.EXPORT_MONTHS - 1);
        assertTrue(lines.contains("\"" + first.getYear() + "\",\"" + month(first) + "\",\"0.00\",\"0.00\",\"0.00\",\"0\""),
                "12 months, the oldest with nothing in it: " + csv);
        long monthRows = lines.stream().filter(l -> l.matches("\"\\d{4}\",\"[A-Z][a-z]+\",.*")).count();
        assertEquals(ReportService.EXPORT_MONTHS, monthRows);

        assertTrue(lines.contains("\"Car\",\"1\",\"100.0\""), csv);
        assertTrue(lines.contains("\"Van\",\"0\",\"0.0\""), csv);
        assertTrue(lines.contains("\"Total\",\"1\",\"100.0\""), csv);
    }

    @Test
    void lossIsWrittenAsANumberExcelCanAddUp() throws Exception {
        vehicles.addMaintenanceRecord("VEH002", TODAY, "Engine repair", new BigDecimal("6500"), null, false);
        String csv = Files.readString(export().toPath(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"-6500.00\""), "no apostrophe in front of a plain negative number: " + csv);
        assertFalse(csv.contains("'-"), csv);
    }

    @Test
    void emptyDataStillExports() throws Exception {
        String csv = Files.readString(export().toPath(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Most-rented vehicle\",\"none yet\""));
        assertTrue(csv.contains("\"Total\",\"0\",\"0\""));
    }

    @Test
    void exportIsRecordedInTheAuditLog() throws Exception {
        export();
        AuditLog.Entry last = audit.entriesNewestFirst().get(0);
        assertEquals(AuditLog.REPORT_EXPORTED, last.getAction());
        assertTrue(last.getDetails().contains("report.csv"));
        assertEquals("ADM001", last.getUserId());
    }

    @Test
    void onlyAdminsCanExport() throws Exception {
        auth.logout();
        auth.login("john", "john123");
        File file = dataDir.resolve("report.csv").toFile();
        assertThrows(ValidationException.class, () -> reports.exportCsv(file));
        assertFalse(file.exists(), "nothing is written");
        auth.logout();
        assertThrows(ValidationException.class, () -> reports.exportCsv(file));
    }
}
