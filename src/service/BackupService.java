package service;

import model.exceptions.ValidationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backs up the whole data/ folder to backups/rentngo-YYYY-MM-DD-HHmm.zip and restores it from such
 * a file (admins only). A restore:
 * <ol>
 *   <li>never runs while another copy of RentNGo has the data folder open ({@link AppLock});</li>
 *   <li>checks the whole backup first (a RentNGo backup, safe file names, a usable accounts file);</li>
 *   <li>saves the current data as a "-before-restore" backup, so a restore can itself be undone;</li>
 *   <li>swaps the files, putting the old ones back if anything goes wrong part-way.</li>
 * </ol>
 * Both actions are written to the audit log. After a restore the app must restart, because every
 * service still holds the old data in memory.
 */
public class BackupService {

    /** A small text file inside every backup, so a random zip is never restored by mistake. */
    public static final String MANIFEST = "rentngo-backup.txt";
    public static final String FILE_PREFIX = "rentngo-";
    public static final String BEFORE_RESTORE_SUFFIX = "-before-restore";
    /** A backup must contain the accounts, or nobody could log in after restoring it. */
    static final String REQUIRED_FILE = "accounts.txt";
    /** Refuses suspiciously large backups (e.g. a "zip bomb") before writing anything. */
    static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    static final int MAX_FILES = 500;

    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final File dataDir;
    private final File backupsDir;
    private final AuthService auth;
    private AuditLog audit = AuditLog.NONE;
    /** The lock this running app holds on dataDir, or null (then a restore takes the lock itself). */
    private AppLock appLock;

    /**
     * @param dataDir    the folder to back up and restore (data/)
     * @param backupsDir where backups are written (backups/, beside data/)
     */
    public BackupService(File dataDir, File backupsDir, AuthService auth) {
        this.dataDir = dataDir;
        this.backupsDir = backupsDir;
        this.auth = auth;
    }

    void setAuditLog(AuditLog audit) {
        this.audit = audit != null ? audit : AuditLog.NONE;
    }

    /** The app passes the lock it took at start-up, so a restore knows no other copy is using the data. */
    public void useAppLock(AppLock lock) {
        this.appLock = lock;
    }

    public File getBackupsDir() {
        return backupsDir;
    }

    // ===== Backup =====

    /** Zips every file in data/ into a new backups/rentngo-YYYY-MM-DD-HHmm.zip and returns it. */
    public File backup() throws ValidationException, IOException {
        requireAdmin("back up the data");
        File zip = createZip(newBackupFile(""));
        audit.record(AuditLog.BACKUP_CREATED, null, zip.getName() + " (" + dataFiles().size() + " files)");
        return zip;
    }

