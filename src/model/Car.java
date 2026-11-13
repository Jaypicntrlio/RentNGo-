package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class Car extends Vehicle {
    private int numberOfDoors;
    private String fuelType;

    public Car(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate, int numberOfDoors, String fuelType) {
        super(vehicleId, brand, model, year, pricePerDay, licensePlate);
        this.numberOfDoors = numberOfDoors;
        this.fuelType = fuelType;
    }

    public int getNumberOfDoors() {
        return numberOfDoors;
    }

    public void setNumberOfDoors(int numberOfDoors) {
        this.numberOfDoors = numberOfDoors;
    }

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    @Override
    public String getVehicleCategory() {
        return "Car";
    }

    @Override
    public Car copy() {
        return copyCommonTo(new Car(getVehicleId(), getBrand(), getModel(), getYear(), getPricePerDay(), getLicensePlate(), numberOfDoors, fuelType));
    }

    @Override
    public BigDecimal calculateRentalCost(int days) {
        BigDecimal baseCost = baseCost(days);
        if ("Electric".equalsIgnoreCase(fuelType)) {
            return Money.times(baseCost, "0.9"); // 10% discount for electric
        }
        return baseCost;
    }

    @Override
    public List<String> getFeatures() {
        return Arrays.asList(numberOfDoors + " doors", fuelType);
    }

    @Override
    public String[] getDetailFields() {
        return new String[]{String.valueOf(numberOfDoors), fuelType};
    }

    @Override
    public void validateDetails() throws ValidationException {
        if (numberOfDoors <= 0) throw new ValidationException("Number of doors must be at least 1");
        if (fuelType == null || fuelType.trim().isEmpty()) throw new ValidationException("Fuel type is required");
    }

    @Override
    public String toString() {
        return "Car{" +
                "numberOfDoors=" + numberOfDoors +
                ", fuelType='" + fuelType + '\'' +
                ", vehicleId='" + getVehicleId() + '\'' +
                ", brand='" + getBrand() + '\'' +
                ", model='" + getModel() + '\'' +
                ", pricePerDay=" + getPricePerDay() +
                '}';
    }
}