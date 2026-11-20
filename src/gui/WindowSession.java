package gui;

import model.Admin;
import service.AuthService;

import javax.swing.*;
import java.awt.*;

/**
 * Ties windows to the login session: closes every other window on logout or a theme rebuild, and
 * lets admin tool windows check that an admin is still logged in before changing anything.
 */
public final class WindowSession {

    public static final String SESSION_ENDED = "Your session has ended. Please log in again.";

    private WindowSession() {}

    /** Set by the app at start-up (feature 9); null in tests and tools, which hides Backup and Restore. */
    private static service.BackupService backupService;
    private static Runnable restartAction;

    /**
     * Called once by app.RentNGo for each start: the backup service for this data folder, and how to
     * start the app again (load the data afresh and show the login screen) after a restore.
     */
    public static void useApp(service.BackupService backups, Runnable restart) {
        backupService = backups;
        restartAction = restart;
    }

    /** The backup service, or null when the app wasn't started through app.RentNGo. */
    public static service.BackupService getBackupService() {
        return backupService;
    }

    /**
     * Closes every window and starts RentNGo again with the data now on disk (after a restore every
     * service still holds the old data in memory). Without a restart action the app exits instead.
     */
    public static void restartApp() {
        closeAllExcept(null);
        if (restartAction != null) {
            restartAction.run();
        } else {
            JOptionPane.showMessageDialog(null, "Please start RentNGo again to load the restored data.",
                    "RentNGo", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }
    }

    /** Disposes every open window except {@code keep} (pass null to close them all). */
    public static void closeAllExcept(Window keep) {
        for (Window window : Window.getWindows()) {
            if (window != keep && window.isDisplayable()) {
                window.dispose();
            }
        }
    }

    /** True while an admin is logged in. */
    public static boolean isAdminSession(AuthService authService) {
        return authService != null && authService.getCurrentUser() instanceof Admin;
    }

    /**
     * Call before every save, payment, return or delete in an admin window. Returns true when an
     * admin is still logged in; otherwise says the session has ended, closes the window and returns false.
     */
    public static boolean requireAdmin(Window window, AuthService authService) {
        if (isAdminSession(authService)) return true;
        JOptionPane.showMessageDialog(window, SESSION_ENDED, "Session Ended", JOptionPane.WARNING_MESSAGE);
        window.dispose();
        return false;
    }
}
