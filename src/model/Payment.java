package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Payment {
    private String paymentId;
    private String bookingId;
    private String customerId;
    private BigDecimal amount;
    private String method;
    private BigDecimal amountPaid;
    private LocalDateTime paymentDate;

    public Payment(String paymentId, String bookingId, String customerId, BigDecimal amount, String method, BigDecimal amountPaid) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.amount = Money.round(amount);
        this.method = method;
        this.amountPaid = Money.round(amountPaid);
        this.paymentDate = LocalDateTime.now();
    }

    /** Restores a saved payment with its original date. */
    public Payment(String paymentId, String bookingId, String customerId, BigDecimal amount, String method,
                   BigDecimal amountPaid, LocalDateTime paymentDate) {
        this(paymentId, bookingId, customerId, amount, method, amountPaid);
        this.paymentDate = paymentDate;
    }

    /** Refunds are stored as payments with a negative amount. */
    public boolean isRefund() {
        return amount.signum() < 0;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMethod() {
        return method;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getChange() {
        return amountPaid.subtract(amount);
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId='" + paymentId + '\'' +
                ", bookingId='" + bookingId + '\'' +
                ", amount=" + amount +
                ", method='" + method + '\'' +
                '}';
    }
}
