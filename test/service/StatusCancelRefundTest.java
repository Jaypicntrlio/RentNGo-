package service;

import model.Booking;
import model.BookingStatus;
import model.Payment;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fix #5 from the loophole review: every way of cancelling records the refund as a payment, so the
 * booking, the Payments list and Reports always agree. Before, updateBookingStatus(CANCELLED) and
 * BookingService.cancelBooking marked the money as refunded on the booking but recorded no refund.
 */
class StatusCancelRefundTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;
    private ReportService reports;

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("admin", "admin123");
    }

    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        payments = new PaymentService(bookings, dataDir.resolve("payments.txt").toFile());
        reports = new ReportService(bookings, vehicles, payments);
    }

    /** Camry in 5 days for 2 days (₱5,000), paid in full. */
    private Booking paidBooking(int startOffset) throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 2));
        payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        return b;
    }

    private List<Payment> refunds() {
        return payments.getAllPayments().stream().filter(Payment::isRefund).collect(Collectors.toList());
    }

    @Test
    void statusChangeToCancelledRecordsTheRefund() throws Exception {
        Booking b = paidBooking(5);
        bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED);

        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, after.getStatus());
        assertEquals(1, refunds().size(), "the refund is a payment row now");
        assertEquals(0, new BigDecimal("-5000.00").compareTo(refunds().get(0).getAmount()));
        assertEquals(0, reports.getTotalRevenue().signum(), "Reports: ₱5,000 paid minus ₱5,000 refunded");
    }

    @Test
    void bookingServiceCancelRecordsTheRefundToo() throws Exception {
        Booking b = paidBooking(5);
        BigDecimal refund = bookings.cancelBooking(b.getBookingId());
        assertEquals(0, new BigDecimal("5000.00").compareTo(refund));
        assertEquals(1, refunds().size());
        assertEquals(0, reports.getTotalRevenue().signum());
    }

    @Test
    void customerCancellingThroughBookingServiceRecordsTheRefund() throws Exception {
        Booking b = paidBooking(5);
        auth.logout();
        auth.login("john", "john123");
        bookings.cancelBooking(b.getBookingId());
        assertEquals(1, refunds().size());
    }

    @Test
    void lateCancellationRecordsTheRefundAndKeepsTheFeeAsRevenue() throws Exception {
        Booking b = paidBooking(0); // starts today: less than 24 hours' notice
        bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED);
        assertEquals(0, new BigDecimal("-4000.00").compareTo(refunds().get(0).getAmount()), "80% refunded");
        assertEquals(0, new BigDecimal("1000.00").compareTo(reports.getTotalRevenue()), "the 20% fee stays as revenue");
    }

    @Test
    void unpaidBookingCancelsWithoutARefundRow() throws Exception {
        Booking b = bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(5), TODAY.plusDays(6));
        bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED);
        assertEquals(BookingStatus.CANCELLED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
        assertTrue(refunds().isEmpty());
    }

    @Test
    void statusCancelWhoseRefundCantBeSavedIsUndone() throws Exception {
        Booking b = paidBooking(5);
        Path p = dataDir.resolve("payments.txt");
        Files.writeString(p, Files.readString(p) + "# changed elsewhere\n");
        assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED));
        Booking after = bookings.getBookingById(b.getBookingId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, after.getStatus(), "not cancelled without its refund");
        assertEquals(0, after.getAmountRefunded().signum());
    }

    @Test
    void cancellingTwiceIsRefused() throws Exception {
        Booking b = paidBooking(5);
        bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED);
        assertThrows(BookingException.class, () -> bookings.cancelBooking(b.getBookingId()));
        assertThrows(BookingException.class, () -> payments.cancelBooking(b.getBookingId()));
        assertEquals(1, refunds().size(), "refunded once");
    }
}
