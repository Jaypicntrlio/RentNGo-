package service;

import model.*;
import model.exceptions.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BookingService {
    private static final String HEADER = "RentNGo bookings - id\tcustomerId\tvehicleId\tstart\tend\ttotalCost\tstatus"
            + "\tactualReturn\tadditionalCharge\tlateFee\tamountPaid\tamountRefunded\tprocessedBy\tprocessedAt\treturnNote";

    /** A return dated other than today needs a reason at least this long. */
    public static final int MIN_RETURN_NOTE_LENGTH = 10;

    private List<Booking> bookings;
    private VehicleService vehicleService;
    private AuthService authService;
    private int bookingCounter;
    private final File dataFile;
    private String loadError;
    private boolean readOnly;
    private AuditLog audit = AuditLog.NONE;
    /** Why changes are refused while read-only (unreadable file, or changed by another program). */
    private String readOnlyMessage;
    /** The file as it was when last loaded or saved; a save is refused if it changed since. */
    private TsvFile.Stamp stamp;
    private static final String ID_PREFIX = "BKG";

    /** Bookings saved in data/bookings.txt. */
    public BookingService(VehicleService vehicleService, AuthService authService) {
        this(vehicleService, authService, new File(ProjectPaths.dir("data"), "bookings.txt"));
    }

    /** Pass null to keep bookings in memory only (tests). */
    public BookingService(VehicleService vehicleService, AuthService authService, File dataFile) {
        this.vehicleService = vehicleService;
        this.authService = authService;
        if (vehicleService != null) {
            vehicleService.useAuthService(authService); // vehicle changes are checked against the same session
            vehicleService.useOpenBookingCount(id -> bookings.stream()
                    .filter(b -> b.getVehicleId().equals(id) && isOpen(b)).count());
        }
        this.dataFile = dataFile;
        this.stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
        this.bookings = new ArrayList<>();
        this.bookingCounter = 1;
        if (dataFile != null && dataFile.isFile()) {
            try {
                load();
            } catch (IOException e) {
                // keep the file as it is and never save over it (or reuse its booking numbers)
                readOnly = true;
                loadError = TsvFile.describeUnreadable(dataFile, e);
                readOnlyMessage = loadError;
            }
        }
    }

    // ===== Saving =====

    /** Where booking actions are recorded (data/audit.txt). */
    public void setAuditLog(AuditLog audit) {
        this.audit = audit != null ? audit : AuditLog.NONE;
    }

    /** The audit log (for the admin Audit Log view and the payment service). */
    public AuditLog getAuditLog() {
        return audit;
    }

    /** Records an action only if it was actually saved. */
    private void logIfSaved(String action, String bookingId, String details) {
        if (!readOnly) audit.record(action, bookingId, details);
    }

    static String peso(BigDecimal amount) {
        return String.format("₱%,.2f", amount);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Why the saved file couldn't be (fully) loaded, or null if it loaded fine (shown at startup). */
    public String getLoadError() {
        return loadError;
    }

    /** True when bookings.txt couldn't be read or was changed by another program; changes are refused then. */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** Why bookings can't be changed right now, or null. */
    public String getReadOnlyMessage() {
        return readOnly ? readOnlyMessage : null;
    }

    /**
     * Loads every booking row that can be read; bad rows are skipped and reported in loadError.
     * The next booking number comes after the saved "#seq" line and every ID seen in the file
     * (skipped rows included), so a number is never handed out twice.
     */
    private void load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(dataFile);
        stamp = contents.stamp;
        TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
        int lastUsed = TsvFile.readSequence(contents, ID_PREFIX, warnings);
        for (TsvFile.Row row : contents.rows) {
            String[] f = row.fields;
            lastUsed = Math.max(lastUsed, TsvFile.idNumber(f.length > 0 ? f[0] : "", ID_PREFIX));
            try {
                bookings.add(parseBooking(f));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        bookingCounter = lastUsed + 1;
        loadError = warnings.summary();
    }

    private static Booking parseBooking(String[] f) {
        TsvFile.requireFields(f, 12);
        if (TsvFile.idNumber(f[0], ID_PREFIX) == 0) {
            throw new IllegalArgumentException("bad booking ID '" + f[0] + "'");
        }
        Booking b = new Booking(f[0], f[1], f[2], LocalDate.parse(f[3]), LocalDate.parse(f[4]), Money.parse(f[5]));
        b.setStatus(BookingStatus.valueOf(f[6]));
        b.setActualReturnDate(f[7].isEmpty() ? null : LocalDate.parse(f[7]));
        b.setAdditionalCharge(Money.parse(f[8]));
        b.setLateFee(Money.parse(f[9]));
        b.setAmountPaid(Money.parse(f[10]));
        b.setAmountRefunded(Money.parse(f[11]));
        // added later: older files simply don't have these columns
        b.setProcessedBy(f.length > 12 && !f[12].isEmpty() ? f[12] : null);
        b.setProcessedAt(f.length > 13 && !f[13].isEmpty() ? LocalDateTime.parse(f[13]) : null);
        b.setReturnNote(f.length > 14 && !f[14].isEmpty() ? f[14] : null);
        return b;
    }

    private void requireWritable() throws BookingException {
        if (readOnly) {
            throw new BookingException(readOnlyMessage);
        }
    }

    /**
     * Writes all bookings and the last-used booking number; does nothing for an in-memory service.
     * If the file was changed by another program, nothing is written and the app is told.
     */
    void save() {
        try {
            write();
        } catch (TsvFile.DataChangedException e) {
            TsvFile.reportChanged(e);
        }
    }

    /** Like save(), but hands a refused save back to the caller so it can undo its change. */
    private void write() throws TsvFile.DataChangedException {
        if (dataFile == null) return;
        if (readOnly) {
            System.err.println("Not saving bookings: " + readOnlyMessage);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Booking b : bookings) {
            rows.add(new String[]{b.getBookingId(), b.getCustomerId(), b.getVehicleId(),
                    b.getStartDate().toString(), b.getEndDate().toString(), b.getTotalCost().toPlainString(),
                    b.getStatus().name(), b.getActualReturnDate() == null ? "" : b.getActualReturnDate().toString(),
                    b.getAdditionalCharge().toPlainString(), b.getLateFee().toPlainString(),
                    b.getAmountPaid().toPlainString(), b.getAmountRefunded().toPlainString(),
                    nz(b.getProcessedBy()), b.getProcessedAt() == null ? "" : b.getProcessedAt().toString(),
                    nz(b.getReturnNote())});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, bookingCounter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another copy of RentNGo (or another program) wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            System.err.println("Could not save bookings: " + e.getMessage());
        }
    }

    /**
     * Saves; if the file changed on disk the save is refused, {@code undo} puts the booking back
     * as it was, and the caller gets the reason. Use for every change so a refused save never
     * leaves the app showing something that isn't in the file.
     */
    void saveOrUndo(Runnable undo) throws BookingException {
        try {
            write();
        } catch (TsvFile.DataChangedException e) {
            undo.run();
            throw new BookingException(e.getMessage());
        }
    }

    /** Puts the booking back to an earlier copy (taken with {@link Booking#copy()}) and saves. */
    void restore(Booking snapshot) {
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).getBookingId().equals(snapshot.getBookingId())) {
                bookings.set(i, snapshot.copy());
                save();
                return;
            }
        }
    }

    private Runnable undoTo(Booking booking) {
        Booking before = booking.copy();
        return () -> bookings.set(bookings.indexOf(booking), before);
    }

    // ===== Availability =====

    private static boolean isOpen(Booking b) {
        return b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.ACTIVE;
    }

    /**
     * True when no CONFIRMED or ACTIVE booking of this vehicle overlaps the given dates.
     *
     * Dates follow the same rule as {@link Booking#calculateDays}: the end date is the return day,
     * so it is exclusive. A car booked 1st-5th is returned on the 5th and can be booked again from
     * the 5th. Two bookings overlap only if newStart &lt; existingEnd and existingStart &lt; newEnd.
     *
     * An overdue rental (end date already passed, not returned) is treated as ending tomorrow, so it
     * blocks the vehicle through today. Completed and cancelled bookings never block. Whether the
     * vehicle is in service is a separate check ({@link Vehicle#isAvailable()}).
     * An end that isn't after the start is checked as the single day {@code start}.
     */
    public boolean isVehicleAvailable(String vehicleId, LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        LocalDate until = end.isAfter(start) ? end : start.plusDays(1);
        return bookings.stream()
                .filter(b -> b.getVehicleId().equals(vehicleId) && isOpen(b))
                .noneMatch(b -> overlaps(start, until, b.getStartDate(), blockedUntil(b, today)));
    }

    /** True when [aStart, aEnd) and [bStart, bEnd) share at least one day; end dates are exclusive. */
    static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** The (exclusive) day a booking stops blocking its vehicle: its end date, or tomorrow if it is overdue. */
    private static LocalDate blockedUntil(Booking b, LocalDate today) {
        return b.getEndDate().isBefore(today) ? today.plusDays(1) : b.getEndDate();
    }

    /** True when the vehicle is free for today, i.e. the day [today, tomorrow) (used by the vehicle tables). */
    public boolean isVehicleAvailableToday(String vehicleId) {
        LocalDate today = LocalDate.now();
        return isVehicleAvailable(vehicleId, today, today.plusDays(1));
    }


    // ===== Booking lifecycle =====

    public Booking createBooking(String customerId, String vehicleId, LocalDate startDate, LocalDate endDate) throws BookingException, VehicleNotFoundException {
        // an admin can book for anyone; a customer only for themselves
        String denied = authService == null ? AuthService.NOT_LOGGED_IN : authService.adminOrSelf(customerId, "make bookings");
        if (denied != null) throw new BookingException(denied);
        requireWritable();
        if (startDate == null || endDate == null) {
            throw new BookingException("Start date and end date are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BookingException("End date must be after the start date (minimum rental is 1 day)");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BookingException("Start date cannot be in the past");
        }

        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        Customer customer = authService.findCustomerRecord(customerId)
                .orElseThrow(() -> new BookingException("Customer not found: " + customerId));
        if (!customer.isActive()) {
            throw new BookingException(customer.getFullName() + "'s account is deactivated, so no new bookings can be made for it.");
        }

        if (!vehicle.isAvailable()) {
            throw new BookingException("This vehicle is out of service and can't be booked right now");
        }
        if (!isVehicleAvailable(vehicleId, startDate, endDate)) {
            throw new BookingException("This vehicle is already booked for some of the selected dates");
        }

        long days = Booking.calculateDays(startDate, endDate);
        BigDecimal totalCost = vehicle.calculateRentalCost((int) days);

        String bookingId = "BKG" + String.format("%04d", bookingCounter++);
        Booking booking = new Booking(bookingId, customerId, vehicleId, startDate, endDate, totalCost);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookings.add(booking);
        try {
            write();
        } catch (TsvFile.DataChangedException e) {
            // the other copy may have booked these dates: undo, and keep the number unused
            bookings.remove(booking);
            bookingCounter--;
            throw new BookingException(e.getMessage());
        }
        audit.record(AuditLog.BOOKING_CREATED, bookingId, "customer " + customerId + ", vehicle " + vehicleId
                + ", " + startDate + " to " + endDate + " (" + days + (days == 1 ? " day" : " days") + "), total "
                + peso(totalCost));
        return booking.copy();
    }

    /**
     * Cancels a rental that has not started yet. Cancelling less than 24 hours before the start
     * keeps a cancellation fee (CANCELLATION_FEE_RATE of what was paid); the rest of the payment is
     * recorded as refunded on the booking. The refund is returned so the caller can record the refund payment.
     */
    public BigDecimal cancelBooking(String bookingId) throws BookingException {
        requireWritable();
        // another customer's booking is reported as not found, so IDs can't be probed
        Booking booking = find(bookingId).filter(this::visible)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));
        String denied = authService.adminOrSelf(booking.getCustomerId(), "cancel bookings");
        if (denied != null) throw new BookingException(denied);

        if (booking.getStatus() == BookingStatus.ACTIVE) {
            throw new BookingException("This vehicle has already been picked up, so the booking can't be cancelled. "
                    + "Please return the vehicle instead.");
        }
        checkTransition(booking, BookingStatus.CANCELLED);
        if (booking.getStartDate().isBefore(LocalDate.now())) {
            throw new BookingException("This rental has already started, so it can't be cancelled. "
                    + "Please return the vehicle instead.");
        }

        BigDecimal fee = calculateCancellationFee(booking);
        BigDecimal refund = booking.getNetPaid().subtract(fee);
        Runnable undo = undoTo(booking);
        booking.setAmountRefunded(booking.getAmountRefunded().add(refund));
        booking.setStatus(BookingStatus.CANCELLED);
        saveOrUndo(undo); // a refused save must not hand back a refund for a booking still open on disk
        String details = "cancelled before " + booking.getStartDate();
        if (!Money.isPositive(booking.getAmountPaid())) {
            details += ", nothing paid";
        } else {
            details += Money.isPositive(fee)
                    ? ", less than 24 hours before the start: fee " + peso(fee) + " kept, refund " + peso(refund)
                    : ", full refund " + peso(refund);
        }
        logIfSaved(AuditLog.CANCELLED, bookingId, details);
        return refund;
    }

    // ===== Cancellation policy =====

    /** Share of what was paid that is kept when cancelling less than 24 hours before the start date. */
    public static final String CANCELLATION_FEE_RATE = "0.20";
    /** Cancelling at least this long before the rental starts (midnight of the start date) is free. */
    public static final java.time.Duration FREE_CANCELLATION_NOTICE = java.time.Duration.ofHours(24);

    /** True when the rental starts in less than 24 hours (counting from midnight of the start date). */
    public boolean isLateCancellation(Booking booking) {
        LocalDateTime start = booking.getStartDate().atStartOfDay();
        return java.time.Duration.between(LocalDateTime.now(), start).compareTo(FREE_CANCELLATION_NOTICE) < 0;
    }

    /** The fee kept if this booking were cancelled now: 20% of what was paid if late, otherwise nothing. */
    public BigDecimal calculateCancellationFee(Booking booking) {
        if (!isLateCancellation(booking) || !Money.isPositive(booking.getNetPaid())) return Money.ZERO;
        return Money.times(booking.getNetPaid(), CANCELLATION_FEE_RATE);
    }

    /** What would be refunded if this booking were cancelled now. */
    public BigDecimal previewCancellationRefund(Booking booking) {
        BigDecimal net = booking.getNetPaid();
        return Money.isPositive(net) ? net.subtract(calculateCancellationFee(booking)) : Money.ZERO;
    }

    /** Each late day costs this many times the vehicle's daily rate. */
    public static final String LATE_FEE_MULTIPLIER = "1.5";

    /**
     * Late fee for returning on the given date: 1.5x the vehicle's daily rate for each day past
     * the booked end date. Early returns are not refunded.
     */
    public BigDecimal calculateLateFee(Booking booking, LocalDate returnDate) {
        long lateDays = ChronoUnit.DAYS.between(booking.getEndDate(), returnDate);
        if (lateDays <= 0) return Money.ZERO;
        return vehicleService.getVehicleById(booking.getVehicleId())
                .map(v -> Money.times(v.getPricePerDay().multiply(BigDecimal.valueOf(lateDays)), LATE_FEE_MULTIPLIER))
                .orElse(Money.ZERO);
    }

    /** What the customer will still owe if the vehicle comes back on this date with these extra charges. */
    public BigDecimal previewAmountDue(Booking booking, LocalDate returnDate, BigDecimal additionalCharge) {
        BigDecimal finalAmount = booking.getTotalCost()
                .add(calculateLateFee(booking, returnDate))
                .add(Money.round(additionalCharge));
        BigDecimal due = finalAmount.subtract(booking.getAmountPaid());
        return due.signum() > 0 ? due : Money.ZERO;
    }

    /** Records that the customer collected the vehicle: CONFIRMED -> ACTIVE. */
    public Booking markPickedUp(String bookingId) throws BookingException {
        requireAdmin("record pick-ups");
        requireWritable();
        Booking booking = find(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));
        checkTransition(booking, BookingStatus.ACTIVE);
        if (LocalDate.now().isBefore(booking.getStartDate())) {
            throw new BookingException("This rental starts on " + booking.getStartDate()
                    + ", so the vehicle can't be picked up yet.");
        }
        Runnable undo = undoTo(booking);
        booking.setStatus(BookingStatus.ACTIVE);
        saveOrUndo(undo);
        logIfSaved(AuditLog.PICKED_UP, bookingId, "vehicle " + booking.getVehicleId() + " picked up by customer "
                + booking.getCustomerId() + " (rental " + booking.getStartDate() + " to " + booking.getEndDate() + ")");
        return booking.copy();
    }

    /**
     * Completes the rental (ACTIVE -> COMPLETED). A CONFIRMED rental that was never marked as
     * picked up is treated as picked up first, so it still passes through ACTIVE.
     */
    public Booking returnBooking(String bookingId, LocalDate returnDate, BigDecimal additionalCharge) throws BookingException {
        return returnBooking(bookingId, returnDate, additionalCharge, null);
    }

    /**
     * Completes the rental, recording who processed it and when. A return dated other than today
     * (which changes the late fee) needs a reason of at least MIN_RETURN_NOTE_LENGTH characters;
     * the reason is saved on the booking and written to the audit log.
     */
    public Booking returnBooking(String bookingId, LocalDate returnDate, BigDecimal additionalCharge, String note) throws BookingException {
        requireAdmin("record returns");
        requireWritable();
        Booking booking = find(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BookingException("Vehicle for this booking was already returned");
        }
        BookingStatus effective = booking.getStatus() == BookingStatus.CONFIRMED ? BookingStatus.ACTIVE : booking.getStatus();
        if (!canTransition(effective, BookingStatus.COMPLETED)) {
            checkTransition(booking, BookingStatus.COMPLETED);
        }
        if (returnDate == null) {
            throw new BookingException("Return date is required");
        }
        if (returnDate.isBefore(booking.getStartDate())) {
            throw new BookingException("Return date cannot be before the rental start date (" + booking.getStartDate() + ")");
        }
        if (returnDate.isAfter(LocalDate.now())) {
            throw new BookingException("Return date cannot be in the future");
        }
        if (additionalCharge == null || additionalCharge.signum() < 0) {
            throw new BookingException("Additional charge cannot be negative");
        }
        String reason = note == null ? "" : note.trim();
        boolean backdated = !returnDate.equals(LocalDate.now());
        if (backdated && reason.length() < MIN_RETURN_NOTE_LENGTH) {
            throw new BookingException("The return date isn't today, so a reason is required (at least "
                    + MIN_RETURN_NOTE_LENGTH + " characters), e.g. when the vehicle was actually dropped off.");
        }

        User processor = authService != null ? authService.getCurrentUser() : null;
        BigDecimal lateFee = calculateLateFee(booking, returnDate);
        Runnable undo = undoTo(booking);
        booking.setActualReturnDate(returnDate);
        booking.setLateFee(lateFee);
        booking.setAdditionalCharge(additionalCharge);
        booking.setProcessedBy(processor != null ? processor.getUserId() : null);
        booking.setProcessedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        booking.setReturnNote(reason.isEmpty() ? null : reason);
        booking.setStatus(BookingStatus.COMPLETED);
        saveOrUndo(undo);

        long lateDays = Math.max(0, ChronoUnit.DAYS.between(booking.getEndDate(), returnDate));
        StringBuilder details = new StringBuilder("return date " + returnDate);
        if (backdated) details.append(" (processed on ").append(LocalDate.now()).append(")");
        details.append(", end date ").append(booking.getEndDate())
                .append(", late fee ").append(peso(lateFee))
                .append(lateDays > 0 ? " (" + lateDays + (lateDays == 1 ? " day late)" : " days late)") : "")
                .append(", additional charge ").append(peso(Money.round(additionalCharge)));
        if (!reason.isEmpty()) details.append(", reason: ").append(reason);
        logIfSaved(AuditLog.RETURNED, bookingId, details.toString());
        return booking.copy();
    }

    // ===== Queries =====
    // Every read returns copies, filtered to what the logged-in user may see: all bookings for an
    // admin, their own for a customer, none when nobody is logged in.

    /** True when the logged-in user may see this booking. */
    private boolean visible(Booking b) {
        return authService != null && authService.canSee(b.getCustomerId());
    }

    private List<Booking> visibleCopies(java.util.function.Predicate<Booking> filter) {
        return bookings.stream()
                .filter(b -> filter.test(b) && visible(b))
                .map(Booking::copy)
                .collect(Collectors.toList());
    }

    /** The real booking, for changes made inside the service package; never handed to windows. */
    Optional<Booking> find(String bookingId) {
        return bookings.stream()
                .filter(b -> b.getBookingId().equals(bookingId))
                .findFirst();
    }

    public List<Booking> getReturnableBookings() {
        return visibleCopies(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.ACTIVE);
    }

    /** Bookings that still have money owing, including extra charges added at return. */
    public List<Booking> getUnpaidBookings() {
        return visibleCopies(b -> Money.isPositive(b.getBalanceDue()));
    }

    /** A copy of the booking, or empty if it doesn't exist or the logged-in user may not see it. */
    public Optional<Booking> getBookingById(String bookingId) {
        return find(bookingId).filter(this::visible).map(Booking::copy);
    }

    /** Every booking the logged-in user may see. */
    public List<Booking> getAllBookings() {
        return visibleCopies(b -> true);
    }

    public List<Booking> getBookingsByCustomer(String customerId) {
        return visibleCopies(b -> b.getCustomerId().equals(customerId));
    }

    /**
     * The bookings this user may see: every booking for an admin, only their own for a customer.
     * Only works for the user who is logged in: anyone else (or null) gets an empty list, so passing
     * another account can't widen what a window sees. Use this for any list a user can view or export.
     */
    public List<Booking> getBookingsVisibleTo(User user) {
        User current = authService == null ? null : authService.getCurrentUser();
        if (user == null || current == null || !current.getUserId().equals(user.getUserId())) {
            return new ArrayList<>();
        }
        return getAllBookings();
    }

    public List<Booking> getBookingsByVehicle(String vehicleId) {
        return visibleCopies(b -> b.getVehicleId().equals(vehicleId));
    }

    public List<Booking> getBookingsByStatus(BookingStatus status) {
        return visibleCopies(b -> b.getStatus() == status);
    }

    /** The session the access rules are checked against (for PaymentService). */
    AuthService auth() {
        return authService;
    }

    /** Throws unless an admin is logged in. */
    private void requireAdmin(String action) throws BookingException {
        String denied = authService == null ? AuthService.NOT_LOGGED_IN : authService.adminOnly(action);
        if (denied != null) throw new BookingException(denied);
    }

    /**
     * Changes a booking's status, allowing only these steps:
     * PENDING -> CONFIRMED or CANCELLED, CONFIRMED -> ACTIVE or CANCELLED, ACTIVE -> COMPLETED.
     * COMPLETED and CANCELLED are final. Cancelling, picking up and completing go through
     * their own methods so refunds, start-date checks and late fees are applied.
     */
    public void updateBookingStatus(String bookingId, BookingStatus status) throws BookingException {
        requireAdmin("change booking status");
        requireWritable();
        Booking booking = find(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));
        checkTransition(booking, status);
        switch (status) {
            case ACTIVE:
                markPickedUp(bookingId);
                break;
            case COMPLETED:
                returnBooking(bookingId, LocalDate.now(), Money.ZERO);
                break;
            case CANCELLED:
                cancelBooking(bookingId);
                break;
            default:
                Runnable undo = undoTo(booking);
                booking.setStatus(status);
                saveOrUndo(undo);
        }
    }

    // ===== Status rules =====

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(BookingStatus.PENDING, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.ACTIVE, BookingStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(BookingStatus.ACTIVE, EnumSet.of(BookingStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(BookingStatus.COMPLETED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED_TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BookingStatus.class)).contains(to);
    }

    private static void checkTransition(Booking booking, BookingStatus to) throws BookingException {
        BookingStatus from = booking.getStatus();
        if (canTransition(from, to)) return;

        String id = booking.getBookingId();
        Set<BookingStatus> next = ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BookingStatus.class));
        if (next.isEmpty()) {
            throw new BookingException("Booking " + id + " is already " + label(from)
                    + ". That is final, so it can't be changed to " + label(to) + ".");
        }
        String options = next.stream().map(BookingService::label).collect(Collectors.joining(" or "));
        throw new BookingException("Booking " + id + " can't go from " + label(from) + " to " + label(to)
                + ". A " + label(from) + " booking can only become " + options + ".");
    }

    private static String label(BookingStatus status) {
        return status.name().toLowerCase();
    }
}
