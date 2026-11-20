package model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The damage checklist an admin fills in when a customer picks up a vehicle and again when it
 * comes back: scratches, dents, fuel level, cleanliness and an optional note. Comparing the two
 * shows what is new, and {@link #suggestedCharge} prices it so the admin can fill in the extra
 * charge. Immutable: a new checklist is a new object.
 */
public final class VehicleCondition {

    /** How full the tank is, in quarters. */
    public enum Fuel {
        EMPTY("Empty"), QUARTER("1/4"), HALF("1/2"), THREE_QUARTERS("3/4"), FULL("Full");

        private final String label;

        Fuel(String label) {
            this.label = label;
        }

        /** "Full", "3/4", ... as shown on screen and on the receipt. */
        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The longest note kept with a checklist. */
    public static final int MAX_NOTES_LENGTH = 200;

    // Suggested charges for a problem that wasn't there at pick-up. The admin can change the amount.
    public static final BigDecimal SCRATCH_CHARGE = new BigDecimal("1500.00");
    public static final BigDecimal DENT_CHARGE = new BigDecimal("3000.00");
    /** For each quarter tank less than at pick-up. */
    public static final BigDecimal FUEL_CHARGE_PER_QUARTER = new BigDecimal("500.00");
    public static final BigDecimal CLEANING_CHARGE = new BigDecimal("500.00");

    private final boolean scratches;
    private final boolean dents;
    private final Fuel fuel;
    private final boolean clean;
    private final String notes;

    /**
     * @param fuel  required: the fuel level has to be checked
     * @param notes optional (e.g. "small scratch on the rear bumper"); trimmed, null when blank
     */
    public VehicleCondition(boolean scratches, boolean dents, Fuel fuel, boolean clean, String notes) {
        if (fuel == null) {
            throw new IllegalArgumentException("Choose the fuel level");
        }
        String n = notes == null ? "" : notes.replaceAll("[\\s|]+", " ").trim();
        if (n.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notes can be at most " + MAX_NOTES_LENGTH + " characters");
        }
        this.scratches = scratches;
        this.dents = dents;
        this.fuel = fuel;
        this.clean = clean;
        this.notes = n.isEmpty() ? null : n;
    }

    public boolean hasScratches() {
        return scratches;
    }

    public boolean hasDents() {
        return dents;
    }

    public Fuel getFuel() {
        return fuel;
    }

    public boolean isClean() {
        return clean;
    }

    /** The note, or null. */
    public String getNotes() {
        return notes;
    }

    /** "Full tank, clean, no damage" or "1/2 tank, needs cleaning, scratches, dents". */
    public String summary() {
        StringBuilder sb = new StringBuilder(fuel.getLabel() + " tank");
        sb.append(clean ? ", clean" : ", needs cleaning");
        if (!scratches && !dents) sb.append(", no damage");
        if (scratches) sb.append(", scratches");
        if (dents) sb.append(", dents");
        return sb.toString();
    }

    /**
     * What is worse at return than at pick-up, e.g. ["New scratches", "Fuel lower: Full -> 1/2"].
     * Empty when nothing got worse.
     */
    public static List<String> newProblems(VehicleCondition pickUp, VehicleCondition atReturn) {
        List<String> problems = new ArrayList<>();
        if (pickUp == null || atReturn == null) return problems;
        if (atReturn.scratches && !pickUp.scratches) problems.add("New scratches");
        if (atReturn.dents && !pickUp.dents) problems.add("New dents");
        if (atReturn.fuel.ordinal() < pickUp.fuel.ordinal()) {
            problems.add("Fuel lower: " + pickUp.fuel.getLabel() + " → " + atReturn.fuel.getLabel());
        }
        if (!atReturn.clean && pickUp.clean) problems.add("Needs cleaning");
        return problems;
    }

    /**
     * The suggested extra charge for what got worse (see the *_CHARGE constants); zero when nothing
     * did, or when either checklist is missing (nothing to compare with).
     */
    public static BigDecimal suggestedCharge(VehicleCondition pickUp, VehicleCondition atReturn) {
        BigDecimal total = Money.ZERO;
        if (pickUp == null || atReturn == null) return total;
        if (atReturn.scratches && !pickUp.scratches) total = total.add(SCRATCH_CHARGE);
        if (atReturn.dents && !pickUp.dents) total = total.add(DENT_CHARGE);
        int quartersMissing = pickUp.fuel.ordinal() - atReturn.fuel.ordinal();
        if (quartersMissing > 0) total = total.add(FUEL_CHARGE_PER_QUARTER.multiply(BigDecimal.valueOf(quartersMissing)));
        if (!atReturn.clean && pickUp.clean) total = total.add(CLEANING_CHARGE);
        return Money.round(total);
    }

    /** One data-file cell: "scratches|dents|FUEL|clean|notes", e.g. "no|no|FULL|yes|". */
    public String encode() {
        return yesNo(scratches) + "|" + yesNo(dents) + "|" + fuel.name() + "|" + yesNo(clean) + "|" + (notes == null ? "" : notes);
    }

    /** Reads {@link #encode()}'s format; null or "" gives null (no checklist recorded). */
    public static VehicleCondition decode(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] f = text.split("\\|", 5);
        if (f.length < 4) throw new IllegalArgumentException("bad vehicle checklist '" + text + "'");
        return new VehicleCondition(parseYesNo(f[0]), parseYesNo(f[1]), Fuel.valueOf(f[2]), parseYesNo(f[3]),
                f.length > 4 ? f[4] : null);
    }

    private static String yesNo(boolean b) {
        return b ? "yes" : "no";
    }

    private static boolean parseYesNo(String s) {
        if (s.equals("yes")) return true;
        if (s.equals("no")) return false;
        throw new IllegalArgumentException("expected yes or no, not '" + s + "'");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VehicleCondition && ((VehicleCondition) o).encode().equals(encode());
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }

    @Override
    public String toString() {
        return "VehicleCondition{" + encode() + "}";
    }
}
