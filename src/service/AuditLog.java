package service;

import model.User;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Append-only record of who did what: data/audit.txt, one tab-separated line per action
 * (timestamp, userId, action, bookingId, details). Lines are only ever added at the end; nothing
 * in the app rewrites or deletes them.
 */
public class AuditLog {

    private static final String HEADER = "RentNGo audit log - timestamp\tuserId\taction\tbookingId\tdetails";

    // Actions
    public static final String BOOKING_CREATED = "BOOKING_CREATED";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String RETURNED = "RETURNED";
    public static final String PAYMENT = "PAYMENT";
    public static final String CANCELLED = "BOOKING_CANCELLED";
    public static final String REFUND = "REFUND";
    public static final String VEHICLE_ADDED = "VEHICLE_ADDED";
    public static final String VEHICLE_EDITED = "VEHICLE_EDITED";
    public static final String VEHICLE_DELETED = "VEHICLE_DELETED";
    public static final String CUSTOMER_ADDED = "CUSTOMER_ADDED";
    public static final String CUSTOMER_REGISTERED = "CUSTOMER_REGISTERED";
    public static final String CUSTOMER_EDITED = "CUSTOMER_EDITED";
    public static final String CUSTOMER_DEACTIVATED = "CUSTOMER_DEACTIVATED";
    public static final String CUSTOMER_REACTIVATED = "CUSTOMER_REACTIVATED";
    public static final String ADMIN_CREATED = "ADMIN_CREATED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String LOGIN_LOCKED = "LOGIN_LOCKED";
    /** A change was taken back because its payment or refund could not be saved. */
    public static final String UNDONE = "UNDONE";

    /** Used when no audit file is set up (tests, in-memory services): records nothing. */
    public static final AuditLog NONE = new AuditLog(null, () -> null);

    /** One line of the log. */
    public static final class Entry {
        private final LocalDateTime timestamp;
        private final String userId;
        private final String action;
        private final String bookingId;
        private final String details;

        Entry(LocalDateTime timestamp, String userId, String action, String bookingId, String details) {
            this.timestamp = timestamp;
            this.userId = userId;
            this.action = action;
            this.bookingId = bookingId;
            this.details = details;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getUserId() { return userId; }
        public String getAction() { return action; }
        public String getBookingId() { return bookingId; }
        public String getDetails() { return details; }
    }

    private final File file;
    private final Supplier<User> currentUser;

    /**
     * @param file        the log file (data/audit.txt), or null to record nothing
     * @param currentUser who is logged in; their ID is written on each line
     */
    public AuditLog(File file, Supplier<User> currentUser) {
        this.file = file;
        this.currentUser = currentUser;
    }

    /**
     * Records an action by the logged-in user. bookingId may be null for non-booking actions.
     * Only the services in this package write lines, so a window can't add forged ones.
     */
    void record(String action, String bookingId, String details) {
        User user = currentUser.get();
        recordAs(user != null ? user.getUserId() : "", action, bookingId, details);
    }

    /** Records an action for a specific user ID (e.g. a new account created from the sign-up screen). */
    void recordAs(String userId, String action, String bookingId, String details) {
        if (file == null) return;
        String[] row = {LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), nz(userId), action,
                nz(bookingId), nz(details)};
        try {
            TsvFile.appendRow(file, HEADER, row);
        } catch (IOException e) {
            // the action itself already succeeded; make the missing audit line visible
            System.err.println("Could not write to " + file.getName() + ": " + e.getMessage() + " - " + String.join(" | ", row));
        }
    }

    /**
     * Every readable line, newest first, for an admin. Anyone else gets an empty list: the log
     * holds every customer's actions. Unreadable lines are skipped (the file itself is never changed).
     */
    public List<Entry> readNewestFirst() {
        User user = currentUser.get();
        if (!(user instanceof model.Admin) || !user.isActive() || user.isMustChangePassword()) {
            return new ArrayList<>();
        }
        return entriesNewestFirst();
    }

    /** All entries without the admin check, for code (and tests) inside the service package. */
    List<Entry> entriesNewestFirst() {
        List<Entry> entries = new ArrayList<>();
        if (file == null || !file.isFile()) return entries;
        try {
            for (TsvFile.Row row : TsvFile.read(file).rows) {
                String[] f = row.fields;
                try {
                    TsvFile.requireFields(f, 5);
                    entries.add(new Entry(LocalDateTime.parse(f[0]), f[1], f[2], f[3], f[4]));
                } catch (RuntimeException ignored) {
                    // a damaged line is left in the file and just not shown
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + file.getName() + ": " + e.getMessage());
        }
        // file order is oldest first; a stable sort keeps same-second lines in reverse file order
        java.util.Collections.reverse(entries);
        entries.sort(Comparator.comparing(Entry::getTimestamp).reversed());
        return entries;
    }

    public File getFile() {
        return file;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
