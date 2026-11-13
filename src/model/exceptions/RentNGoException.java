package model.exceptions;

public class RentNGoException extends Exception {
    public RentNGoException(String message) {
        super(message);
    }

    public RentNGoException(String message, Throwable cause) {
        super(message, cause);
    }
}