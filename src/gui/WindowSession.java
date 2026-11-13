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
