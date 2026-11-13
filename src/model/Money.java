package model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: every amount is a BigDecimal rounded to 2 decimal places (half up). */
public final class Money {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {}

    /** Rounds to 2 decimals; null counts as zero. */
    public static BigDecimal round(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Parses text such as "2500", "2,500.50" or "2500.0" (as typed or as saved). */
    public static BigDecimal parse(String text) {
        return round(new BigDecimal(text.trim().replace(",", "")));
    }

    /** For whole-number literals in code, e.g. Money.of(2500). */
    public static BigDecimal of(long amount) {
        return round(BigDecimal.valueOf(amount));
    }

    /** amount x factor, rounded (used for percentage discounts and surcharges). */
    public static BigDecimal times(BigDecimal amount, String factor) {
        return round(amount.multiply(new BigDecimal(factor)));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
