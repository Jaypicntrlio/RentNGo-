package service;

import model.Booking;
import model.BookingStatus;
import model.Money;
import model.Payment;
import model.Vehicle;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Figures for the admin Reports view. Revenue is money actually received: payments minus refunds,
 * dated by when the payment was taken.
 */
public class ReportService {

    public static final String[] VEHICLE_TYPES = {"Car", "SUV", "Truck"};

    private final BookingService bookingService;
    private final VehicleService vehicleService;
    private final PaymentService paymentService;

    public ReportService(BookingService bookingService, VehicleService vehicleService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.vehicleService = vehicleService;
        this.paymentService = paymentService;
    }

    /** The vehicle with the most bookings, and how many it has. */
    public static class VehicleRentals {
        private final String vehicleId;
        private final String name;
        private final long rentals;

        public VehicleRentals(String vehicleId, String name, long rentals) {
            this.vehicleId = vehicleId;
            this.name = name;
            this.rentals = rentals;
        }

        public String getVehicleId() { return vehicleId; }
        public String getName() { return name; }
        public long getRentals() { return rentals; }
    }

    /** All payments received, minus refunds. */
    public BigDecimal getTotalRevenue() {
        return paymentService.getAllPayments().stream()
                .map(Payment::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    /** Payments received in the given month, minus refunds made that month. */
    public BigDecimal getRevenueForMonth(YearMonth month) {
        return paymentService.getAllPayments().stream()
                .filter(p -> p.getPaymentDate() != null && YearMonth.from(p.getPaymentDate()).equals(month))
                .map(Payment::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    /** Vehicle with the most non-cancelled bookings; ties go to the lower vehicle ID. Empty if nothing was booked. */
    public Optional<VehicleRentals> getMostRentedVehicle() {
        Map<String, Long> counts = new TreeMap<>();
        for (Booking b : bookingService.getAllBookings()) {
            if (b.getStatus() != BookingStatus.CANCELLED) {
                counts.merge(b.getVehicleId(), 1L, Long::sum);
            }
        }
        String bestId = null;
        long best = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > best) {
                bestId = e.getKey();
                best = e.getValue();
            }
        }
        if (bestId == null) return Optional.empty();
        String id = bestId;
        String name = vehicleService.getVehicleById(id)
                .map(v -> v.getBrand() + " " + v.getModel())
                .orElse(id + " (deleted)");
        return Optional.of(new VehicleRentals(id, name, best));
    }

    /** Vehicles picked up and not yet returned. */
    public long getActiveRentalCount() {
        return bookingService.getBookingsByStatus(BookingStatus.ACTIVE).size();
    }

    /** Non-cancelled bookings per vehicle type, always listing Car, SUV and Truck in that order. */
    public Map<String, Long> getBookingsByVehicleType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String type : VEHICLE_TYPES) counts.put(type, 0L);
        Map<String, String> typeById = vehicleService.getTypesById(); // one pass, not a search per booking
        for (Booking b : bookingService.getAllBookings()) {
            if (b.getStatus() == BookingStatus.CANCELLED) continue;
            String type = typeById.get(b.getVehicleId());
            if (type != null) counts.merge(type, 1L, Long::sum);
        }
        return counts;
    }
}
