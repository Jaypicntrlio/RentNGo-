package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Booking {
    /** Which booking, for whom and for which vehicle never change after creation: no setters. */
    private final String bookingId;
    private final String customerId;
    private final String vehicleId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCost;
    private BookingStatus status;
    private LocalDate actualReturnDate;
    private BigDecimal additionalCharge = Money.ZERO;
    private BigDecimal lateFee = Money.ZERO;
    private BigDecimal amountPaid = Money.ZERO;
    private BigDecimal amountRefunded = Money.ZERO;
    /** User ID of the admin who processed the return, and when; null until returned. */
    private String processedBy;
    private LocalDateTime processedAt;
    /** Why the return was recorded with a date other than the day it was processed (or null). */
    private String returnNote;

    public Booking(String bookingId, String customerId, String vehicleId, LocalDate startDate, LocalDate endDate, BigDecimal totalCost) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalCost = Money.round(totalCost);
        this.status = BookingStatus.PENDING;
    }

    /** An independent copy: changing it never changes the booking held by BookingService. */
    public Booking copy() {
        Booking c = new Booking(bookingId, customerId, vehicleId, startDate, endDate, totalCost);
        c.status = status;
        c.actualReturnDate = actualReturnDate;
        c.additionalCharge = additionalCharge;
        c.lateFee = lateFee;
        c.amountPaid = amountPaid;
        c.amountRefunded = amountRefunded;
        c.processedBy = processedBy;
        c.processedAt = processedAt;
        c.returnNote = returnNote;
        return c;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = Money.round(totalCost);
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public long getNumberOfDays() {
        return calculateDays(startDate, endDate);
    }

    /**
     * The one rule for counting rental days: the number of days between the dates
     * (Mon -> Tue is 1 day), never less than 1. Callers must also require the end date
     * to be after the start date.
     */
    public static long calculateDays(LocalDate start, LocalDate end) {
        return Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end));
    }

    public LocalDate getActualReturnDate() {
        return actualReturnDate;
    }

    public void setActualReturnDate(LocalDate actualReturnDate) {
        this.actualReturnDate = actualReturnDate;
    }

    public BigDecimal getAdditionalCharge() {
        return additionalCharge;
    }

    public void setAdditionalCharge(BigDecimal additionalCharge) {
        this.additionalCharge = Money.round(additionalCharge);
    }

    public BigDecimal getLateFee() {
        return lateFee;
    }

    public void setLateFee(BigDecimal lateFee) {
        this.lateFee = Money.round(lateFee);
    }

    /** Rental cost plus any late fee and extra charges added at return. */
    public BigDecimal getFinalAmount() {
        return totalCost.add(lateFee).add(additionalCharge);
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = Money.round(amountPaid);
    }

    public void addPayment(BigDecimal amount) {
        this.amountPaid = Money.round(amountPaid.add(amount));
    }

    public BigDecimal getAmountRefunded() {
        return amountRefunded;
    }

    public void setAmountRefunded(BigDecimal amountRefunded) {
        this.amountRefunded = Money.round(amountRefunded);
    }

    /** Money actually kept for this booking: payments minus refunds. */
    public BigDecimal getNetPaid() {
        return amountPaid.subtract(amountRefunded);
    }

    /** What the customer still owes; zero for cancelled bookings. */
    public BigDecimal getBalanceDue() {
        if (status == BookingStatus.CANCELLED) return Money.ZERO;
        BigDecimal due = getFinalAmount().subtract(amountPaid);
        return due.signum() > 0 ? due : Money.ZERO;
    }

    /** Fully paid, including any charges added when the vehicle was returned. */
    public boolean isPaid() {
        return status != BookingStatus.CANCELLED && Money.isPositive(amountPaid) && getBalanceDue().signum() == 0;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getReturnNote() {
        return returnNote;
    }

    public void setReturnNote(String returnNote) {
        this.returnNote = returnNote;
    }

    @Override
    public String toString() {
        return "Booking{" +
                "bookingId='" + bookingId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", totalCost=" + totalCost +
                ", status=" + status +
                '}';
    }
}