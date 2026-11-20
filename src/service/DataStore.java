package service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens all saved data in one place. Each service keeps its own file in data/
 * (accounts.txt, vehicles.txt with maintenance.txt and favorites.txt, bookings.txt with promocodes.txt
 * and reviews.txt, payments.txt) and saves it after every change;
 * this class creates them in the right order and collects the load problems, so the app can
 * warn the user instead of crashing.
 *
 * A bad line is skipped on its own (and the original file kept as a ".corrupt-..." backup).
 * Demo accounts and vehicles are only created on the first run, when their file doesn't exist.
 * A file that exists but can't be read at all is left untouched and its service goes
 * read-only; accounts with no admin are treated the same way, and the app won't start.
 */
public class DataStore {

    private final AuthService authService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final BackupService backupService;
    /** Set when loyalty points are still owed after start-up (shown with the load problems). */
    private String loyaltyProblem;

    /** Uses the project's data/ folder and images/ for photos. */
    public DataStore() {
        this(ProjectPaths.dir("data"), ProjectPaths.dir("images"));
    }

    /** Pass a different folder (e.g. a temporary one for tests); a null imagesDir leaves vehicle photos alone. */
    public DataStore(File dataDir, File imagesDir) {
        dataDir.mkdirs();
        authService = new AuthService(new AccountStore(new File(dataDir, "accounts.txt")));
        vehicleService = new VehicleService(new File(dataDir, "vehicles.txt"), imagesDir);
        bookingService = new BookingService(vehicleService, authService, new File(dataDir, "bookings.txt"));
        paymentService = new PaymentService(bookingService, new File(dataDir, "payments.txt"));

        // who did what: data/audit.txt, written under the logged-in user's ID
        AuditLog audit = new AuditLog(new File(dataDir, "audit.txt"), authService::getCurrentUser);
        authService.setAuditLog(audit);
        vehicleService.setAuditLog(audit);
        bookingService.setAuditLog(audit); // PaymentService records through the booking service's log

        // backups of the whole data folder go to backups/, beside data/
        backupService = new BackupService(dataDir, new File(dataDir.getAbsoluteFile().getParentFile(), "backups"), authService);
        backupService.setAuditLog(audit);

        // loyalty points that couldn't be saved last time are given now (never twice)
        int waiting = bookingService.givePendingLoyaltyPoints();
        if (waiting > 0) {
            loyaltyProblem = waiting + (waiting == 1 ? " rental still owes" : " rentals still owe")
                    + " loyalty points to customers because accounts.txt can't be saved. They will be added "
                    + "the next time RentNGo starts with a working accounts file.";
        }
    }

    public AuthService getAuthService() {
        return authService;
    }

    public VehicleService getVehicleService() {
        return vehicleService;
    }

    public BookingService getBookingService() {
        return bookingService;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }

    /** Backup and restore of the data folder (admins only). */
    public BackupService getBackupService() {
        return backupService;
    }

    /** True when accounts.txt couldn't be loaded (unreadable or no admin), so nobody can safely log in. */
    public boolean accountsUnavailable() {
        return authService.isReadOnly();
    }

    /** One message per data file with skipped lines or that could not be read; empty when everything loaded. */
    public List<String> getLoadProblems() {
        List<String> problems = new ArrayList<>();
        for (String error : new String[]{authService.getLoadError(), vehicleService.getLoadError(),
                bookingService.getLoadError(), paymentService.getLoadError(), loyaltyProblem}) {
            if (error != null) problems.add(error);
        }
        return problems;
    }
}
