package gui;

import model.Booking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.DataStore;
import service.ReportService;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Fix #10: the Dashboard's Revenue card shows the same number as Reports' Revenue This Month. */
class DashboardRevenueTest {

    @TempDir
    Path dir;

    private static List<String> labels(Container c) {
        List<String> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel && ((JLabel) k).getText() != null) out.add(((JLabel) k).getText());
            if (k instanceof Container) out.addAll(labels((Container) k));
        }
        return out;
    }

    @Test
    void dashboardRevenueMatchesReports() throws Exception {
        DataStore store = new DataStore(dir.resolve("data").toFile(), null);
        store.getAuthService().login("admin", "admin123");
        // a rental starting next month, paid today: received this month
        YearMonth next = YearMonth.now().plusMonths(1);
        Booking b = store.getBookingService().createBooking("CUS001", "VEH001", next.atDay(10), next.atDay(11));
        store.getPaymentService().processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());

        ReportService reports = new ReportService(store.getBookingService(), store.getVehicleService(), store.getPaymentService());
        String expected = String.format("₱%,.2f", reports.getRevenueForMonth(YearMonth.now()));
        assertEquals("₱2,500.00", expected);

        List<String> shown = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> shown.addAll(labels(new DashboardPanel(store.getVehicleService(),
                store.getAuthService(), store.getBookingService(), store.getPaymentService()))));
        int at = shown.indexOf("Revenue");
        assertTrue(at >= 0, shown.toString());
        assertEquals(expected, shown.get(at + 1), "the Revenue card shows Reports' Revenue This Month");
    }
}
