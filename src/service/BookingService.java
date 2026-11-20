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
            + "\tactualReturn\tadditionalCharge\tlateFee\tamountPaid\tamountRefunded\tprocessedBy\tprocessedAt\treturnNote"
            + "\tpromoCode\tpromoDiscount\tloyaltyPointsUsed\tloyaltyPointsEarned\tloyaltyPointsPending\tpromoId"
            + "\tpickUpCondition\treturnCondition";

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
    /** Discount codes, saved next to bookings.txt as promocodes.txt. */
    private final PromoCodeStore promoCodes;
    /** Wrong promo codes per customer, so codes can't be found by guessing (promo-attempts.properties). */
    private final PromoGuessLimiter promoGuesses;
    /** Ratings and reviews of completed rentals, saved next to bookings.txt as reviews.txt. */
    private final ReviewService reviewService;

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
        this.promoCodes = new PromoCodeStore(dataFile == null ? null
                : new File(dataFile.getAbsoluteFile().getParentFile(), "promocodes.txt"));
        this.promoGuesses = new PromoGuessLimiter(dataFile == null ? null
                : new File(dataFile.getAbsoluteFile().getParentFile(), "promo-attempts.properties"));
        this.reviewService = new ReviewService(this, authService, dataFile == null ? null
                : new File(dataFile.getAbsoluteFile().getParentFile(), "reviews.txt"));
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
        List<String> errors = new ArrayList<>();
        for (String error : new String[]{loadError, promoCodes.getLoadError(), reviewService.getLoadError()}) {
            if (error != null) errors.add(error);
        }
        return errors.isEmpty() ? null : String.join("\n\n", errors);
    }

    /** Ratings and reviews of vehicles (written by customers after a completed rental). */
    public ReviewService getReviewService() {
        return reviewService;
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
        if (f.length > 16 && !f[15].isEmpty()) {
            b.setPromo(f[15], Money.parse(f[16]));
        }
        if (f.length > 18) {
            b.setLoyaltyPointsUsed(f[17].isEmpty() ? 0 : Integer.parseInt(f[17]));
            b.setLoyaltyPointsEarned(f[18].isEmpty() ? 0 : Integer.parseInt(f[18]));
        }
        if (f.length > 19 && !f[19].isEmpty()) {
            b.setLoyaltyPointsPending(Integer.parseInt(f[19]));
        }
        if (f.length > 20 && !f[20].isEmpty()) {
            b.setPromoId(f[20]);
        }
        // damage checklists (feature 8): "scratches|dents|FUEL|clean|notes", empty when not filled in
        if (f.length > 21) b.setPickUpCondition(VehicleCondition.decode(f[21]));
        if (f.length > 22) b.setReturnCondition(VehicleCondition.decode(f[22]));
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
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /** Like save(), but hands a refused save back to the caller so it can undo its change. */
    private void write() throws IOException {
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
                    nz(b.getReturnNote()), nz(b.getPromoCode()), b.getPromoDiscount().toPlainString(),
                    String.valueOf(b.getLoyaltyPointsUsed()), String.valueOf(b.getLoyaltyPointsEarned()),
                    String.valueOf(b.getLoyaltyPointsPending()), nz(b.getPromoId()),
                    b.getPickUpCondition() == null ? "" : b.getPickUpCondition().encode(),
                    b.getReturnCondition() == null ? "" : b.getReturnCondition().encode()});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, bookingCounter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another copy of RentNGo (or another program) wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save bookings: " + e.getMessage(), e);
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
        } catch (IOException e) { // changed on disk, or couldn't be written at all
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

    // ===== Availability calendar =====

    /**
     * Days a vehicle is taken by one open booking: from {@link #getStart()} up to, but not including,
     * {@link #getEndExclusive()} (the return day is free for the next renter). Which booking it is, and
     * whose, is only filled in when the logged-in user may see that booking; customers just see dates.
     */
    public static final class BookedPeriod {
        private final LocalDate start;
        private final LocalDate endExclusive;
        private final String bookingId;
        private final String customerId;
        private final boolean mine;

        BookedPeriod(LocalDate start, LocalDate endExclusive, String bookingId, String customerId, boolean mine) {
            this.start = start;
            this.endExclusive = endExclusive;
            this.bookingId = bookingId;
            this.customerId = customerId;
            this.mine = mine;
        }

        public LocalDate getStart() { return start; }
        /** The first day that is free again (the return day; tomorrow for a rental that is overdue). */
        public LocalDate getEndExclusive() { return endExclusive; }
        /** The booking ID, or null when the logged-in user may not see this booking. */
        public String getBookingId() { return bookingId; }
        /** The customer's ID, or null when the logged-in user may not see this booking. */
        public String getCustomerId() { return customerId; }
        /** True when it is the logged-in customer's own booking. */
        public boolean isMine() { return mine; }

        /** True when the vehicle is taken on this day (start included, end excluded). */
        public boolean covers(LocalDate day) {
            return !day.isBefore(start) && day.isBefore(endExclusive);
        }
    }

    /**
     * The open (CONFIRMED or ACTIVE) bookings of a vehicle as booked periods, oldest first, for the
     * availability calendar. Uses exactly the same days as {@link #isVehicleAvailable}, so a range the
     * calendar shows as free can be booked. Empty when nobody is logged in.
     */
    public List<BookedPeriod> getBookedPeriods(String vehicleId) {
        User viewer = authService != null ? authService.getCurrentUser() : null;
        if (viewer == null) return new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<BookedPeriod> periods = new ArrayList<>();
        for (Booking b : bookings) {
            if (!b.getVehicleId().equals(vehicleId) || !isOpen(b)) continue;
            boolean canSee = visible(b);
            boolean mine = viewer instanceof Customer && viewer.getUserId().equals(b.getCustomerId());
            periods.add(new BookedPeriod(b.getStartDate(), blockedUntil(b, today),
                    canSee ? b.getBookingId() : null, canSee ? b.getCustomerId() : null, mine));
        }
        periods.sort(java.util.Comparator.comparing(BookedPeriod::getStart));
        return periods;
    }

    /**
     * The first day in [start, end) that one of the periods takes, or null when the whole range is
     * free. The end (return) day itself may be booked: that is a same-day handover.
     */
    public static LocalDate firstBookedDay(List<BookedPeriod> periods, LocalDate start, LocalDate end) {
        LocalDate until = end.isAfter(start) ? end : start.plusDays(1);
        LocalDate first = null;
        for (BookedPeriod p : periods) {
            if (overlaps(start, until, p.getStart(), p.getEndExclusive())) {
                LocalDate clash = p.getStart().isAfter(start) ? p.getStart() : start;
                if (first == null || clash.isBefore(first)) first = clash;
            }
        }
        return first;
    }

    /** True when the vehicle is free for today, i.e. the day [today, tomorrow) (used by the vehicle tables). */
    public boolean isVehicleAvailableToday(String vehicleId) {
        LocalDate today = LocalDate.now();
        return isVehicleAvailable(vehicleId, today, today.plusDays(1));
    }


    // ===== Booking lifecycle =====

    /** Longest rental; also keeps day counts far away from int overflow in the price maths. */
    public static final int MAX_RENTAL_DAYS = 30;
    /** How far ahead a rental can start. */
    public static final int MAX_DAYS_AHEAD = 365;
    /** Upcoming or active rentals a customer may hold at once when booking for themselves (admins aren't limited). */
    public static final int MAX_OPEN_BOOKINGS_PER_CUSTOMER = 3;

    private long countOpenBookings(String customerId) {
        return bookings.stream().filter(b -> b.getCustomerId().equals(customerId) && isOpen(b)).count();
    }

    public Booking createBooking(String customerId, String vehicleId, LocalDate startDate, LocalDate endDate) throws BookingException, VehicleNotFoundException {
        return createBooking(customerId, vehicleId, startDate, endDate, null);
    }

    /**
     * Books the vehicle; with a promo code (null or blank for none) its discount is taken off the
     * rental cost and one of its uses is spent. An unknown, expired or used-up code stops the booking.
     */
    public Booking createBooking(String customerId, String vehicleId, LocalDate startDate, LocalDate endDate, String promoCode) throws BookingException, VehicleNotFoundException {
        return createBooking(customerId, vehicleId, startDate, endDate, promoCode, 0);
    }

    /**
     * As above, also spending the customer's loyalty points (0 for none): each point takes ₱1 off,
     * in steps of 100, after the promo discount and never more than the rental costs.
     */
    public Booking createBooking(String customerId, String vehicleId, LocalDate startDate, LocalDate endDate, String promoCode,
                                 int loyaltyPointsToUse) throws BookingException, VehicleNotFoundException {
        return createBooking(customerId, vehicleId, startDate, endDate, promoCode, loyaltyPointsToUse, null);
    }

    /**
     * As above. When an admin books with the customer's loyalty points, the customer must agree by
     * typing their own password ({@code customerPassword}); a customer booking for themselves doesn't.
     */
    public Booking createBooking(String customerId, String vehicleId, LocalDate startDate, LocalDate endDate, String promoCode,
                                 int loyaltyPointsToUse, String customerPassword) throws BookingException, VehicleNotFoundException {
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
        if (startDate.isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD))) {
            throw new BookingException("Bookings can be made at most " + MAX_DAYS_AHEAD + " days ahead");
        }
        if (Booking.calculateDays(startDate, endDate) > MAX_RENTAL_DAYS) {
            throw new BookingException("A rental can be at most " + MAX_RENTAL_DAYS + " days long");
        }
        if (authService.isCustomerSession() && countOpenBookings(customerId) >= MAX_OPEN_BOOKINGS_PER_CUSTOMER) {
            throw new BookingException("You already have " + MAX_OPEN_BOOKINGS_PER_CUSTOMER
                    + " upcoming or active rentals. Please return or cancel one before booking another.");
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

        // promo code and loyalty points: checked last, so nothing is spent on a booking that is otherwise refused
        PromoCode promo = null;
        BigDecimal discount = Money.ZERO;
        if (promoCode != null && !promoCode.isBlank()) {
            promo = usablePromoCode(promoCode);
            checkNotUsedBefore(promo, customerId);
            discount = promo.discountOn(totalCost);
            totalCost = totalCost.subtract(discount);
        }
        boolean pointsConfirmedByPassword = false;
        if (loyaltyPointsToUse != 0) {
            checkPointsToUse(customer, loyaltyPointsToUse, totalCost);
            if (authService.isAdminSession()) {
                // the points are the customer's: an admin may only spend them with the customer's consent
                try {
                    authService.confirmCustomerPassword(customerId, customerPassword);
                } catch (ValidationException e) {
                    throw new BookingException(e.getMessage());
                }
                pointsConfirmedByPassword = true;
            }
            totalCost = totalCost.subtract(Money.of(loyaltyPointsToUse));
        }
        if (promo != null) {
            try {
                promoCodes.setUsesLeft(promo, promo.getUsesLeft() - 1);
            } catch (IOException e) {
                throw new BookingException(e.getMessage()); // use not spent, nothing booked
            }
        }
        if (loyaltyPointsToUse > 0) {
            try {
                authService.changeLoyaltyPoints(customerId, -loyaltyPointsToUse);
            } catch (ValidationException e) {
                if (promo != null) giveBackUse(promo);
                throw new BookingException(e.getMessage()); // points not spent, nothing booked
            }
        }

        String bookingId = "BKG" + String.format("%04d", bookingCounter++);
        Booking booking = new Booking(bookingId, customerId, vehicleId, startDate, endDate, totalCost);
        booking.setStatus(BookingStatus.CONFIRMED);
        if (promo != null) {
            booking.setPromo(promo.getCode(), discount);
            booking.setPromoId(promo.getId());
        }
        booking.setLoyaltyPointsUsed(loyaltyPointsToUse);
        bookings.add(booking);
        try {
            write();
        } catch (IOException e) {
            // not saved (another copy may have booked these dates, or the file is locked): undo, keep the number unused
            bookings.remove(booking);
            bookingCounter--;
            if (promo != null) giveBackUse(promo);
            if (loyaltyPointsToUse > 0) giveBackPoints(customerId, loyaltyPointsToUse);
            throw new BookingException(e.getMessage());
        }
        audit.record(AuditLog.BOOKING_CREATED, bookingId, "customer " + customerId + ", vehicle " + vehicleId
                + ", " + startDate + " to " + endDate + " (" + days + (days == 1 ? " day" : " days") + "), total "
                + peso(totalCost) + (promo == null ? "" : " after promo code " + promo.getCode() + " (-" + peso(discount) + ")")
                + (loyaltyPointsToUse == 0 ? "" : " and " + loyaltyPointsToUse + " loyalty points (-" + peso(Money.of(loyaltyPointsToUse)) + ")"));
        if (loyaltyPointsToUse > 0) {
            audit.record(AuditLog.LOYALTY_SPENT, bookingId, customerId + " spent " + loyaltyPointsToUse
                    + " points for " + peso(Money.of(loyaltyPointsToUse)) + " off"
                    + (pointsConfirmedByPassword ? " (booked by an admin, confirmed with the customer's password)" : ""));
        }
        return booking.copy();
    }

    /** Cancels a booking and records its refund payment: PaymentService.cancelBooking. */
    @FunctionalInterface
    interface Canceller {
        BigDecimal cancel(String bookingId) throws BookingException;
    }

    /** Set by PaymentService when it is created, so every cancellation records its refund as a payment. */
    private Canceller canceller;

    void useCanceller(Canceller canceller) {
        this.canceller = canceller;
    }

    /**
     * Cancels a rental that has not started yet. Cancelling less than 24 hours before the start
     * keeps a cancellation fee (CANCELLATION_FEE_RATE of what was paid); the rest is refunded.
     * Goes through PaymentService.cancelBooking when there is one, so the refund is always recorded as
     * a payment (Reports stay right) and the promo use and points come back. Returns the refund.
     */
    public BigDecimal cancelBooking(String bookingId) throws BookingException {
        if (canceller != null) return canceller.cancel(bookingId);
        // no payment service (some tests): nothing can have been paid through it, so nothing to record
        BigDecimal refund = cancelAndMarkRefund(bookingId);
        giveBackDiscounts(bookingId);
        return refund;
    }

    /**
     * The cancellation itself, for PaymentService: marks the booking CANCELLED and the refund on it
     * and returns the refund; the caller records the refund payment and then calls giveBackDiscounts.
     */
    BigDecimal cancelAndMarkRefund(String bookingId) throws BookingException {
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
        // a CONFIRMED booking whose start has passed is a no-show (never picked up): only an admin can
        // cancel it, so it stops blocking the vehicle without charging a return for a car that never left
        boolean noShow = booking.getStartDate().isBefore(LocalDate.now());
        if (noShow && !authService.isAdminSession()) {
            throw new BookingException("This rental's start date has passed, so it can't be cancelled here. "
                    + "Please contact RentNGo.");
        }

        BigDecimal fee = calculateCancellationFee(booking);
        BigDecimal refund = booking.getNetPaid().subtract(fee);
        Runnable undo = undoTo(booking);
        booking.setAmountRefunded(booking.getAmountRefunded().add(refund));
        booking.setStatus(BookingStatus.CANCELLED);
        saveOrUndo(undo); // a refused save must not hand back a refund for a booking still open on disk
        String details = noShow ? "no-show, start date " + booking.getStartDate() + " passed"
                : "cancelled before " + booking.getStartDate();
        if (!Money.isPositive(booking.getAmountPaid())) {
            details += ", nothing paid";
        } else {
            details += Money.isPositive(fee)
                    ? ", less than 24 hours before the start: fee " + peso(fee) + " kept, refund " + peso(refund)
                    : ", full refund " + peso(refund);
        }
        logIfSaved(AuditLog.CANCELLED, bookingId, details);
        // the promo use and loyalty points are NOT given back here: the caller does that with
        // giveBackDiscounts() once the whole cancellation (refund included) has been saved, because a
        // refund that can't be saved puts the booking back to open with its discounts still on it
        return refund;
    }

    /**
     * Gives back the promo code use and loyalty points of a CANCELLED booking. Called once, after the
     * cancellation and its refund were saved (PaymentService.cancelBooking, or updateBookingStatus).
     */
    void giveBackDiscounts(String bookingId) {
        Booking booking = find(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.CANCELLED) return;
        if (booking.getPromoCode() != null && giveBackPromoUse(booking)) {
            audit.record(AuditLog.PROMO_USE_RETURNED, bookingId, "promo code " + booking.getPromoCode()
                    + " use given back (booking cancelled)");
        }
        if (booking.getLoyaltyPointsUsed() > 0 && giveBackPoints(booking.getCustomerId(), booking.getLoyaltyPointsUsed())) {
            audit.record(AuditLog.LOYALTY_RETURNED, bookingId, booking.getCustomerId() + " got back "
                    + booking.getLoyaltyPointsUsed() + " points (booking cancelled)");
        }
    }

    // ===== Loyalty points =====

    /** Every full ₱100 paid on a completed rental earns 1 point. */
    public static final int PESOS_PER_POINT_EARNED = 100;
    /** Points are spent in steps of 100 (= ₱100 off). */
    public static final int POINTS_STEP = 100;

    /** Points earned for an amount paid: 1 per full ₱100. */
    public static int pointsEarnedFor(BigDecimal paid) {
        if (paid == null || paid.signum() <= 0) return 0;
        return paid.divideToIntegralValue(BigDecimal.valueOf(PESOS_PER_POINT_EARNED)).intValue();
    }

    /** The most points that can be spent on a rental costing {@code cost}: a multiple of 100, at most the balance and the cost. */
    public static int maxPointsUsable(int balance, BigDecimal cost) {
        if (balance <= 0 || cost == null || cost.signum() <= 0) return 0;
        long costPesos = cost.setScale(0, java.math.RoundingMode.FLOOR).longValue();
        long most = Math.min(balance, costPesos);
        return (int) (most / POINTS_STEP * POINTS_STEP);
    }

    /** The customer's points; 0 unless an admin or that customer is logged in. */
    public int getLoyaltyPoints(String customerId) {
        if (authService == null || authService.adminOrSelf(customerId, "see loyalty points") != null) return 0;
        return authService.findCustomerRecord(customerId).map(Customer::getLoyaltyPoints).orElse(0);
    }

    private void checkPointsToUse(Customer customer, int points, BigDecimal cost) throws BookingException {
        if (points < 0 || points % POINTS_STEP != 0) {
            throw new BookingException("Loyalty points are used in steps of " + POINTS_STEP);
        }
        if (points > customer.getLoyaltyPoints()) {
            throw new BookingException(customer.getFullName() + " has only " + customer.getLoyaltyPoints() + " loyalty points");
        }
        int most = maxPointsUsable(customer.getLoyaltyPoints(), cost);
        if (points > most) {
            throw new BookingException("At most " + most + " points can be used on this rental (it costs " + peso(cost) + ")");
        }
    }

    /** Puts spent points back (cancelled or unsaved booking). False if they couldn't be saved. */
    private boolean giveBackPoints(String customerId, int points) {
        try {
            authService.changeLoyaltyPoints(customerId, points);
            return true;
        } catch (ValidationException e) {
            System.err.println("Could not give back " + points + " loyalty points to " + customerId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Gives the customer their points once the rental is COMPLETED and nothing is owed: called after a
     * return and after a payment. Only ever once per booking; never at booking time. If the points
     * can't be saved to the account, they are kept on the booking as owed (see getLoyaltyPointsWarning)
     * and given the next time RentNGo starts, so they are never lost silently.
     */
    void awardLoyaltyPointsIfDue(String bookingId) {
        Booking booking = find(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.COMPLETED || booking.getLoyaltyPointsEarned() > 0
                || booking.getLoyaltyPointsPending() > 0 || Money.isPositive(booking.getBalanceDue())) {
            return;
        }
        int points = pointsEarnedFor(booking.getNetPaid());
        if (points > 0) givePoints(booking, points);
    }

    /**
     * Marks the points as earned, then adds them to the account. If the account can't be saved they
     * are marked as owed instead (pending), recorded in the Audit Log, and false is returned.
     */
    private boolean givePoints(Booking booking, int points) {
        String id = booking.getBookingId();
        int pendingBefore = booking.getLoyaltyPointsPending();
        booking.setLoyaltyPointsEarned(points);
        booking.setLoyaltyPointsPending(0);
        try {
            saveOrUndo(() -> {
                booking.setLoyaltyPointsEarned(0);
                booking.setLoyaltyPointsPending(pendingBefore);
            });
        } catch (BookingException e) {
            System.err.println("Could not record loyalty points for " + id + ": " + e.getMessage());
            return false; // bookings.txt can't be saved: nothing changed
        }
        try {
            authService.changeLoyaltyPoints(booking.getCustomerId(), points);
        } catch (ValidationException e) {
            // the account couldn't be saved: keep the points as owed on the booking, never lose them
            booking.setLoyaltyPointsEarned(0);
            booking.setLoyaltyPointsPending(points);
            save();
            audit.record(AuditLog.LOYALTY_PENDING, id, booking.getCustomerId() + " is owed " + points
                    + " points; they couldn't be saved (" + e.getMessage() + ") and will be given when RentNGo next starts");
            return false;
        }
        audit.record(AuditLog.LOYALTY_EARNED, id, booking.getCustomerId() + " earned " + points
                + " points for " + peso(booking.getNetPaid()) + " paid" + (pendingBefore > 0 ? " (owed from before)" : ""));
        return true;
    }

    /**
     * Gives the points still owed from an earlier failed save. DataStore calls this at start-up.
     * Returns how many bookings still wait for their points.
     */
    int givePendingLoyaltyPoints() {
        if (readOnly || authService == null) return (int) bookings.stream().filter(b -> b.getLoyaltyPointsPending() > 0).count();
        int stillWaiting = 0;
        for (Booking b : new ArrayList<>(bookings)) {
            if (b.getLoyaltyPointsPending() > 0 && !givePoints(b, b.getLoyaltyPointsPending())) stillWaiting++;
        }
        return stillWaiting;
    }

    /**
     * A message for the admin when this booking's loyalty points couldn't be saved yet, or null.
     * The return or payment itself was saved; only the points wait.
     */
    public String getLoyaltyPointsWarning(String bookingId) {
        Booking booking = find(bookingId).filter(this::visible).orElse(null);
        if (booking == null || booking.getLoyaltyPointsPending() <= 0) return null;
        return "The rental was saved, but the customer's " + booking.getLoyaltyPointsPending() + " loyalty points couldn't be "
                + "saved to their account yet. They are kept as owed on " + bookingId + " and will be added "
                + "automatically the next time RentNGo starts.";
    }

    // ===== Promo codes =====

    /** Highest discount a promo code can give. */
    public static final int MAX_PROMO_PERCENT = 90;
    /** Most uses a promo code can be created with. */
    public static final int MAX_PROMO_USES = 100_000;

    /** The stored code if it can be used today; otherwise why not. */
    /** For tests: the clock the promo code guessing limit uses. */
    void setPromoGuessClock(java.time.Clock clock) {
        promoGuesses.setClock(clock);
    }

    /**
     * The stored code if it can be used today; otherwise why not. For a customer, wrong codes are
     * counted: after PromoGuessLimiter.MAX_WRONG in a row, every promo code (even a right one) is
     * refused for PromoGuessLimiter.LOCK, so codes can't be found by trying many. Admins aren't
     * limited, since they can see every code anyway.
     */
    private PromoCode usablePromoCode(String code) throws BookingException {
        String customerId = authService != null && authService.isCustomerSession()
                ? authService.getCurrentUser().getUserId() : null;
        if (customerId == null) return findUsablePromoCode(code);
        String locked = promoGuesses.lockMessage(customerId);
        if (locked != null) throw new BookingException(locked);
        try {
            PromoCode promo = findUsablePromoCode(code);
            promoGuesses.recordSuccess(customerId);
            return promo;
        } catch (BookingException wrong) {
            if (promoGuesses.recordWrong(customerId)) {
                audit.record(AuditLog.PROMO_GUESSES_LOCKED, null, customerId + " can't use promo codes for "
                        + PromoGuessLimiter.LOCK.toMinutes() + " minutes after " + PromoGuessLimiter.MAX_WRONG + " wrong codes");
                throw new BookingException(wrong.getMessage() + ". " + promoGuesses.lockMessage(customerId));
            }
            throw wrong;
        }
    }

    private PromoCode findUsablePromoCode(String code) throws BookingException {
        String name = PromoCode.normalize(code);
        PromoCode promo = promoCodes.find(name)
                .orElseThrow(() -> new BookingException("Promo code " + name + " doesn't exist"));
        if (promo.isExpired(LocalDate.now())) {
            throw new BookingException("Promo code " + promo.getCode() + " expired on " + promo.getExpiryDate());
        }
        if (promo.getUsesLeft() <= 0) {
            throw new BookingException("Promo code " + promo.getCode() + " has been used up");
        }
        return promo;
    }

    /**
     * Checks a code before booking, so the screen can show the discount. Any logged-in user can
     * check a code they typed; only admins can list all codes.
     */
    public PromoCode checkPromoCode(String code) throws BookingException {
        String self = authService != null && authService.isCustomerSession() ? authService.getCurrentUser().getUserId() : null;
        return checkPromoCode(code, self);
    }

    /**
     * As above, for a booking for this customer (null: don't check the customer): also refuses a code
     * the customer already used. An admin passes the customer chosen on the Rent Vehicle screen.
     */
    public PromoCode checkPromoCode(String code, String customerId) throws BookingException {
        if (authService == null || !(authService.isAdminSession() || authService.isCustomerSession())) {
            throw new BookingException(AuthService.NOT_LOGGED_IN);
        }
        if (customerId != null) {
            String denied = authService.adminOrSelf(customerId, "use promo codes for other customers");
            if (denied != null) throw new BookingException(denied);
        }
        if (code == null || code.isBlank()) throw new BookingException("Enter a promo code");
        if (promoCodes.isReadOnly()) throw new BookingException(promoCodes.getReadOnlyMessage());
        PromoCode promo = usablePromoCode(code);
        if (customerId != null) checkNotUsedBefore(promo, customerId);
        return promo.copy();
    }

    /**
     * Each customer can use a promo code once. A cancelled booking doesn't count (its use was given
     * back). Checked after the code itself, so it never counts as a wrong guess.
     */
    private void checkNotUsedBefore(PromoCode promo, String customerId) throws BookingException {
        Optional<Booking> earlier = bookings.stream()
                .filter(b -> customerId.equals(b.getCustomerId()) && usedThisCode(b, promo)
                        && b.getStatus() != BookingStatus.CANCELLED)
                .findFirst();
        if (earlier.isPresent()) {
            boolean self = authService.isCustomerSession();
            throw new BookingException((self ? "You" : "This customer") + " already used " + promo.getCode()
                    + " (" + earlier.get().getBookingId() + "). One use per customer.");
        }
    }

    /** Puts one use back on the code (cancelled or unsaved booking). False if the code is gone or couldn't be saved. */
    /**
     * Puts the booking's promo use back on the very code it used (found by its permanent ID). If that
     * code was deleted, nothing is given back, even when a new code now has the same name.
     * Bookings saved before codes had IDs fall back to the name.
     */
    private boolean giveBackPromoUse(Booking booking) {
        Optional<PromoCode> promo = booking.getPromoId() != null
                ? promoCodes.findById(booking.getPromoId())
                : promoCodes.find(booking.getPromoCode());
        return promo.isPresent() && giveBackUse(promo.get()); // deleted since: nothing to give back
    }

    /** Adds one use to this stored code and saves. False if it couldn't be saved. */
    private boolean giveBackUse(PromoCode stored) {
        try {
            promoCodes.setUsesLeft(stored, stored.getUsesLeft() + 1);
            return true;
        } catch (IOException e) {
            System.err.println("Could not give back a use of promo code " + stored.getCode() + ": " + e.getMessage());
            return false;
        }
    }

    /** True if the booking used this very code (by ID; by name for bookings saved before IDs). */
    private static boolean usedThisCode(Booking booking, PromoCode promo) {
        if (booking.getPromoId() != null) return booking.getPromoId().equals(promo.getId());
        return promo.getCode().equals(booking.getPromoCode());
    }

    /** Every promo code, sorted by code; empty unless an admin is logged in. */
    public List<PromoCode> getPromoCodes() {
        if (authService == null || !authService.isAdminSession()) return new ArrayList<>();
        return promoCodes.all();
    }

    /** Creates a promo code (admin only). The code is stored in capitals. */
    public PromoCode addPromoCode(String code, int percentOff, LocalDate expiryDate, int uses) throws BookingException {
        requireAdmin("create promo codes");
        if (promoCodes.isReadOnly()) throw new BookingException(promoCodes.getReadOnlyMessage());
        String name = PromoCode.normalize(code);
        if (name == null || !name.matches("[A-Z0-9]{3,20}")) {
            throw new BookingException("Promo code must be 3 to 20 letters or numbers, e.g. WEEKEND10");
        }
        if (promoCodes.find(name).isPresent()) {
            throw new BookingException("Promo code " + name + " already exists");
        }
        if (percentOff < 1 || percentOff > MAX_PROMO_PERCENT) {
            throw new BookingException("Discount must be 1% to " + MAX_PROMO_PERCENT + "%");
        }
        if (expiryDate == null || expiryDate.isBefore(LocalDate.now())) {
            throw new BookingException("Expiry date can't be in the past");
        }
        if (uses < 1 || uses > MAX_PROMO_USES) {
            throw new BookingException("Number of uses must be 1 to " + String.format("%,d", MAX_PROMO_USES));
        }
        PromoCode promo = new PromoCode(promoCodes.nextId(), name, percentOff, expiryDate, uses);
        try {
            promoCodes.add(promo);
        } catch (IOException e) {
            throw new BookingException(e.getMessage());
        }
        audit.record(AuditLog.PROMO_CODE_ADDED, null, name + ": " + percentOff + "% off, valid until " + expiryDate
                + ", " + uses + (uses == 1 ? " use" : " uses"));
        return promo.copy();
    }

    /** Deletes a promo code (admin only). Bookings that already used it keep their discount. */
    public void deletePromoCode(String code) throws BookingException {
        requireAdmin("delete promo codes");
        if (promoCodes.isReadOnly()) throw new BookingException(promoCodes.getReadOnlyMessage());
        PromoCode promo = promoCodes.find(code)
                .orElseThrow(() -> new BookingException("Promo code " + PromoCode.normalize(code) + " doesn't exist"));
        try {
            promoCodes.remove(promo);
        } catch (IOException e) {
            throw new BookingException(e.getMessage());
        }
        audit.record(AuditLog.PROMO_CODE_DELETED, null, promo.getCode() + " (" + promo.getUsesLeft() + " uses were left)");
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

    /**
     * Records that the customer collected the vehicle (CONFIRMED -> ACTIVE) without a checklist.
     * Kept for older code; the Return Vehicle screen uses {@link #markPickedUp(String, VehicleCondition)}.
     */
    public Booking markPickedUp(String bookingId) throws BookingException {
        return markPickedUp(bookingId, null);
    }

    /**
     * Records that the customer collected the vehicle (CONFIRMED -> ACTIVE), with the damage
     * checklist filled in at the counter (null = none). A rental picked up with a checklist must
     * also be returned with one.
     */
    public Booking markPickedUp(String bookingId, VehicleCondition condition) throws BookingException {
        requireAdmin("record pick-ups");
        requireWritable();
        Booking booking = find(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));
        checkTransition(booking, BookingStatus.ACTIVE);
        if (LocalDate.now().isBefore(booking.getStartDate())) {
            throw new BookingException("This rental starts on " + booking.getStartDate()
                    + ", so the vehicle can't be picked up yet.");
        }
        if (vehicleService.getVehicleById(booking.getVehicleId()).map(v -> !v.isAvailable()).orElse(false)) {
            throw new BookingException("This vehicle is out of service, so it can't be picked up. "
                    + "Put it back in service first, or cancel the booking.");
        }
        Optional<Booking> stillOut = bookings.stream()
                .filter(b -> b != booking && b.getVehicleId().equals(booking.getVehicleId())
                        && b.getStatus() == BookingStatus.ACTIVE)
                .findFirst();
        if (stillOut.isPresent()) {
            throw new BookingException("This vehicle hasn't come back from rental " + stillOut.get().getBookingId()
                    + " yet. Record that return first.");
        }
        Runnable undo = undoTo(booking);
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setPickUpCondition(condition);
        saveOrUndo(undo);
        logIfSaved(AuditLog.PICKED_UP, bookingId, "vehicle " + booking.getVehicleId() + " picked up by customer "
                + booking.getCustomerId() + " (rental " + booking.getStartDate() + " to " + booking.getEndDate() + ")"
                + (condition == null ? "" : ", checklist: " + describe(condition)));
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
        return returnBooking(bookingId, returnDate, additionalCharge, note, null);
    }

    /**
     * Completes the rental with the damage checklist filled in when the vehicle came back. The
     * checklist is required when one was filled in at pick-up, so new damage is always compared.
     * New damage (see {@link VehicleCondition#newProblems}) is written to the audit log as
     * DAMAGE_FOUND with the suggested and the actual additional charge.
     */
    public Booking returnBooking(String bookingId, LocalDate returnDate, BigDecimal additionalCharge, String note,
                                 VehicleCondition condition) throws BookingException {
        requireAdmin("record returns");
        requireWritable();
        Booking booking = find(bookingId)
                .orElseThrow(() -> new BookingException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BookingException("Vehicle for this booking was already returned");
        }
        // only a vehicle that was picked up can come back: a booking the customer never collected
        // (a no-show) is cancelled instead, so it is never charged a rental or late fees for a car
        // that stayed in the lot, and never earns loyalty points or a review
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingException("This vehicle hasn't been picked up yet. Mark it as picked up first, "
                    + "or cancel the booking if the customer didn't come.");
        }
        checkTransition(booking, BookingStatus.COMPLETED);
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
        if (condition == null && booking.getPickUpCondition() != null) {
            throw new BookingException(RETURN_CHECKLIST_REQUIRED);
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
        booking.setReturnCondition(condition);
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
        if (condition != null) details.append(", checklist: ").append(describe(condition));
        logIfSaved(AuditLog.RETURNED, bookingId, details.toString());
        List<String> damage = VehicleCondition.newProblems(booking.getPickUpCondition(), condition);
        if (!damage.isEmpty()) {
            logIfSaved(AuditLog.DAMAGE_FOUND, bookingId, String.join(", ", damage) + " on vehicle "
                    + booking.getVehicleId() + " (customer " + booking.getCustomerId() + "): suggested charge "
                    + peso(VehicleCondition.suggestedCharge(booking.getPickUpCondition(), condition))
                    + ", additional charge " + peso(Money.round(additionalCharge))
                    + (condition.getNotes() != null ? ", note: " + condition.getNotes() : ""));
        }
        awardLoyaltyPointsIfDue(bookingId); // prepaid rentals earn their points now; others when paid
        return find(bookingId).orElse(booking).copy();
    }

    /** Shown when a return is tried without the checklist although one was filled in at pick-up. */
    public static final String RETURN_CHECKLIST_REQUIRED = "Fill in the return checklist (scratches, dents, fuel, "
            + "cleanliness) first, so it can be compared with the one from pick-up.";

    private static String describe(VehicleCondition c) {
        return c.summary() + (c.getNotes() != null ? " (" + c.getNotes() + ")" : "");
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
                cancelBooking(bookingId); // records the refund payment and gives back promo use and points
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
