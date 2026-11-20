package model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One service or repair done on a vehicle: when, what was done, what it cost and, optionally,
 * when the next service is due. Records are never changed after they are saved, so this class
 * has no setters and can be handed out without copying.
 */
public final class MaintenanceRecord {
    private final String recordId;
    private final String vehicleId;
    private final LocalDate serviceDate;
    private final String description;
    private final BigDecimal cost;
    private final LocalDate nextServiceDate; // null when no next service was planned

    public MaintenanceRecord(String recordId, String vehicleId, LocalDate serviceDate, String description,
                             BigDecimal cost, LocalDate nextServiceDate) {
        this.recordId = recordId;
        this.vehicleId = vehicleId;
        this.serviceDate = serviceDate;
        this.description = description;
        this.cost = Money.round(cost);
        this.nextServiceDate = nextServiceDate;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCost() {
        return cost;
    }

    /** When the next service is due, or null if none was planned. */
    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    @Override
    public String toString() {
        return "MaintenanceRecord{" +
                "recordId='" + recordId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", serviceDate=" + serviceDate +
                ", description='" + description + '\'' +
                ", cost=" + cost +
                ", nextServiceDate=" + nextServiceDate +
                '}';
    }
}
