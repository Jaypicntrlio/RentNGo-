package gui;

import model.Admin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.DataStore;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The admin top bar: RentNGo crest, "RentNGo" as the name and "Admin" as the role. */
class AdminTopBarTest {

    @TempDir
    Path dir;

    private static List<JLabel> labels(Container c) {
        List<JLabel> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel) out.add((JLabel) k);
            if (k instanceof Container) out.addAll(labels((Container) k));
        }
        return out;
    }

    @Test
    void namesAreShownSafely() {
        Admin noName = new Admin("ADM009", "frontdesk", "x", "  ", "a@b.c", "1", "ADMIN");
        assertEquals("frontdesk", AdminDashboardFrame.displayName(noName), "a blank name falls back to the username");
        assertEquals("Maria Clara", AdminDashboardFrame.displayName(
                new Admin("ADM010", "maria", "x", " Maria Clara ", "a@b.c", "1", "SUPER_ADMIN")));
    }

    @Test
    void topBarShowsTheCrestRentNGoAndAdmin() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to build the window");
        DataStore store = new DataStore(dir.resolve("data").toFile(), null);
        store.getAuthService().login("admin", "admin123"); // saved with admin level SUPER_ADMIN
        List<JLabel> shown = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            AdminDashboardFrame frame = new AdminDashboardFrame(store.getAuthService(), store.getVehicleService(),
                    store.getBookingService(), store.getPaymentService());
            shown.addAll(labels(frame.getContentPane()));
            frame.dispose();
        });
        List<String> texts = new ArrayList<>();
        for (JLabel l : shown) texts.add(l.getText());
        assertTrue(texts.contains("RentNGo"), texts.toString());
        assertFalse(texts.contains("System Administrator"), "the person's name is only in the tooltip");
        assertTrue(shown.stream().anyMatch(l -> "RentNGo".equals(l.getText()) && l.getToolTipText() != null
                && l.getToolTipText().contains("System Administrator (admin)")), "hover shows who is logged in");
        assertTrue(texts.contains(AdminDashboardFrame.ADMIN_LABEL), texts.toString());
        assertFalse(texts.contains("SUPER_ADMIN"), "the internal admin level is never shown");
        assertTrue(shown.stream().anyMatch(l -> l.getIcon() instanceof AdminDashboardFrame.BrandAvatar), "RentNGo crest as the picture");
    }

    @Test
    void onlyOpensForAnAdmin() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to build the window");
        DataStore store = new DataStore(dir.resolve("data").toFile(), null);
        store.getAuthService().login("john", "john123");
        Throwable[] error = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                new AdminDashboardFrame(store.getAuthService(), store.getVehicleService(),
                        store.getBookingService(), store.getPaymentService());
            } catch (Throwable t) {
                error[0] = t;
            }
        });
        assertTrue(error[0] instanceof IllegalStateException, "a customer never gets the admin window: " + error[0]);
    }
}
