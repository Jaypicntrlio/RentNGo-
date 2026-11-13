package app;

import gui.LoginFrame;
import gui.Theme;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import service.AppLock;
import service.DataStore;
import service.ProjectPaths;
import service.TsvFile;

public class RentNGo {
    static final String ALREADY_RUNNING = "RentNGo is already running on this computer. Close the other window first.";

    /** Held for the whole session; a static field so the lock file is never closed early. */
    private static AppLock appLock;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // FlatLaf look-and-feel; the login screen always uses the dark theme
            Theme.use(Theme.Mode.DARK);

            // Only one copy of RentNGo may use data/ at a time, or saves would overwrite each other
            File dataDir = ProjectPaths.dir("data");
            try {
                appLock = AppLock.tryAcquire(dataDir);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "RentNGo can't open its data folder (" + e.getMessage() + ").",
                        "RentNGo", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            if (appLock == null) {
                JOptionPane.showMessageDialog(null, ALREADY_RUNNING, "RentNGo", JOptionPane.WARNING_MESSAGE);
                System.exit(1);
                return;
            }
            appLock.releaseOnExit();

            // A data file changed on disk since it was loaded: refuse to overwrite it and say so (once)
            AtomicBoolean changeShown = new AtomicBoolean();
            TsvFile.setChangedListener(message -> SwingUtilities.invokeLater(() -> {
                if (changeShown.compareAndSet(false, true)) {
                    JOptionPane.showMessageDialog(null, message, "RentNGo - Data Changed", JOptionPane.ERROR_MESSAGE);
                }
            }));

            // Loads accounts, vehicles, bookings and payments saved in data/
            DataStore store = new DataStore(dataDir, ProjectPaths.dir("images"));

            if (!store.getLoadProblems().isEmpty()) {
                StringBuilder message = new StringBuilder("Some saved data could not be loaded:\n\n");
                for (String problem : store.getLoadProblems()) {
                    message.append("• ").append(problem).append("\n\n");
                }
                if (store.accountsUnavailable()) {
                    // demo accounts are never created over real ones, so there is no safe way to log in
                    message.append("RentNGo can't start until the accounts file is fixed or restored. No files were changed.");
                    showProblems(message.toString(), "RentNGo - Accounts Unavailable");
                    System.exit(1);
                    return;
                }
                message.append("The app will continue with the data that could be loaded.");
                showProblems(message.toString(), "RentNGo - Data Problem");
            }

            new LoginFrame(store.getAuthService(), store.getVehicleService(),
                    store.getBookingService(), store.getPaymentService()).setVisible(true);
        });
    }

    /** Error dialog with a scrolling text box, since many skipped lines can be listed. */
    private static void showProblems(String message, String title) {
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new java.awt.Dimension(560, 260));
        JOptionPane.showMessageDialog(null, scroll, title, JOptionPane.ERROR_MESSAGE);
    }
}
