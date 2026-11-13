package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.List;

/**
 * A vehicle that can be rented. Each subclass (Car, SUV, Truck) answers the questions that differ
 * by type itself (its category, price, features, saved fields and checks), so other code never
 * needs to ask "is this a Car?".
 */
public abstract class Vehicle {
    private String vehicleId;
    private String brand;
    private String model;
    private int year;
    private BigDecimal pricePerDay;
    private boolean available;
    private String licensePlate;
    private String imagePath;

    public Vehicle(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate) {
        this.vehicleId = vehicleId;
        this.brand = brand;
        this.model = model;
        this.year = year;
        this.pricePerDay = Money.round(pricePerDay);
        this.licensePlate = licensePlate;
        this.available = true;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    /** An independent copy: changing it never changes the vehicle held by VehicleService. */
    public abstract Vehicle copy();

    /** Copies the fields every vehicle has onto a new copy. */
    protected <T extends Vehicle> T copyCommonTo(T copy) {
        copy.setAvailable(available);
        copy.setImagePath(imagePath);
        return copy;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    /**
     * "Car", "SUV" or "Truck". Always taken from the subclass, so a Car can never be labelled
     * as a Truck. Same value as {@link #getVehicleCategory()}.
     */
    public final String getType() {
        return getVehicleCategory();
    }

    public BigDecimal getPricePerDay() {
        return pricePerDay;
    }

    public void setPricePerDay(BigDecimal pricePerDay) {
        if (Money.isPositive(pricePerDay)) {
            this.pricePerDay = Money.round(pricePerDay);
        }
    }

    /**
     * Admin-controlled "in service" flag: false while the vehicle is under maintenance.
     * Whether it is booked on given dates comes from BookingService.isVehicleAvailable.
     */
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public abstract String getVehicleCategory();

    /** Total for the given number of days, including any type-specific discount or surcharge. */
    public abstract BigDecimal calculateRentalCost(int days);

    /** Type-specific features for display, e.g. "4 doors" and "Gasoline"; screens join them. */
    public abstract List<String> getFeatures();

    /** The two type-specific values, in the order they are saved (detail1, detail2 in vehicles.txt). */
    public abstract String[] getDetailFields();

    /** Checks the type-specific fields; the fields every vehicle has are checked by VehicleService. */
    public abstract void validateDetails() throws ValidationException;

    /** Daily rate x days, before type adjustments. */
    protected BigDecimal baseCost(int days) {
        return Money.round(pricePerDay.multiply(BigDecimal.valueOf(days)));
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "vehicleId='" + vehicleId + '\'' +
                ", brand='" + brand + '\'' +
                ", model='" + model + '\'' +
                ", year=" + year +
                ", type='" + getVehicleCategory() + '\'' +
                ", pricePerDay=" + pricePerDay +
                ", available=" + available +
                ", licensePlate='" + licensePlate + '\'' +
                '}';
    }
}