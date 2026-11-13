package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class Truck extends Vehicle {
    private double loadCapacity; // in tons
    private boolean hasLiftGate;

    public Truck(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate, double loadCapacity, boolean hasLiftGate) {
        super(vehicleId, brand, model, year, pricePerDay, licensePlate);
        this.loadCapacity = loadCapacity;
        this.hasLiftGate = hasLiftGate;
    }

    public double getLoadCapacity() {
        return loadCapacity;
    }

    public void setLoadCapacity(double loadCapacity) {
        this.loadCapacity = loadCapacity;
    }

    public boolean isHasLiftGate() {
        return hasLiftGate;
    }

    public void setHasLiftGate(boolean hasLiftGate) {
        this.hasLiftGate = hasLiftGate;
    }

    @Override
    public String getVehicleCategory() {
        return "Truck";
    }

    @Override
    public Truck copy() {
        return copyCommonTo(new Truck(getVehicleId(), getBrand(), getModel(), getYear(), getPricePerDay(), getLicensePlate(), loadCapacity, hasLiftGate));
    }

    @Override
    public BigDecimal calculateRentalCost(int days) {
        BigDecimal baseCost = baseCost(days);
        if (loadCapacity > 5) {
            return Money.times(baseCost, "1.2"); // 20% surcharge for heavy duty
        }
        return baseCost;
    }

    @Override
    public List<String> getFeatures() {
        return Arrays.asList(loadCapacity + " tons", hasLiftGate ? "Lift gate" : "No lift gate");
    }

    @Override
    public String[] getDetailFields() {
        return new String[]{String.valueOf(loadCapacity), String.valueOf(hasLiftGate)};
    }

    @Override
    public void validateDetails() throws ValidationException {
        if (loadCapacity <= 0) throw new ValidationException("Load capacity must be greater than 0");
    }

    @Override
    public String toString() {
        return "Truck{" +
                "loadCapacity=" + loadCapacity +
                ", hasLiftGate=" + hasLiftGate +
                ", vehicleId='" + getVehicleId() + '\'' +
                ", brand='" + getBrand() + '\'' +
                ", model='" + getModel() + '\'' +
                ", pricePerDay=" + getPricePerDay() +
                '}';
    }
}