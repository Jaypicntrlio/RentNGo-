package gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.AccountStore;
import service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** JUnit 5 tests for WindowSession. Windows are packed (made displayable) but never shown. */
class WindowSessionTest {

    @TempDir
    Path dataDir;

    @BeforeEach
    void needsDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to create windows");
    }

    @AfterEach
    void cleanUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> WindowSession.closeAllExcept(null));
    }

    private static JFrame packedFrame() {
        JFrame frame = new JFrame();
        frame.pack(); // displayable, not visible
        return frame;
    }

    @Test
    void closeAllExceptKeepsOnlyTheGivenWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame keep = packedFrame();
            JFrame toolA = packedFrame();
            JFrame toolB = packedFrame();
            JDialog dialog = new JDialog(toolA);
            dialog.pack();

            WindowSession.closeAllExcept(keep);

            assertTrue(keep.isDisplayable());
            assertFalse(toolA.isDisplayable());
            assertFalse(toolB.isDisplayable());
            assertFalse(dialog.isDisplayable());
        });
    }

    @Test
    void closeAllExceptNullClosesEverything() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame a = packedFrame();
            JFrame b = packedFrame();
            WindowSession.closeAllExcept(null);
            assertFalse(a.isDisplayable());
            assertFalse(b.isDisplayable());
            for (Window w : Window.getWindows()) assertFalse(w.isDisplayable());
        });
    }

    @Test
    void adminSessionOnlyWhileAnAdminIsLoggedIn() throws Exception {
        AuthService auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        assertFalse(WindowSession.isAdminSession(auth), "nobody logged in");
        auth.login("admin", "admin123");
        assertTrue(WindowSession.isAdminSession(auth));
        auth.logout();
        assertFalse(WindowSession.isAdminSession(auth), "after logout");
        auth.login("john", "john123");
        assertFalse(WindowSession.isAdminSession(auth), "a customer is not an admin");
        assertFalse(WindowSession.isAdminSession(null));
    }

    @Test
    void requireAdminLetsAnAdminThroughWithoutClosing() throws Exception {
        AuthService auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        auth.login("admin", "admin123");
        SwingUtilities.invokeAndWait(() -> {
            JFrame tool = packedFrame();
            assertTrue(WindowSession.requireAdmin(tool, auth));
            assertTrue(tool.isDisplayable());
        });
    }
}