    /** Every backup in backups/, newest first. */
    public List<File> listBackups() {
        File[] files = backupsDir.listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(".zip"));
        List<File> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) if (f.isFile()) list.add(f);
        }
        // newest first: by the date and time in the name, then -2, -3 ... (made later that minute)
        list.sort(Comparator.comparing(BackupService::minuteOf).thenComparingInt(BackupService::numberOf)
                .thenComparing(File::lastModified).reversed());
        return list;
    }

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile(
            "rentngo-(\\d{4}-\\d{2}-\\d{2}-\\d{4})(" + BEFORE_RESTORE_SUFFIX + ")?(?:-(\\d+))?\\.zip");

    /** "2026-11-19-1930" from the file name ("" for a name in another format). */
    private static String minuteOf(File f) {
        java.util.regex.Matcher m = NAME.matcher(f.getName());
        return m.matches() ? m.group(1) : "";
    }

    /** 1 for the first backup of that minute, 2 for "-2", ... */
    private static int numberOf(File f) {
        java.util.regex.Matcher m = NAME.matcher(f.getName());
        return m.matches() && m.group(3) != null ? Integer.parseInt(m.group(3)) : 1;
    }

    /** The files that make up the data: everything in data/ except the lock file and folders. */
    private List<File> dataFiles() {
        File[] files = dataDir.listFiles();
        List<File> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().equals(AppLock.LOCK_FILE)) list.add(f);
            }
        }
        list.sort(Comparator.comparing(File::getName));
        return list;
    }

    /** "rentngo-2026-11-19-1930{suffix}.zip", or "-2", "-3" ... when that name is already taken. */
    private File newBackupFile(String suffix) {
        backupsDir.mkdirs();
        String base = FILE_PREFIX + LocalDateTime.now().format(NAME_TIME) + suffix;
        File file = new File(backupsDir, base + ".zip");
        for (int n = 2; file.exists(); n++) {
            file = new File(backupsDir, base + "-" + n + ".zip");
        }
        return file;
    }

    /** Writes the zip next to its final name first, so a failed backup never leaves a half-written file. */
    private File createZip(File zip) throws IOException {
        File partial = new File(zip.getPath() + ".part");
        List<File> files = dataFiles();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(partial.toPath()))) {
            out.putNextEntry(new ZipEntry(MANIFEST));
            String user = auth.getCurrentUser() != null ? auth.getCurrentUser().getUserId() : "";
            out.write(("RentNGo backup\ncreated=" + LocalDateTime.now().withNano(0) + "\nby=" + user
                    + "\nfiles=" + files.size() + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (File f : files) {
                out.putNextEntry(new ZipEntry(f.getName()));
                Files.copy(f.toPath(), out);
                out.closeEntry();
            }
        } catch (IOException e) {
            Files.deleteIfExists(partial.toPath());
            throw new IOException("Could not write the backup: " + e.getMessage(), e);
        }
        Files.move(partial.toPath(), zip.toPath(), StandardCopyOption.ATOMIC_MOVE);
        return zip;
    }

    // ===== Restore =====

    /** What a restore did: the backup restored and where the data from before it was saved. */
    public static final class RestoreResult {
        private final File restored;
        private final File savedBefore;

        RestoreResult(File restored, File savedBefore) {
            this.restored = restored;
            this.savedBefore = savedBefore;
        }

        public File getRestored() { return restored; }
        /** The "-before-restore" backup of the data that was replaced. */
        public File getSavedBefore() { return savedBefore; }
    }

    /**
     * Replaces everything in data/ with the backup's files. The caller must restart the app
     * afterwards. Nothing is changed when the backup is refused or can't be read.
     */
    public RestoreResult restore(File backup) throws ValidationException, IOException {
        requireAdmin("restore data");
        AppLock tempLock = null;
        if (appLock != null) {
            if (!appLock.isHeld()) throw new ValidationException(LOCK_LOST);
        } else {
            tempLock = AppLock.tryAcquire(dataDir); // e.g. tools and tests: take the lock for the restore
            if (tempLock == null) throw new ValidationException(OTHER_COPY_RUNNING);
        }
        File staging = null;
        try {
            staging = Files.createTempDirectory(prepareBackupsDir(), ".restore-").toFile();
            String created = extractChecked(backup, staging);

            // keep the data we are about to replace, and say so in its log first
            File savedBefore = newBackupFile(BEFORE_RESTORE_SUFFIX);
            audit.record(AuditLog.DATA_RESTORED, null, "replacing this data with backup " + backup.getName()
                    + "; this data is saved as " + savedBefore.getName());
            createZip(savedBefore);

            swapIn(staging);

            // the restored data has its own audit log: the restore goes there too
            new AuditLog(new File(dataDir, "audit.txt"), auth::getCurrentUser).record(AuditLog.DATA_RESTORED, null,
                    "restored from backup " + backup.getName() + (created != null ? " (made " + created + ")" : "")
                            + "; the data from before the restore is saved as " + savedBefore.getName());
            return new RestoreResult(backup, savedBefore);
        } finally {
            if (staging != null) deleteFolder(staging);
            if (tempLock != null) tempLock.close();
        }
    }

    public static final String OTHER_COPY_RUNNING = "Another copy of RentNGo is using the data folder. "
            + "Close it before restoring a backup.";
    static final String LOCK_LOST = "RentNGo no longer holds the lock on its data folder, so it can't be sure "
            + "no other copy is using it. Restart RentNGo, then try again.";

    private java.nio.file.Path prepareBackupsDir() throws IOException {
        Files.createDirectories(backupsDir.toPath());
        return backupsDir.toPath();
    }

    /**
     * Unzips the backup into {@code into}, refusing anything that isn't a sound RentNGo backup.
     * Returns the "created" date from the manifest (or null).
     */
    private String extractChecked(File backup, File into) throws ValidationException, IOException {
        if (backup == null || !backup.isFile()) {
            throw new ValidationException("Backup file not found" + (backup != null ? ": " + backup.getName() : ""));
        }
        String created = null;
        boolean hasManifest = false;
        Set<String> names = new HashSet<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(backup)) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!isSafeName(name) || entry.isDirectory()) {
                    throw new ValidationException(notABackup(backup) + " (it contains \"" + name + "\").");
                }
                if (!names.add(name.toLowerCase(java.util.Locale.ROOT)) || names.size() > MAX_FILES + 1) {
                    throw new ValidationException(notABackup(backup) + " (repeated or too many files).");
                }
                File target = new File(into, name);
                try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target.toPath())) {
                    byte[] buffer = new byte[8192];
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        total += n;
                        if (total > MAX_TOTAL_BYTES) {
                            throw new ValidationException(notABackup(backup) + " (it is larger than "
                                    + MAX_TOTAL_BYTES / (1024 * 1024) + " MB when unzipped).");
                        }
                        out.write(buffer, 0, n);
                    }
                }
                if (name.equals(MANIFEST)) {
                    hasManifest = true;
                    for (String line : Files.readAllLines(target.toPath(), StandardCharsets.UTF_8)) {
                        if (line.startsWith("created=")) created = line.substring(8).replace('T', ' ');
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new ValidationException(notABackup(backup) + " (it isn't a readable zip file).");
        }
        if (!hasManifest) throw new ValidationException(notABackup(backup) + ".");
        Files.delete(new File(into, MANIFEST).toPath()); // not part of the data
        if (!new File(into, REQUIRED_FILE).isFile()) {
            throw new ValidationException("This backup has no " + REQUIRED_FILE + ", so nobody could log in after "
                    + "restoring it. Nothing was changed.");
        }
        checkAccountsUsable(into);
        return created;
    }

    /** Loads the backup's accounts from a throw-away copy, so a backup nobody could log in to is refused. */
    private void checkAccountsUsable(File extracted) throws IOException, ValidationException {
        File copy = Files.createTempDirectory(extracted.getParentFile().toPath(), ".check-").toFile();
        try {
            Files.copy(new File(extracted, REQUIRED_FILE).toPath(), new File(copy, REQUIRED_FILE).toPath());
            AuthService check = new AuthService(new AccountStore(new File(copy, REQUIRED_FILE)));
            if (check.isReadOnly()) {
                throw new ValidationException("This backup's accounts can't be used (" + check.getLoadError()
                        + "). Nothing was changed.");
            }
        } finally {
            deleteFolder(copy);
        }
    }

    /** A plain file name: no folders, no "..", not hidden, not the lock file. */
    static boolean isSafeName(String name) {
        return !name.isEmpty() && !name.contains("/") && !name.contains("\\") && !name.contains(":")
                && !name.startsWith(".") && !name.equals(AppLock.LOCK_FILE) && name.length() <= 120;
    }

    private static String notABackup(File f) {
        return f.getName() + " is not a RentNGo backup. Nothing was changed";
    }

    /**
     * Moves the current data files aside, moves the backup's files in, then deletes the old ones.
     * If a move fails part-way, everything is put back as it was.
     */
    private void swapIn(File staging) throws IOException {
        File aside = Files.createTempDirectory(backupsDir.toPath(), ".replaced-").toFile();
        List<File> movedAside = new ArrayList<>();
        List<File> movedIn = new ArrayList<>();
        try {
            for (File f : dataFiles()) {
                File to = new File(aside, f.getName());
                Files.move(f.toPath(), to.toPath());
                movedAside.add(to);
            }
            File[] incoming = staging.listFiles();
            if (incoming != null) {
                for (File f : incoming) {
                    File to = new File(dataDir, f.getName());
                    Files.move(f.toPath(), to.toPath());
                    movedIn.add(to);
                }
            }
        } catch (IOException e) {
            // put the data back exactly as it was
            for (File f : movedIn) Files.deleteIfExists(f.toPath());
            for (File f : movedAside) Files.move(f.toPath(), new File(dataDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            deleteFolder(aside);
            throw new IOException("Could not restore the backup (" + e.getMessage() + "). Your data was put back "
                    + "as it was.", e);
        }
        deleteFolder(aside);
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteFolder(f); else f.delete();
            }
        }
        folder.delete();
    }

    private void requireAdmin(String action) throws ValidationException {
        String denied = auth == null ? AuthService.NOT_LOGGED_IN : auth.adminOnly(action);
        if (denied != null) throw new ValidationException(denied);
    }
}
