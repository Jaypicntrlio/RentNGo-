package model;

import model.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class SUV extends Vehicle {
    private boolean fourWheelDrive;
    private int seatingCapacity;

    public SUV(String vehicleId, String brand, String model, int year, BigDecimal pricePerDay, String licensePlate, boolean fourWheelDrive, int seatingCapacity) {
        super(vehicleId, brand, model, year, pricePerDay, licensePlate);
        this.fourWheelDrive = fourWheelDrive;
        this.seatingCapacity = seatingCapacity;
    }

    public boolean isFourWheelDrive() {
        return fourWheelDrive;
    }

    public void setFourWheelDrive(boolean fourWheelDrive) {
        this.fourWheelDrive = fourWheelDrive;
    }

    public int getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(int seatingCapacity) {
        this.seatingCapacity = seatingCapacity;
    }

    @Override
    public String getVehicleCategory() {
        return "SUV";
    }

    @Override
    public SUV copy() {
        return copyCommonTo(new SUV(getVehicleId(), getBrand(), getModel(), getYear(), getPricePerDay(), getLicensePlate(), fourWheelDrive, seatingCapacity));
    }

    @Override
    public BigDecimal calculateRentalCost(int days) {
        BigDecimal baseCost = baseCost(days);
        if (fourWheelDrive) {
            return Money.times(baseCost, "1.15"); // 15% surcharge for 4WD
        }
        return baseCost;
    }

    @Override
    public List<String> getFeatures() {
        return Arrays.asList(fourWheelDrive ? "4WD" : "2WD", seatingCapacity + " seats");
    }

    @Override
    public String[] getDetailFields() {
        return new String[]{String.valueOf(fourWheelDrive), String.valueOf(seatingCapacity)};
    }

    @Override
    public void validateDetails() throws ValidationException {
        if (seatingCapacity <= 0) throw new ValidationException("Seating capacity must be at least 1");
    }

    @Override
    public String toString() {
        return "SUV{" +
                "fourWheelDrive=" + fourWheelDrive +
                ", seatingCapacity=" + seatingCapacity +
                ", vehicleId='" + getVehicleId() + '\'' +
                ", brand='" + getBrand() + '\'' +
                ", model='" + getModel() + '\'' +
                ", pricePerDay=" + getPricePerDay() +
                '}';
    }
}