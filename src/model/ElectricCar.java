package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * A fully electric car (EV). Its two type-specific values are the battery range on a full charge
 * and whether it supports DC fast charging. EVs get a 10% eco discount, the same as a Car whose fuel
 * type is "Electric" (saved before this type existed).
 */
public class ElectricCar extends Vehicle {
    /** The type name saved in vehicles.txt and shown in lists, filters and Reports. */
    public static final String CATEGORY = "EV";
    public static final int MIN_RANGE_KM = 100;
    public static final int MAX_RANGE_KM = 1000;
    /** Multiplier for the eco discount: the rental costs 90% of the base price. */
    public static final String ECO_DISCOUNT_RATE = "0.9";

    private int batteryRangeKm;
    private boolean fastCharging;

    public ElectricCar(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate,
                       int batteryRangeKm, boolean fastCharging) {
        super(vehicleId, brand, model, year, pricePerDay, licensePlate);
        this.batteryRangeKm = batteryRangeKm;
        this.fastCharging = fastCharging;
    }

    /** How far it goes on a full charge, in kilometres. */
    public int getBatteryRangeKm() {
        return batteryRangeKm;
    }

    public void setBatteryRangeKm(int batteryRangeKm) {
        this.batteryRangeKm = batteryRangeKm;
    }

    /** True when it can use DC fast chargers. */
    public boolean hasFastCharging() {
        return fastCharging;
    }

    public void setFastCharging(boolean fastCharging) {
        this.fastCharging = fastCharging;
    }

    @Override
    public String getVehicleCategory() {
        return CATEGORY;
    }

    @Override
    public ElectricCar copy() {
        return copyCommonTo(new ElectricCar(getVehicleId(), getBrand(), getModel(), getYear(), getPricePerDay(),
                getLicensePlate(), batteryRangeKm, fastCharging));
    }

    @Override
    public BigDecimal calculateRentalCost(int days) {
        return Money.times(baseCost(days), ECO_DISCOUNT_RATE); // 10% eco discount for every EV
    }

    @Override
    public List<String> getFeatures() {
        return Arrays.asList(String.format("%,d km range", batteryRangeKm), fastCharging ? "Fast charging" : "Standard charging");
    }

    @Override
    public String[] getDetailFields() {
        return new String[]{String.valueOf(batteryRangeKm), String.valueOf(fastCharging)};
    }

    @Override
    public void validateDetails() throws ValidationException {
        if (batteryRangeKm < MIN_RANGE_KM || batteryRangeKm > MAX_RANGE_KM) {
            throw new ValidationException("EV battery range must be " + MIN_RANGE_KM + " to " + MAX_RANGE_KM + " km");
        }
    }

    @Override
    public String toString() {
        return "ElectricCar{" +
                "batteryRangeKm=" + batteryRangeKm +
                ", fastCharging=" + fastCharging +
                ", vehicleId='" + getVehicleId() + '\'' +
                ", brand='" + getBrand() + '\'' +
                ", model='" + getModel() + '\'' +
                ", pricePerDay=" + getPricePerDay() +
                '}';
    }
}
