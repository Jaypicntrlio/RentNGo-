package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * A passenger van, for groups and families. Its two type-specific values are the number of seats
 * and whether it has a sliding side door.
 */
public class Van extends Vehicle {
    /** Vans with at least this many seats pay the large-van surcharge. */
    public static final int LARGE_VAN_SEATS = 12;
    public static final int MIN_SEATS = 8;
    public static final int MAX_SEATS = 18;

    private int seatingCapacity;
    private boolean slidingDoor;

    public Van(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate, int seatingCapacity, boolean slidingDoor) {
        super(vehicleId, brand, model, year, pricePerDay, licensePlate);
        this.seatingCapacity = seatingCapacity;
        this.slidingDoor = slidingDoor;
    }

    public int getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(int seatingCapacity) {
        this.seatingCapacity = seatingCapacity;
    }

    public boolean hasSlidingDoor() {
        return slidingDoor;
    }

    public void setSlidingDoor(boolean slidingDoor) {
        this.slidingDoor = slidingDoor;
    }

    @Override
    public String getVehicleCategory() {
        return "Van";
    }

    @Override
    public Van copy() {
        return copyCommonTo(new Van(getVehicleId(), getBrand(), getModel(), getYear(), getPricePerDay(), getLicensePlate(), seatingCapacity, slidingDoor));
    }

    @Override
    public BigDecimal calculateRentalCost(int days) {
        BigDecimal baseCost = baseCost(days);
        if (seatingCapacity >= LARGE_VAN_SEATS) {
            return Money.times(baseCost, "1.1"); // 10% surcharge for a large van (12+ seats)
        }
        return baseCost;
    }

    @Override
    public List<String> getFeatures() {
        return Arrays.asList(seatingCapacity + " seats", slidingDoor ? "Sliding door" : "No sliding door");
    }

    @Override
    public String[] getDetailFields() {
        return new String[]{String.valueOf(seatingCapacity), String.valueOf(slidingDoor)};
    }

    @Override
    public void validateDetails() throws ValidationException {
        if (seatingCapacity < MIN_SEATS || seatingCapacity > MAX_SEATS) {
            throw new ValidationException("Van seating capacity must be " + MIN_SEATS + " to " + MAX_SEATS);
        }
    }

    @Override
    public String toString() {
        return "Van{" +
                "seatingCapacity=" + seatingCapacity +
                ", slidingDoor=" + slidingDoor +
                ", vehicleId='" + getVehicleId() + '\'' +
                ", brand='" + getBrand() + '\'' +
                ", model='" + getModel() + '\'' +
                ", pricePerDay=" + getPricePerDay() +
                '}';
    }
}
