package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A discount code, e.g. WEEKEND10 for 10% off the rental. It can be used until its expiry date
 * (that day included) while it has uses left. Codes are compared without regard to case and are
 * always stored in capitals.
 */
public class PromoCode {
    /** Never reused (PRM0001, ...): a code deleted and created again with the same name is another code. */
    private final String id;
    private final String code;
    private final int percentOff;
    private final LocalDate expiryDate;
    private int usesLeft;

    public PromoCode(String code, int percentOff, LocalDate expiryDate, int usesLeft) {
        this(null, code, percentOff, expiryDate, usesLeft);
    }

    public PromoCode(String id, String code, int percentOff, LocalDate expiryDate, int usesLeft) {
        this.id = id;
        this.code = normalize(code);
        this.percentOff = percentOff;
        this.expiryDate = expiryDate;
        this.usesLeft = usesLeft;
    }

    /** "weekend10 " -> "WEEKEND10"; null stays null. */
    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** An independent copy: changing it never changes the code held by BookingService. */
    public PromoCode copy() {
        return new PromoCode(id, code, percentOff, expiryDate, usesLeft);
    }

    /** The code's permanent ID, e.g. "PRM0003" (bookings remember it, not just the name). */
    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getPercentOff() {
        return percentOff;
    }

    /** The last day the code can be used. */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public int getUsesLeft() {
        return usesLeft;
    }

    public void setUsesLeft(int usesLeft) {
        this.usesLeft = usesLeft;
    }

    public boolean isExpired(LocalDate today) {
        return today.isAfter(expiryDate);
    }

    /** The amount taken off a rental cost, rounded to centavos. */
    public BigDecimal discountOn(BigDecimal rentalCost) {
        return Money.round(rentalCost.multiply(BigDecimal.valueOf(percentOff)).divide(BigDecimal.valueOf(100)));
    }

    @Override
    public String toString() {
        return "PromoCode{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", percentOff=" + percentOff +
                ", expiryDate=" + expiryDate +
                ", usesLeft=" + usesLeft +
                '}';
    }
}
