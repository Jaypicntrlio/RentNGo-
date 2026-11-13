package service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens all saved data in one place. Each service keeps its own file in data/
 * (accounts.txt, vehicles.txt, bookings.txt, payments.txt) and saves it after every change;
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

    /** True when accounts.txt couldn't be loaded (unreadable or no admin), so nobody can safely log in. */
    public boolean accountsUnavailable() {
        return authService.isReadOnly();
    }

    /** One message per data file with skipped lines or that could not be read; empty when everything loaded. */
    public List<String> getLoadProblems() {
        List<String> problems = new ArrayList<>();
        for (String error : new String[]{authService.getLoadError(), vehicleService.getLoadError(),
                bookingService.getLoadError(), paymentService.getLoadError()}) {
            if (error != null) problems.add(error);
        }
        return problems;
    }
}
