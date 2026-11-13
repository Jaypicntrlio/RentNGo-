package service;

import model.Booking;
import model.BookingStatus;
import model.Money;
import model.Payment;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PaymentService {
    private static final String HEADER = "RentNGo payments - id\tbookingId\tcustomerId\tamount\tmethod\tamountPaid\tdate";
    public static final String REFUND_METHOD = "Refund";
    /** Payment methods offered at the counter. */
    public static final String[] METHODS = {"Cash", "Card", "GCash"};
    public static final String CASH = "Cash";
    /** Cash is the only method that can be more than the amount due (to give change), by at most this much. */
    public static final BigDecimal MAX_CASH_OVER = Money.of(100_000);

    private List<Payment> payments;
    private BookingService bookingService;
    private int paymentCounter;
    private final File dataFile;
    private String loadError;
    private boolean readOnly;
    /** Why changes are refused while read-only (unreadable file, or changed by another program). */
    private String readOnlyMessage;
    /** The file as it was when last loaded or saved; a save is refused if it changed since. */
    private TsvFile.Stamp stamp;
    private static final String ID_PREFIX = "PAY";

    /** Payments saved in data/payments.txt. */
    public PaymentService(BookingService bookingService) {
        this(bookingService, new File(ProjectPaths.dir("data"), "payments.txt"));
    }

    /** Pass null to keep payments in memory only (tests). */
    public PaymentService(BookingService bookingService, File dataFile) {
        this.bookingService = bookingService;
        this.dataFile = dataFile;
        this.stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
        this.payments = new ArrayList<>();
        this.paymentCounter = 1;
        if (dataFile != null && dataFile.isFile()) {
            try {
                load();
            } catch (IOException e) {
                // keep the file as it is and never save over it (or reuse its payment numbers)
                readOnly = true;
                loadError = TsvFile.describeUnreadable(dataFile, e);
                readOnlyMessage = loadError;
            }
        }
    }

    /**
     * Loads every payment row that can be read; bad rows are skipped and reported in loadError.
     * The next payment number comes after the saved "#seq" line and every ID seen in the file
     * (skipped rows included), so a new payment is never linked to an old number.
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
                TsvFile.requireFields(f, 7);
                if (TsvFile.idNumber(f[0], ID_PREFIX) == 0) {
                    throw new IllegalArgumentException("bad payment ID '" + f[0] + "'");
                }
                payments.add(new Payment(f[0], f[1], f[2], Money.parse(f[3]), f[4],
                        Money.parse(f[5]), LocalDateTime.parse(f[6])));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        paymentCounter = lastUsed + 1;
        loadError = warnings.summary();
    }

    /** Why the saved file couldn't be (fully) loaded, or null if it loaded fine (shown at startup). */
    public String getLoadError() {
        return loadError;
    }

    /** True when payments.txt couldn't be read; payments can't be taken or saved then. */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** Why payments can't be taken right now, or null. Payments also update the booking, so both files must be writable. */
    private String blockedMessage() {
        if (readOnly) return readOnlyMessage;
        return bookingService.getReadOnlyMessage();
    }

    private void save() {
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
            System.err.println("Not saving payments: " + readOnlyMessage);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Payment p : payments) {
            rows.add(new String[]{p.getPaymentId(), p.getBookingId(), p.getCustomerId(), p.getAmount().toPlainString(),
                    p.getMethod(), p.getAmountPaid().toPlainString(), p.getPaymentDate().toString()});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, paymentCounter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another copy of RentNGo (or another program) wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            System.err.println("Could not save payments: " + e.getMessage());
        }
    }

    /**
     * Pays the booking's current balance. The amount must match what is owed right now, so a
     * stale window (opened before a return charge was added, or after another payment) is refused.
     */
    public Payment processPayment(String bookingId, String customerId, BigDecimal amount, String method, BigDecimal amountPaid) throws ValidationException {
        requireAdmin("record payments");
        String blocked = blockedMessage();
        if (blocked != null) {
            throw new ValidationException(blocked);
        }
        validateMethod(method);
        Booking booking = bookingService.find(bookingId)
                .orElseThrow(() -> new ValidationException("Booking not found: " + bookingId));
        if (customerId != null && !customerId.isBlank() && !customerId.trim().equals(booking.getCustomerId())) {
            throw new ValidationException("Booking " + bookingId + " belongs to another customer, so this payment can't be recorded for "
                    + customerId.trim() + ".");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ValidationException("This booking was cancelled and can't be paid");
        }
        BigDecimal due = booking.getBalanceDue();
        if (!Money.isPositive(due)) {
            throw new ValidationException("This booking is already fully paid");
        }
        if (amount == null || Money.round(amount).compareTo(due) != 0) {
            throw new ValidationException(String.format(
                    "The amount due for this booking is now ₱%,.2f. Please select the rental again.", due));
        }
        validateTender(method, due, amountPaid);
        amountPaid = Money.round(amountPaid);

        Booking before = booking.copy();
        String paymentId = ID_PREFIX + String.format("%04d", paymentCounter++);
        Payment payment = new Payment(paymentId, bookingId, booking.getCustomerId(), amount, method, amountPaid);
        Runnable undoPayment = () -> {
            payments.remove(payment);
            paymentCounter--;
        };
        payments.add(payment);
        booking.addPayment(amount);

        // bookings.txt first, then payments.txt; if either save is refused, both go back as they were,
        // so the booking never shows money that has no payment row (or the other way round)
        try {
            bookingService.saveOrUndo(() -> {
                booking.setAmountPaid(before.getAmountPaid());
                undoPayment.run();
            });
        } catch (BookingException e) {
            throw new ValidationException(e.getMessage());
        }
        try {
            write();
        } catch (TsvFile.DataChangedException e) {
            undoPayment.run();
            bookingService.restore(before);
            throw new ValidationException(e.getMessage());
        }
        bookingService.getAuditLog().record(AuditLog.PAYMENT, bookingId, paymentId + " " + BookingService.peso(amount)
                + " by " + method + ", received " + BookingService.peso(amountPaid)
                + ", change " + BookingService.peso(payment.getChange()));
        return payment;
    }

    /**
     * What was handed over must cover the amount due. Card and GCash are charged the exact amount;
     * only Cash may be more (change is given), and at most MAX_CASH_OVER more.
     */
    static void validateTender(String method, BigDecimal due, BigDecimal received) throws ValidationException {
        if (received == null) {
            throw new ValidationException("Please enter the amount received");
        }
        BigDecimal paid = Money.round(received);
        if (paid.compareTo(due) < 0) {
            throw new ValidationException(String.format("Amount received is less than the amount due (\u20B1%,.2f)", due));
        }
        if (!CASH.equals(method) && paid.compareTo(due) != 0) {
            throw new ValidationException(String.format(
                    "%s payments must be for the exact amount due (\u20B1%,.2f); only cash can be more, for change.", method, due));
        }
        if (paid.subtract(due).compareTo(MAX_CASH_OVER) > 0) {
            throw new ValidationException(String.format(
                    "Cash received can't be more than \u20B1%,.2f over the amount due. Please check the amount.", MAX_CASH_OVER));
        }
    }

    private static void validateMethod(String method) throws ValidationException {
        if (method == null || method.trim().isEmpty()) {
            throw new ValidationException("Payment method is required");
        }
        if (!java.util.Arrays.asList(METHODS).contains(method)) {
            throw new ValidationException("Payment method must be Cash, Card or GCash");
        }
    }

    /**
     * Returns the vehicle and takes the payment in one step: the booking becomes COMPLETED and
     * whatever is owed (rental + 1.5x late fee + extra charges - earlier payments) is recorded.
     * The payment is checked first, so a booking is never left returned but unpaid.
     * Returns the payment, or null when nothing was owed (already paid in advance).
     */
    public Payment completeReturn(String bookingId, java.time.LocalDate returnDate, BigDecimal additionalCharge,
                                  String method, BigDecimal amountReceived) throws ValidationException, BookingException {
        return completeReturn(bookingId, returnDate, additionalCharge, null, method, amountReceived);
    }

    /** As above, with the reason required when the return date isn't today (see BookingService.returnBooking). */
    public Payment completeReturn(String bookingId, java.time.LocalDate returnDate, BigDecimal additionalCharge, String returnNote,
                                  String method, BigDecimal amountReceived) throws ValidationException, BookingException {
        requireAdmin("record returns");
        String blocked = blockedMessage();
        if (blocked != null) {
            throw new ValidationException(blocked);
        }
        Booking booking = bookingService.find(bookingId)
                .orElseThrow(() -> new ValidationException("Booking not found: " + bookingId));
        if (returnDate == null) {
            throw new ValidationException("Return date is required");
        }
        BigDecimal due = bookingService.previewAmountDue(booking, returnDate, additionalCharge);
        if (Money.isPositive(due)) {
            // checked before the vehicle is returned, so a bad payment never leaves it returned but unpaid
            validateMethod(method);
            validateTender(method, due, amountReceived);
        }

        Booking before = booking.copy();
        bookingService.returnBooking(bookingId, returnDate, additionalCharge, returnNote);
        Booking returned = bookingService.find(bookingId).orElseThrow();
        if (!Money.isPositive(returned.getBalanceDue())) {
            return null;
        }
        try {
            return processPayment(bookingId, returned.getCustomerId(), returned.getBalanceDue(), method, amountReceived);
        } catch (ValidationException e) {
            // the payment couldn't be saved: take the return back, so it is never returned but unpaid
            bookingService.restore(before);
            bookingService.getAuditLog().record(AuditLog.UNDONE, bookingId,
                    "return taken back because the payment could not be saved: " + e.getMessage());
            throw e;
        }
    }

    /** Cancels the booking and records a refund payment for anything that had been paid. */
    public BigDecimal cancelBooking(String bookingId) throws BookingException {
        String blocked = blockedMessage();
        if (blocked != null) {
            throw new BookingException(blocked);
        }
        Booking before = bookingService.find(bookingId).map(Booking::copy).orElse(null);
        BigDecimal refund = bookingService.cancelBooking(bookingId); // throws (and changes nothing) if not saved
        if (Money.isPositive(refund)) {
            Booking booking = bookingService.find(bookingId).orElseThrow();
            String paymentId = ID_PREFIX + String.format("%04d", paymentCounter++);
            Payment refundRow = new Payment(paymentId, bookingId, booking.getCustomerId(), refund.negate(), REFUND_METHOD, refund.negate());
            payments.add(refundRow);
            try {
                write();
            } catch (TsvFile.DataChangedException e) {
                // no refund row could be saved: the booking goes back to open, so nobody is refunded twice
                payments.remove(refundRow);
                paymentCounter--;
                bookingService.restore(before);
                bookingService.getAuditLog().record(AuditLog.UNDONE, bookingId,
                        "cancellation taken back because the refund could not be saved: " + e.getMessage());
                throw new BookingException(e.getMessage());
            }
            bookingService.getAuditLog().record(AuditLog.REFUND, bookingId, paymentId + " refunded "
                    + BookingService.peso(refund) + " to customer " + booking.getCustomerId());
        }
        return refund;
    }

    // Payments can't be changed after they are made, so reads return them as they are, filtered to
    // what the logged-in user may see: all for an admin, their own for a customer, none otherwise.

    private boolean visible(Payment p) {
        AuthService auth = bookingService.auth();
        return auth != null && auth.canSee(p.getCustomerId());
    }

    private void requireAdmin(String action) throws ValidationException {
        AuthService auth = bookingService.auth();
        String denied = auth == null ? AuthService.NOT_LOGGED_IN : auth.adminOnly(action);
        if (denied != null) throw new ValidationException(denied);
    }

    /** Every payment the logged-in user may see. */
    public List<Payment> getAllPayments() {
        return payments.stream().filter(this::visible).collect(Collectors.toList());
    }

    /** Every payment for the booking, refunds included, oldest first (if the logged-in user may see them). */
    public List<Payment> getPaymentsByBookingId(String bookingId) {
        return payments.stream()
                .filter(p -> p.getBookingId().equals(bookingId) && visible(p))
                .collect(Collectors.toList());
    }

    public List<Payment> getPaymentsByCustomer(String customerId) {
        return payments.stream()
                .filter(p -> p.getCustomerId().equals(customerId) && visible(p))
                .collect(Collectors.toList());
    }
}
