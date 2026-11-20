package service;

import model.Booking;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for Backup and Restore of the data folder (feature 9). */
class BackupRestoreTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path root;

    private File dataDir;
    private DataStore store;
    private BackupService backups;

    @BeforeEach
    void setUp() throws Exception {
        dataDir = root.resolve("data").toFile();
        open();
    }

    /** Starts the app's services on data/ again, logged in as admin. */
    private void open() throws Exception {
        store = new DataStore(dataDir, null);
        backups = store.getBackupService();
        store.getAuthService().login("admin", "admin123");
    }

    private Booking book(String vehicleId, int startOffset) throws Exception {
        return store.getBookingService().createBooking("CUS001", vehicleId, TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 1));
    }

    private List<String> bookingIds() {
        return store.getBookingService().getAllBookings().stream().map(Booking::getBookingId).collect(Collectors.toList());
    }

    private static List<String> namesIn(File zip) throws Exception {
        try (ZipFile z = new ZipFile(zip)) {
            return z.stream().map(ZipEntry::getName).sorted().collect(Collectors.toList());
        }
    }

    private List<String> auditActions() {
        return store.getBookingService().getAuditLog().entriesNewestFirst().stream()
                .map(AuditLog.Entry::getAction).collect(Collectors.toList());
    }

    private File beforeRestoreBackup() {
        return backups.listBackups().stream().filter(f -> f.getName().contains(BackupService.BEFORE_RESTORE_SUFFIX))
                .findFirst().orElse(null);
    }

    /** A zip with the given entries (name, text). */
    private File zip(String fileName, String... nameThenText) throws Exception {
        File f = root.resolve(fileName).toFile();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
            for (int i = 0; i < nameThenText.length; i += 2) {
                out.putNextEntry(new ZipEntry(nameThenText[i]));
                out.write(nameThenText[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return f;
    }

    // ===== Backup =====

    @Test
    void backupZipsTheWholeDataFolder() throws Exception {
        book("VEH001", 3);
        File zip = backups.backup();

        assertEquals(root.resolve("backups").toFile().getCanonicalFile(), zip.getParentFile().getCanonicalFile());
        assertTrue(zip.getName().matches("rentngo-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.zip"), zip.getName());
        List<String> names = namesIn(zip);
        assertTrue(names.containsAll(List.of(BackupService.MANIFEST, "accounts.txt", "vehicles.txt", "bookings.txt", "audit.txt")), names.toString());
        assertFalse(names.contains(AppLock.LOCK_FILE), "the lock file is never backed up");
        assertEquals(AuditLog.BACKUP_CREATED, auditActions().get(0));

        File second = backups.backup(); // the same minute: a new name, the first is kept
        assertNotEquals(zip, second);
        assertTrue(second.getName().endsWith("-2.zip"), second.getName());
        assertEquals(List.of(second, zip), backups.listBackups(), "newest first");
    }

    @Test
    void onlyAdminsCanBackUpOrRestore() throws Exception {
        File zip = backups.backup();
        store.getAuthService().logout();
        store.getAuthService().login("john", "john123");
        assertThrows(ValidationException.class, () -> backups.backup());
        assertThrows(ValidationException.class, () -> backups.restore(zip));
        store.getAuthService().logout();
        assertThrows(ValidationException.class, () -> backups.backup(), "nobody logged in");
        assertEquals(1, backups.listBackups().size());
    }

    // ===== Restore =====

    @Test
    void restoreBringsBackTheDataAndKeepsTheReplacedData() throws Exception {
        Booking kept = book("VEH001", 3);
        File zip = backups.backup();
        Booking later = book("VEH002", 5);
        Files.writeString(new File(dataDir, "notes-added-later.txt").toPath(), "x");

        BackupService.RestoreResult result = backups.restore(zip);
        assertEquals(zip, result.getRestored());
        assertFalse(new File(dataDir, "notes-added-later.txt").exists(), "the folder is exactly the backup");
        assertTrue(new File(dataDir, AppLock.LOCK_FILE).exists(), "the lock file is left alone");

        open(); // the app restarts after a restore
        assertEquals(List.of(kept.getBookingId()), bookingIds());
        assertEquals(AuditLog.DATA_RESTORED, auditActions().get(0), "the restored log records the restore");

        // the replaced data was saved first, so the restore itself can be undone
        File before = result.getSavedBefore();
        assertTrue(before.getName().contains("-before-restore"), before.getName());
        assertEquals(before, beforeRestoreBackup());
        backups.restore(before);
        open();
        assertEquals(List.of(kept.getBookingId(), later.getBookingId()), bookingIds());
        assertTrue(new File(dataDir, "notes-added-later.txt").exists());
        List<String> actions = auditActions();
        assertEquals(AuditLog.DATA_RESTORED, actions.get(0));
        assertEquals(AuditLog.DATA_RESTORED, actions.get(1), "the old log also says it was replaced");
    }

    @Test
    void restoreNeverRunsWhileAnotherCopyIsOpen() throws Exception {
        book("VEH001", 3);
        File zip = backups.backup();
        book("VEH002", 5);
        try (AppLock otherCopy = AppLock.tryAcquire(dataDir)) {
            assertNotNull(otherCopy);
            ValidationException e = assertThrows(ValidationException.class, () -> backups.restore(zip));
            assertEquals(BackupService.OTHER_COPY_RUNNING, e.getMessage());
        }
        assertEquals(2, bookingIds().size(), "nothing was changed");
        assertNull(beforeRestoreBackup());
    }

    @Test
    void theAppsOwnLockIsUsed() throws Exception {
        File zip = backups.backup();
        AppLock appLock = AppLock.tryAcquire(dataDir); // what app.RentNGo holds while running
        backups.useAppLock(appLock);
        backups.restore(zip); // allowed: this copy holds the lock
        assertTrue(appLock.isHeld(), "the lock is kept for the restart");

        appLock.close();
        assertThrows(ValidationException.class, () -> backups.restore(zip), "not sure any more that no other copy runs");
    }

    @Test
    void badBackupsAreRefusedAndChangeNothing() throws Exception {
        book("VEH001", 3);
        String bookingsBefore = Files.readString(new File(dataDir, "bookings.txt").toPath());
        String accounts = Files.readString(new File(dataDir, "accounts.txt").toPath());

        List<File> bad = new ArrayList<>();
        bad.add(zip("photos.zip", "accounts.txt", accounts)); // no manifest: not a RentNGo backup
        bad.add(zip("escape.zip", BackupService.MANIFEST, "RentNGo backup\n", "../evil.txt", "x"));
        bad.add(zip("folder.zip", BackupService.MANIFEST, "RentNGo backup\n", "sub/accounts.txt", accounts));
        bad.add(zip("nolock.zip", BackupService.MANIFEST, "RentNGo backup\n", AppLock.LOCK_FILE, "", "accounts.txt", accounts));
        bad.add(zip("noaccounts.zip", BackupService.MANIFEST, "RentNGo backup\n", "bookings.txt", bookingsBefore));
        bad.add(zip("noadmin.zip", BackupService.MANIFEST, "RentNGo backup\n", "accounts.txt", "nonsense\n"));
        File text = root.resolve("text.zip").toFile();
        Files.writeString(text.toPath(), "this is not a zip");
        bad.add(text);
        bad.add(root.resolve("missing.zip").toFile());

        for (File f : bad) {
            assertThrows(ValidationException.class, () -> backups.restore(f), f.getName());
        }
        assertEquals(bookingsBefore, Files.readString(new File(dataDir, "bookings.txt").toPath()));
        assertFalse(root.resolve("evil.txt").toFile().exists());
        assertNull(beforeRestoreBackup(), "nothing to undo: no restore happened");
        File[] leftovers = root.resolve("backups").toFile().listFiles((d, n) -> n.startsWith("."));
        assertEquals(0, leftovers == null ? 0 : leftovers.length, "temporary folders are cleaned up");
    }

    @Test
    void safeNames() {
        assertTrue(BackupService.isSafeName("bookings.txt"));
        assertTrue(BackupService.isSafeName("promo-attempts.properties"));
        for (String bad : new String[]{"", "../x", "a/b", "a\\b", "C:x", ".hidden", AppLock.LOCK_FILE}) {
            assertFalse(BackupService.isSafeName(bad), bad);
        }
    }
}
