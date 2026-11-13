package model.exceptions;

public class VehicleNotFoundException extends RentNGoException {
    public VehicleNotFoundException(String message) {
        super(message);
    }
}