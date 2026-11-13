package service;

import model.Booking;
import model.BookingStatus;
import model.Money;
import model.Payment;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for payment rules: exact card/GCash amounts, cash limits, customer match, receipts and cancellation fees. */
class PaymentRulesTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private DataStore store;
    private BookingService bookings;
    private PaymentService payments;

    @BeforeEach
    void setUp() {
        store = Stores.asAdmin(dataDir);
        bookings = store.getBookingService();
        payments = store.getPaymentService();
    }

    /** The booking as stored now: bookings handed out by the service are copies, so read it again. */
    private Booking fresh(Booking b) {
        return bookings.getBookingById(b.getBookingId()).orElseThrow();
    }

    private static BigDecimal peso(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, peso(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    /** VEH001 (2,500/day) for 2 days starting in {@code startOffset} days: 5,000 due. */
    private Booking book(int startOffset) throws Exception {
        return bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(startOffset), TODAY.plusDays(startOffset + 2));
    }

    private Payment pay(Booking b, String method, String received) throws Exception {
        return payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), method, peso(received));
    }

    // ===== 1. Card / GCash exact, cash at most 100,000 over =====

    @Nested
    class Tender {

        @ParameterizedTest
        @ValueSource(strings = {"Card", "GCash"})
        void cardAndGcashMustBeExact(String method) throws Exception {
            Booking b = book(5);
            ValidationException e = assertThrows(ValidationException.class, () -> pay(b, method, "5000.01"));
            assertTrue(e.getMessage().contains("exact amount due"), e.getMessage());
            assertTrue(payments.getAllPayments().isEmpty());

            Payment p = pay(b, method, "5000");
            assertMoney("0", p.getChange());
            assertTrue(fresh(b).isPaid());
        }

        @Test
        void cashMayBeUpTo100000Over() throws Exception {
            Booking b = book(5);
            ValidationException e = assertThrows(ValidationException.class, () -> pay(b, "Cash", "105000.01"));
            assertTrue(e.getMessage().contains("100,000.00 over"), e.getMessage());

            Payment p = pay(b, "Cash", "105000");
            assertMoney("100000", p.getChange());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Cash", "Card", "GCash"})
        void lessThanDueIsRefusedForEveryMethod(String method) throws Exception {
            Booking b = book(5);
            ValidationException e = assertThrows(ValidationException.class, () -> pay(b, method, "4999.99"));
            assertTrue(e.getMessage().contains("less than the amount due"), e.getMessage());
            assertThrows(ValidationException.class, () ->
                    payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), method, null));
        }

        @Test
        void completeReturnEnforcesTheSameRulesBeforeReturning() throws Exception {
            Booking b = book(0);
            bookings.markPickedUp(b.getBookingId());

            ValidationException gcash = assertThrows(ValidationException.class, () ->
                    payments.completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, "GCash", peso("5500")));
            assertTrue(gcash.getMessage().contains("exact amount due"), gcash.getMessage());
            ValidationException cash = assertThrows(ValidationException.class, () ->
                    payments.completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, "Cash", peso("200000")));
            assertTrue(cash.getMessage().contains("100,000.00 over"), cash.getMessage());
            assertEquals(BookingStatus.ACTIVE, fresh(b).getStatus(), "a refused payment doesn't return the vehicle");
            assertTrue(payments.getAllPayments().isEmpty());

            Payment p = payments.completeReturn(b.getBookingId(), TODAY, BigDecimal.ZERO, "Card", peso("5000"));
            assertNotNull(p);
            assertEquals(BookingStatus.COMPLETED, fresh(b).getStatus());
        }
    }

    // ===== 3. Payment must belong to the booking's customer =====

    @Nested
    class CustomerMatch {

        @Test
        void anotherCustomersIdIsRefused() throws Exception {
            Booking b = book(5);
            ValidationException e = assertThrows(ValidationException.class, () ->
                    payments.processPayment(b.getBookingId(), "CUS002", b.getBalanceDue(), "Cash", b.getBalanceDue()));
            assertTrue(e.getMessage().contains("belongs to another customer"), e.getMessage());
            assertTrue(payments.getAllPayments().isEmpty());
            assertFalse(fresh(b).isPaid());
        }

        @Test
        void blankOrMatchingCustomerIdIsAccepted() throws Exception {
            for (String id : new String[]{null, "", "   ", " CUS001 "}) {
                Booking b = book(5 + payments.getAllPayments().size() * 3);
                Payment p = payments.processPayment(b.getBookingId(), id, b.getBalanceDue(), "Cash", b.getBalanceDue());
                assertEquals("CUS001", p.getCustomerId(), "recorded under the booking's customer");
            }
        }
    }

    // ===== 2. Every payment for a booking, and the receipt lists them all =====

    @Nested
    class PaymentsForBooking {

        @Test
        void returnsPaymentsAndRefundsForThatBookingOnly() throws Exception {
            Booking cancelled = book(5);
            Payment paid = pay(cancelled, "GCash", "5000");
            Booking other = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(5), TODAY.plusDays(6));
            payments.processPayment(other.getBookingId(), "CUS002", other.getBalanceDue(), "Cash", other.getBalanceDue());
            payments.cancelBooking(cancelled.getBookingId());

            List<Payment> list = payments.getPaymentsByBookingId(cancelled.getBookingId());
            assertEquals(2, list.size());
            assertEquals(paid.getPaymentId(), list.get(0).getPaymentId(), "oldest first");
            assertTrue(list.get(1).isRefund());
            assertMoney("-5000", list.get(1).getAmount());
            assertTrue(payments.getPaymentsByBookingId("BKG9999").isEmpty());
        }

        @Test
        void receiptListsEveryPaymentLine() throws Exception {
            Booking b = book(1); // starts tomorrow: 20% fee on cancelling
            Payment paid = pay(b, "Cash", "5100");
            payments.cancelBooking(b.getBookingId());
            List<Payment> list = payments.getPaymentsByBookingId(b.getBookingId());

            String text = gui.ReceiptDialog.buildReceipt(fresh(b), list, null, "John Doe", null);
            assertTrue(text.contains(" " + paid.getPaymentId() + " "), text);
            assertTrue(text.contains(list.get(1).getPaymentId() + " " + TODAY + " Refund"), text);
            assertTrue(text.contains("-₱4,000.00"), text);
            assertTrue(text.contains("change"), "cash change is shown under its line:\n" + text);
            assertTrue(text.contains("Refunded"), text);
            assertTrue(text.contains("Cancellation fee kept") && text.contains("₱1,000.00"), text);
        }
    }

    // ===== 4. Cancellation fee within 24 hours of the start date =====

    @Nested
    class CancellationPolicy {

        @Test
        void cancellingTheDayBeforeKeepsTwentyPercent() throws Exception {
            Booking b = book(1);
            pay(b, "Card", "5000");
            assertTrue(bookings.isLateCancellation(fresh(b)));
            assertMoney("1000", bookings.calculateCancellationFee(fresh(b)));
            assertMoney("4000", bookings.previewCancellationRefund(fresh(b)));

            BigDecimal refund = payments.cancelBooking(b.getBookingId());

            assertMoney("4000", refund);
            assertMoney("1000", fresh(b).getNetPaid()); // the fee stays as revenue
            Payment refundLine = payments.getPaymentsByBookingId(b.getBookingId()).get(1);
            assertMoney("-4000", refundLine.getAmount());
            assertMoney("1000", new ReportService(bookings, store.getVehicleService(), payments).getTotalRevenue());
            assertTrue(bookings.getAuditLog().entriesNewestFirst().stream().anyMatch(e ->
                    e.getAction().equals(AuditLog.CANCELLED) && e.getDetails().contains("fee ₱1,000.00 kept")));
        }

        @Test
        void cancellingOnTheStartDayAlsoKeepsTheFee() throws Exception {
            Booking b = book(0);
            pay(b, "Cash", "5000");
            assertMoney("4000", payments.cancelBooking(b.getBookingId()));
        }

        @Test
        void cancellingEarlierRefundsEverything() throws Exception {
            Booking b = book(3);
            pay(b, "GCash", "5000");
            assertFalse(bookings.isLateCancellation(fresh(b)));
            assertMoney("0", bookings.calculateCancellationFee(fresh(b)));

            assertMoney("5000", payments.cancelBooking(b.getBookingId()));
            assertMoney("0", fresh(b).getNetPaid());
        }

        @Test
        void unpaidLateCancellationHasNothingToKeepOrRefund() throws Exception {
            Booking b = book(1);
            assertMoney("0", bookings.calculateCancellationFee(fresh(b)));
            assertMoney("0", payments.cancelBooking(b.getBookingId()));
            assertTrue(payments.getPaymentsByBookingId(b.getBookingId()).isEmpty(), "no refund line for nothing");
        }

        @Test
        void feeRateIsTheConstant() {
            assertEquals("0.20", BookingService.CANCELLATION_FEE_RATE);
            assertMoney("200.00", Money.times(peso("1000"), BookingService.CANCELLATION_FEE_RATE));
        }

        @Test
        void refundAndFeeSurviveARestart() throws Exception {
            Booking b = book(1);
            pay(b, "Cash", "5000");
            payments.cancelBooking(b.getBookingId());
            DataStore reopened = Stores.asAdmin(dataDir);
            Booking again = reopened.getBookingService().getBookingById(b.getBookingId()).orElseThrow();
            assertMoney("1000", again.getNetPaid());
            assertEquals(2, reopened.getPaymentService().getPaymentsByBookingId(b.getBookingId()).size());
            assertTrue(Files.readString(dataDir.resolve("payments.txt")).contains("Refund"));
        }
    }
}
