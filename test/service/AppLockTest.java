package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for AppLock: only one RentNGo may use a data folder at a time. */
class AppLockTest {

    @TempDir
    Path dataDir;

    @Test
    void secondLockOnSameFolderFails() throws Exception {
        try (AppLock first = AppLock.tryAcquire(dataDir.toFile())) {
            assertNotNull(first, "the first copy gets the lock");
            assertTrue(first.isHeld());
            assertNull(AppLock.tryAcquire(dataDir.toFile()), "a second copy must not get the lock");
            assertTrue(first.isHeld(), "a failed attempt must not break the first lock");
        }
    }

    @Test
    void lockIsFreeAgainAfterRelease() throws Exception {
        AppLock first = AppLock.tryAcquire(dataDir.toFile());
        assertNotNull(first);
        first.close();
        first.close(); // safe to call twice
        try (AppLock again = AppLock.tryAcquire(dataDir.toFile())) {
            assertNotNull(again, "after the first copy exits, RentNGo can start again");
        }
    }

    @Test
    void differentFoldersDontBlockEachOther(@TempDir Path otherDir) throws Exception {
        try (AppLock a = AppLock.tryAcquire(dataDir.toFile()); AppLock b = AppLock.tryAcquire(otherDir.toFile())) {
            assertNotNull(a);
            assertNotNull(b);
        }
        assertTrue(new File(dataDir.toFile(), AppLock.LOCK_FILE).isFile(), "lock file lives in the data folder");
    }

    @Test
    void secondProcessCannotTakeTheLock() throws Exception {
        try (AppLock held = AppLock.tryAcquire(dataDir.toFile())) {
            assertNotNull(held);
            assertEquals("busy", runProbe());
        }
        assertEquals("locked", runProbe(), "once released, another process can take it");
    }

    /** Runs AppLockProbe in a separate Java process on the same folder and returns what it printed. */
    private String runProbe() throws Exception {
        String classPath = codeLocation(AppLock.class) + File.pathSeparator + codeLocation(AppLockProbe.class);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", classPath, AppLockProbe.class.getName(), dataDir.toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "probe process timed out");
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static String codeLocation(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }
}
