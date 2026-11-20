package service;

import model.Booking;
import model.MaintenanceRecord;
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
 * dated by when the payment was taken. Maintenance cost is dated by the service date.
 */
public class ReportService {

    public static final String[] VEHICLE_TYPES = {"Car", "SUV", "Truck", "Van", "EV"};

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

    /**
     * Revenue for each month from {@code first} to {@code last} (both included), oldest first,
     * counted exactly like {@link #getRevenueForMonth}: by payment date, refunds taken off in the
     * month they were made. The Dashboard chart uses this, so it always agrees with Reports.
     */
    public Map<YearMonth, BigDecimal> getRevenueByMonth(YearMonth first, YearMonth last) {
        Map<YearMonth, BigDecimal> totals = new TreeMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) totals.put(m, Money.ZERO);
        for (Payment p : paymentService.getAllPayments()) {
            if (p.getPaymentDate() == null) continue;
            YearMonth m = YearMonth.from(p.getPaymentDate());
            if (totals.containsKey(m)) totals.put(m, totals.get(m).add(p.getAmount()));
        }
        return totals;
    }

    /**
     * All revenue (payments minus refunds) split by the type of vehicle each payment was for, in
     * the order of {@link #VEHICLE_TYPES}. Adds up to {@link #getTotalRevenue()}, except money for
     * a vehicle that was deleted or has another type, which is listed under "Other" (only shown
     * when it isn't zero).
     */
    public Map<String, BigDecimal> getRevenueByVehicleType() {
        Map<String, String> typeOfBooking = new java.util.HashMap<>();
        Map<String, String> typeById = vehicleService.getTypesById();
        for (Booking b : bookingService.getAllBookings()) {
            typeOfBooking.put(b.getBookingId(), typeById.get(b.getVehicleId()));
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String type : VEHICLE_TYPES) totals.put(type, Money.ZERO);
        BigDecimal other = Money.ZERO;
        for (Payment p : paymentService.getAllPayments()) {
            String type = typeOfBooking.get(p.getBookingId());
            if (totals.containsKey(type)) totals.merge(type, p.getAmount(), BigDecimal::add);
            else other = other.add(p.getAmount());
        }
        if (other.signum() != 0) totals.put(OTHER_TYPE, other);
        return totals;
    }

    /** The heading for revenue whose vehicle was deleted or isn't a Car, SUV, Truck, Van or EV. */
    public static final String OTHER_TYPE = "Other";

    /**
     * The average price of the rentals that start in the given month (non-cancelled, rental cost
     * plus any late fee and extra charge), or zero when there are none. It is a price, not money
     * received, so it does not depend on when, or whether, the rental was paid.
     */
    public BigDecimal getAverageRentalValueForMonth(YearMonth month) {
        BigDecimal total = Money.ZERO;
        long count = 0;
        for (Booking b : bookingService.getAllBookings()) {
            if (b.getStatus() != BookingStatus.CANCELLED && YearMonth.from(b.getStartDate()).equals(month)) {
                total = total.add(b.getFinalAmount());
                count++;
            }
        }
        return count == 0 ? Money.ZERO : total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
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

    /** Everything spent on maintenance (deleted vehicles included). */
    public BigDecimal getTotalMaintenanceCost() {
        return vehicleService.getAllMaintenanceRecords().stream()
                .map(MaintenanceRecord::getCost)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    /** Maintenance done in the given month (by service date). */
    public BigDecimal getMaintenanceCostForMonth(YearMonth month) {
        return vehicleService.getAllMaintenanceRecords().stream()
                .filter(r -> YearMonth.from(r.getServiceDate()).equals(month))
                .map(MaintenanceRecord::getCost)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    /** Non-cancelled bookings whose rental starts in the given month. */
    public long getBookingCountForMonth(YearMonth month) {
        return bookingService.getAllBookings().stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && YearMonth.from(b.getStartDate()).equals(month))
                .count();
    }

    // ===== CSV export =====

    /** How many months (ending with this one) the export lists. */
    public static final int EXPORT_MONTHS = 12;

    /**
     * Saves the report as a CSV file for Excel (admin only): a summary, revenue and maintenance per
     * month for the last 12 months, and bookings per vehicle type. Amounts are plain numbers
     * (e.g. 2500.00) so Excel can add them up; the ₱ is in the column headings. Recorded in the Audit Log.
     */
    public void exportCsv(java.io.File file) throws java.io.IOException, model.exceptions.ValidationException {
        AuthService auth = bookingService.auth();
        String denied = auth == null ? AuthService.NOT_LOGGED_IN : auth.adminOnly("export reports");
        if (denied != null) throw new model.exceptions.ValidationException(denied);

        YearMonth thisMonth = YearMonth.now();
        java.util.List<java.util.List<?>> rows = new java.util.ArrayList<>();
        BigDecimal revenue = getTotalRevenue();
        BigDecimal maintenance = getTotalMaintenanceCost();
        rows.add(java.util.List.of("Generated", java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')));
        rows.add(java.util.List.of());
        rows.add(java.util.List.of("Summary", "Value"));
        rows.add(java.util.List.of("Total revenue (₱)", revenue.toPlainString()));
        rows.add(java.util.List.of("Total maintenance cost (₱)", maintenance.toPlainString()));
        rows.add(java.util.List.of("Revenue minus maintenance (₱)", revenue.subtract(maintenance).toPlainString()));
        rows.add(java.util.List.of("Active rentals", getActiveRentalCount()));
        rows.add(java.util.List.of("Most-rented vehicle", getMostRentedVehicle()
                .map(v -> v.getName() + " (" + v.getVehicleId() + ", " + v.getRentals() + (v.getRentals() == 1 ? " booking)" : " bookings)"))
                .orElse("none yet")));

        // year and month name in separate columns: Excel would turn "Nov 2026" into a date
        rows.add(java.util.List.of());
        rows.add(java.util.List.of("Year", "Month", "Revenue (₱)", "Maintenance (₱)", "Revenue minus maintenance (₱)", "Bookings"));
        for (int i = EXPORT_MONTHS - 1; i >= 0; i--) {
            YearMonth m = thisMonth.minusMonths(i);
            BigDecimal monthRevenue = getRevenueForMonth(m);
            BigDecimal monthMaintenance = getMaintenanceCostForMonth(m);
            rows.add(java.util.List.of(m.getYear(),
                    m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
                    monthRevenue.toPlainString(), monthMaintenance.toPlainString(),
                    monthRevenue.subtract(monthMaintenance).toPlainString(), getBookingCountForMonth(m)));
        }

        rows.add(java.util.List.of());
        rows.add(java.util.List.of("Vehicle type", "Bookings", "Share (%)"));
        Map<String, Long> byType = getBookingsByVehicleType();
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        for (Map.Entry<String, Long> e : byType.entrySet()) {
            String share = total == 0 ? "0" : BigDecimal.valueOf(e.getValue() * 100.0 / total)
                    .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString();
            rows.add(java.util.List.of(e.getKey(), e.getValue(), share));
        }
        rows.add(java.util.List.of("Total", total, total == 0 ? "0" : "100.0"));

        CsvWriter.write(file, java.util.List.of("RentNGo Report", "Revenue is payments received minus refunds"), rows);
        bookingService.getAuditLog().record(AuditLog.REPORT_EXPORTED, null, "Reports exported to " + file.getName()
                + " (" + thisMonth.minusMonths(EXPORT_MONTHS - 1) + " to " + thisMonth + ")");
    }

    /** Vehicles picked up and not yet returned. */
    public long getActiveRentalCount() {
        return bookingService.getBookingsByStatus(BookingStatus.ACTIVE).size();
    }

    /** Non-cancelled bookings per vehicle type, always listing Car, SUV, Truck, Van and EV in that order. */
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
