package service;

import model.*;
import model.exceptions.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VehicleService {
    private static final String HEADER = "RentNGo vehicles - type\tid\tbrand\tmodel\tyear\tpricePerDay\tplate\tinService\timagePath\tdetail1\tdetail2";

    private List<Vehicle> vehicles;
    private final File dataFile;
    private final File imagesDir;
    private String loadError;
    private boolean readOnly;
    private AuditLog audit = AuditLog.NONE;
    /** The session changes are checked against; set by BookingService. Without one, nothing can be changed. */
    private AuthService auth;
    /** Why changes are refused while read-only (unreadable file, or changed by another program). */
    private String readOnlyMessage;
    /** The file as it was when last loaded or saved; a save is refused if it changed since. */
    private TsvFile.Stamp stamp;
    /** How many CONFIRMED or ACTIVE bookings a vehicle has; set by BookingService, so deletes never trust the caller's list. */
    private java.util.function.ToLongFunction<String> openBookings;
    /** Service and repair history, saved next to vehicles.txt as maintenance.txt. */
    private final MaintenanceStore maintenance;
    /** Each customer's starred vehicles, saved next to vehicles.txt as favorites.txt. */
    private final FavoritesStore favorites;

    /** Longest "work done" text for a maintenance record. */
    public static final int MAX_MAINTENANCE_DESCRIPTION = 200;
    /** Highest cost accepted for one maintenance record (catches typing mistakes such as an extra 0). */
    public static final java.math.BigDecimal MAX_MAINTENANCE_COST = Money.of(1_000_000);

    /** Vehicles saved in data/vehicles.txt, photos in images/. */
    public VehicleService() {
        this(new File(ProjectPaths.dir("data"), "vehicles.txt"), ProjectPaths.dir("images"));
    }

    /** Pass null for either to keep vehicles in memory only / leave photo files alone (tests). */
    public VehicleService(File dataFile, File imagesDir) {
        this.dataFile = dataFile;
        this.stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
        this.imagesDir = imagesDir;
        this.vehicles = new ArrayList<>();
        this.maintenance = new MaintenanceStore(dataFile == null ? null
                : new File(dataFile.getAbsoluteFile().getParentFile(), "maintenance.txt"));
        this.favorites = new FavoritesStore(dataFile == null ? null
                : new File(dataFile.getAbsoluteFile().getParentFile(), "favorites.txt"));
        if (dataFile == null || !dataFile.isFile()) {
            // first run (or in-memory): start with the demo fleet
            initializeDefaultVehicles();
            save();
            return;
        }
        try {
            load();
        } catch (IOException e) {
            // keep the file as it is and never save over it; no demo vehicles either
            readOnly = true;
            loadError = TsvFile.describeUnreadable(dataFile, e);
            readOnlyMessage = loadError;
        }
    }

    /** Uses this session for the admin-only checks (called by BookingService when it is created). */
    void useAuthService(AuthService auth) {
        this.auth = auth;
    }

    /** Uses the booking service's own records for the "has open bookings" check on delete. */
    void useOpenBookingCount(java.util.function.ToLongFunction<String> openBookings) {
        this.openBookings = openBookings;
    }

    /** Throws unless an admin is logged in. */
    private void requireAdmin(String action) throws ValidationException {
        String denied = auth == null ? AuthService.NOT_LOGGED_IN : auth.adminOnly(action);
        if (denied != null) throw new ValidationException(denied);
    }

    /** The real vehicle, for changes made inside this service; never handed to windows. */
    private Optional<Vehicle> find(String vehicleId) {
        return vehicles.stream().filter(v -> v.getVehicleId().equals(vehicleId)).findFirst();
    }

    /** Where vehicle adds, edits and deletes are recorded (data/audit.txt). */
    public void setAuditLog(AuditLog audit) {
        this.audit = audit != null ? audit : AuditLog.NONE;
    }

    /** Records an action only if it was actually saved. */
    private void logIfSaved(String action, String details) {
        if (!readOnly) audit.record(action, null, details);
    }

    private static String describe(Vehicle v) {
        return v.getVehicleId() + " " + v.getBrand() + " " + v.getModel() + " " + v.getYear() + " (" + v.getType()
                + ", " + v.getLicensePlate() + ", \u20B1" + v.getPricePerDay().toPlainString() + "/day"
                + (v.isAvailable() ? "" : ", out of service") + ")";
    }

    /** "price 2500.00 -> 2000.00; plate ABC -> XYZ" for the fields that changed. */
    private static String changes(Vehicle before, Vehicle after) {
        List<String> parts = new ArrayList<>();
        diff(parts, "type", before.getType(), after.getType());
        diff(parts, "brand", before.getBrand(), after.getBrand());
        diff(parts, "model", before.getModel(), after.getModel());
        diff(parts, "year", String.valueOf(before.getYear()), String.valueOf(after.getYear()));
        diff(parts, "price/day", before.getPricePerDay().toPlainString(), after.getPricePerDay().toPlainString());
        diff(parts, "plate", before.getLicensePlate(), after.getLicensePlate());
        diff(parts, "in service", String.valueOf(before.isAvailable()), String.valueOf(after.isAvailable()));
        diff(parts, "photo", String.valueOf(before.getImagePath()), String.valueOf(after.getImagePath()));
        return parts.isEmpty() ? "no field changes" : String.join("; ", parts);
    }

    private static void diff(List<String> parts, String name, String before, String after) {
        if (!java.util.Objects.equals(before, after)) parts.add(name + " " + before + " -> " + after);
    }

    /** Why the saved file couldn't be (fully) loaded, or null if it loaded fine (shown at startup). */
    public String getLoadError() {
        List<String> errors = new ArrayList<>();
        for (String error : new String[]{loadError, maintenance.getLoadError(), favorites.getLoadError()}) {
            if (error != null) errors.add(error);
        }
        return errors.isEmpty() ? null : String.join("\n\n", errors);
    }

    /** True when vehicles.txt couldn't be read; changes are then not saved. */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** Loads every vehicle row that can be read; bad rows are skipped and reported in loadError. */
    private void load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(dataFile);
        stamp = contents.stamp;
        TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
        // Older files stored "rented or not" in this column; that is now calculated from bookings,
        // so those values are ignored and every vehicle starts in service.
        boolean legacyAvailability = contents.firstLine.contains("\tavailable\t");
        for (TsvFile.Row row : contents.rows) {
            try {
                vehicles.add(parseVehicle(row.fields, legacyAvailability));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        loadError = warnings.summary();
    }

    private static Vehicle parseVehicle(String[] f, boolean legacyAvailability) {
        TsvFile.requireFields(f, 11);
        String id = f[1], brand = f[2], model = f[3], plate = f[6];
        if (id.isBlank()) throw new IllegalArgumentException("vehicle ID is required");
        int year = Integer.parseInt(f[4]);
        java.math.BigDecimal price = Money.parse(f[5]);
        Vehicle v;
        switch (f[0]) {
            case "SUV":
                v = new SUV(id, brand, model, year, price, plate, Boolean.parseBoolean(f[9]), Integer.parseInt(f[10]));
                break;
            case "Truck":
                v = new Truck(id, brand, model, year, price, plate, Double.parseDouble(f[9]), Boolean.parseBoolean(f[10]));
                break;
            case "Car":
                v = new Car(id, brand, model, year, price, plate, Integer.parseInt(f[9]), f[10]);
                break;
            case "Van":
                v = new Van(id, brand, model, year, price, plate, Integer.parseInt(f[9]), Boolean.parseBoolean(f[10]));
                break;
            case ElectricCar.CATEGORY: // "EV": battery range (km), fast charging
                v = new ElectricCar(id, brand, model, year, price, plate, Integer.parseInt(f[9]), Boolean.parseBoolean(f[10]));
                break;
            default:
                throw new IllegalArgumentException("unknown vehicle type '" + f[0] + "'");
        }
        v.setAvailable(legacyAvailability || Boolean.parseBoolean(f[7]));
        v.setImagePath(f[8].isEmpty() ? null : f[8]);
        return v;
    }

    private void requireWritable() throws ValidationException {
        if (readOnly) {
            throw new ValidationException(readOnlyMessage);
        }
    }

    /** Writes all vehicles to disk; does nothing for an in-memory service or a file that couldn't be read. */
    public void save() {
        try {
            write();
        } catch (TsvFile.DataChangedException e) {
            TsvFile.reportChanged(e);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Saves; if the file changed on disk the save is refused, {@code undo} puts the vehicle list
     * back as it was, and the caller gets the reason.
     */
    private void saveOrUndo(Runnable undo) throws ValidationException {
        try {
            write();
        } catch (IOException e) { // changed on disk, or couldn't be written at all
            undo.run();
            throw new ValidationException(e.getMessage());
        }
    }

    /** Like save(), but hands a refused save back to the caller. */
    private void write() throws IOException {
        if (dataFile == null) return;
        if (readOnly) {
            System.err.println("Not saving vehicles: " + readOnlyMessage);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Vehicle v : vehicles) {
            String[] details = v.getDetailFields(); // each vehicle type supplies its own two fields
            rows.add(new String[]{v.getType(), v.getVehicleId(), v.getBrand(), v.getModel(),
                    String.valueOf(v.getYear()), v.getPricePerDay().toPlainString(), v.getLicensePlate(),
                    String.valueOf(v.isAvailable()), v.getImagePath(), details[0], details[1]});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, new ArrayList<>(), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another copy of RentNGo (or another program) wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save vehicles: " + e.getMessage(), e);
        }
    }

    private void initializeDefaultVehicles() {
        vehicles.add(new Car("VEH001", "Toyota", "Camry", 2023, Money.of(2500), "ABC-1234", 4, "Gasoline"));
        vehicles.add(new Car("VEH002", "Honda", "Accord", 2023, Money.of(2800), "DEF-5678", 4, "Gasoline"));
        vehicles.add(new Car("VEH003", "Tesla", "Model 3", 2024, Money.of(4500), "GHI-9012", 4, "Electric"));
        vehicles.add(new SUV("VEH004", "Ford", "Explorer", 2023, Money.of(3500), "JKL-3456", true, 7));
        vehicles.add(new SUV("VEH005", "Jeep", "Grand Cherokee", 2023, Money.of(3800), "MNO-7890", true, 5));
        vehicles.add(new Truck("VEH006", "Ford", "F-150", 2023, Money.of(3200), "PQR-1111", 2.5, false));
        vehicles.add(new Truck("VEH007", "Chevrolet", "Silverado", 2023, Money.of(3800), "STU-2222", 3.0, true));
        vehicles.add(new ElectricCar("VEH008", "BYD", "Atto 3", 2024, Money.of(3000), "EVB-3003", 420, true));
        vehicles.add(new ElectricCar("VEH009", "Nissan", "Leaf", 2023, Money.of(2600), "EVN-2023", 270, false));
    }

    // Reads return copies: changing one never changes the stored vehicle.

    public List<Vehicle> getAllVehicles() {
        return vehicles.stream().map(Vehicle::copy).collect(Collectors.toList());
    }

    /** Vehicles that are in service (not under maintenance), whether or not they are booked. */
    public List<Vehicle> getInServiceVehicles() {
        return vehicles.stream()
                .filter(Vehicle::isAvailable)
                .map(Vehicle::copy)
                .collect(Collectors.toList());
    }

    public List<Vehicle> getVehiclesByType(String type) {
        return vehicles.stream()
                .filter(v -> v.getType().equalsIgnoreCase(type))
                .map(Vehicle::copy)
                .collect(Collectors.toList());
    }

    public Optional<Vehicle> getVehicleById(String vehicleId) {
        return find(vehicleId).map(Vehicle::copy);
    }

    /**
     * Each vehicle's type ("Car", "SUV", "Truck", "Van" or "EV") by vehicle ID. Reports use it to group many bookings
     * in one pass instead of searching the vehicle list (and copying a vehicle) once per booking.
     */
    public Map<String, String> getTypesById() {
        Map<String, String> types = new HashMap<>();
        for (Vehicle v : vehicles) {
            types.put(v.getVehicleId(), v.getType());
        }
        return types;
    }

    /** Oldest model year accepted; the newest is next year (dealers sell next year's models early). */
    public static final int MIN_YEAR = 1990;

    public static int maxYear() {
        return java.time.Year.now().getValue() + 1;
    }

    /** All the rules a vehicle must meet, for both adding and editing. */
    private static void validateVehicle(Vehicle v) throws ValidationException {
        if (v == null) {
            throw new ValidationException("Vehicle cannot be null");
        }
        if (isBlank(v.getVehicleId())) throw new ValidationException("Vehicle ID is required");
        // the ID also names the photo file (images/VEH003.png), so no folders, dots or spaces
        if (!v.getVehicleId().matches("[A-Za-z0-9-]{1,20}")) {
            throw new ValidationException("Vehicle ID can only use letters, numbers and '-' (at most 20), e.g. VEH008");
        }
        if (isBlank(v.getBrand())) throw new ValidationException("Brand is required");
        if (isBlank(v.getModel())) throw new ValidationException("Model is required");
        if (isBlank(v.getLicensePlate())) throw new ValidationException("License plate is required");
        if (!Money.isPositive(v.getPricePerDay())) {
            throw new ValidationException("Price per day must be greater than 0");
        }
        if (v.getYear() < MIN_YEAR || v.getYear() > maxYear()) {
            throw new ValidationException("Year must be between " + MIN_YEAR + " and " + maxYear());
        }
        v.validateDetails(); // Car, SUV, Truck, Van and EV each check their own fields
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public Vehicle addVehicle(Vehicle vehicle) throws ValidationException {
        requireAdmin("add vehicles");
        requireWritable();
        validateVehicle(vehicle);
        // case doesn't count: on Windows VEH001.jpg and veh001.jpg are the same photo file
        if (vehicles.stream().anyMatch(v -> v.getVehicleId().equalsIgnoreCase(vehicle.getVehicleId()))) {
            throw new ValidationException("Vehicle ID already exists");
        }
        if (plateTaken(vehicle.getLicensePlate(), null)) {
            throw new ValidationException("License plate already registered");
        }
        Vehicle stored = vehicle.copy();
        vehicles.add(stored);
        saveOrUndo(() -> vehicles.remove(stored));
        logIfSaved(AuditLog.VEHICLE_ADDED, describe(stored));
        return stored.copy();
    }

    public Vehicle updateVehicle(String vehicleId, Vehicle updatedVehicle) throws VehicleNotFoundException, ValidationException {
        requireAdmin("edit vehicles");
        Vehicle existing = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        requireWritable();
        validateVehicle(updatedVehicle);

        if (plateTaken(updatedVehicle.getLicensePlate(), existing)) {
            throw new ValidationException("License plate already registered");
        }
        // late fees use the vehicle's current daily rate and type, so they can't change under an open rental
        boolean priceOrTypeChanged = existing.getClass() != updatedVehicle.getClass()
                || existing.getPricePerDay().compareTo(updatedVehicle.getPricePerDay()) != 0;
        if (priceOrTypeChanged && openBookings != null && openBookings.applyAsLong(vehicleId) > 0) {
            throw new ValidationException("This vehicle has upcoming or active bookings, so its price and type can't be "
                    + "changed until they are returned or cancelled. Other details can still be edited.");
        }

        // Store a copy of the edited vehicle in place of the old one. copy() is polymorphic, so this
        // works for every type, including a type change (Car -> SUV), and the old object is the "before".
        Vehicle stored = updatedVehicle.copy();
        stored.setVehicleId(existing.getVehicleId());
        int index = vehicles.indexOf(existing);
        vehicles.set(index, stored);
        saveOrUndo(() -> vehicles.set(index, existing));
        String change = existing.getClass() == stored.getClass()
                ? changes(existing, stored)
                : "was " + describe(existing) + "; now " + describe(stored);
        logIfSaved(AuditLog.VEHICLE_EDITED, existing.getVehicleId() + ": " + change);
        return stored.copy();
    }

    /** Records the vehicle's photo file name (inside images/) and saves. */
    public void setVehicleImage(String vehicleId, String imagePath) throws VehicleNotFoundException, ValidationException {
        requireAdmin("change vehicle photos");
        Vehicle vehicle = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        requireWritable();
        if (imagePath != null && !imagePath.isEmpty() && !isPlainFileName(imagePath)) {
            throw new ValidationException("The photo must be a file name inside the images folder, not a path: " + imagePath);
        }
        String oldPath = vehicle.getImagePath();
        vehicle.setImagePath(imagePath);
        saveOrUndo(() -> vehicle.setImagePath(oldPath));
        logIfSaved(AuditLog.VEHICLE_EDITED, vehicleId + ": photo " + oldPath + " -> " + imagePath);
    }

    /**
     * Refuses deletion while the vehicle has CONFIRMED or ACTIVE bookings. The caller passes the
     * vehicle's bookings (e.g. bookingService.getBookingsByVehicle(id)), so this service never
     * depends on BookingService, which itself depends on VehicleService.
     */
    public void checkCanDelete(String vehicleId, List<Booking> vehicleBookings) throws ValidationException {
        long open = vehicleBookings.stream()
                .filter(b -> b.getVehicleId().equals(vehicleId))
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.ACTIVE)
                .count();
        if (openBookings != null) {
            // the service's own records win: a stale or empty list from a window can't allow a delete
            open = Math.max(open, openBookings.applyAsLong(vehicleId));
        }
        if (open > 0) {
            throw new ValidationException("Cannot delete: vehicle has " + open + " upcoming or active booking"
                    + (open == 1 ? "" : "s"));
        }
    }

    public void deleteVehicle(String vehicleId, List<Booking> vehicleBookings) throws VehicleNotFoundException, ValidationException {
        requireAdmin("delete vehicles");
        Vehicle vehicle = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        requireWritable();
        checkCanDelete(vehicleId, vehicleBookings);
        int index = vehicles.indexOf(vehicle);
        vehicles.remove(index);
        saveOrUndo(() -> vehicles.add(index, vehicle)); // refused: the vehicle and its photos stay
        logIfSaved(AuditLog.VEHICLE_DELETED, describe(vehicle));
        try {
            favorites.removeVehicle(vehicleId);
        } catch (IOException e) {
            // the vehicle is gone either way; getFavoriteVehicleIds skips IDs that no longer exist
            System.err.println("Could not remove " + vehicleId + " from favorites: " + e.getMessage());
        }
        // remove its photos too, so a future vehicle given the same ID doesn't inherit them
        if (imagesDir != null) {
            for (String ext : new String[]{"jpg", "jpeg", "png"}) {
                deleteInsideImages(vehicleId + "." + ext);
            }
            String path = vehicle.getImagePath();
            if (path != null && !path.isEmpty()) {
                deleteInsideImages(path);
            }
        }
    }

    // ===== Favorites =====

    /** The logged-in customer's ID, or null when no customer is logged in. */
    private String currentCustomerId() {
        if (auth == null || !auth.isCustomerSession()) return null;
        return auth.getCurrentUser().getUserId();
    }

    /** IDs of the vehicles the logged-in customer starred (deleted vehicles left out). Empty for admins. */
    public java.util.Set<String> getFavoriteVehicleIds() {
        String customerId = currentCustomerId();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (customerId == null) return ids;
        for (String id : favorites.of(customerId)) {
            if (find(id).isPresent()) ids.add(id);
        }
        return ids;
    }

    public boolean isFavorite(String vehicleId) {
        return getFavoriteVehicleIds().contains(vehicleId);
    }

    /** Stars (true) or unstars (false) a vehicle for the logged-in customer and saves it. */
    public void setFavorite(String vehicleId, boolean favorite) throws VehicleNotFoundException, ValidationException {
        String customerId = currentCustomerId();
        if (customerId == null) throw new ValidationException("Only customers can save favorite vehicles.");
        if (favorites.isReadOnly()) throw new ValidationException(favorites.getReadOnlyMessage());
        Vehicle vehicle = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        try {
            favorites.set(customerId, vehicle.getVehicleId(), favorite);
        } catch (IOException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    // ===== Maintenance history =====

    /** True when an admin is logged in; maintenance figures are only shown to admins. */
    private boolean isAdmin() {
        return auth != null && auth.adminOnly("view maintenance") == null;
    }

    /**
     * Saves a service or repair done on a vehicle. With {@code takeOutOfService} the vehicle is also
     * switched out of service (so it can't be picked up) until an admin switches it back on in Edit.
     * The record is kept even if the vehicle is deleted later, so past costs still count in Reports.
     *
     * @param nextServiceDate when the next service is due, or null if none is planned
     */
    public MaintenanceRecord addMaintenanceRecord(String vehicleId, java.time.LocalDate serviceDate, String description,
                                                  java.math.BigDecimal cost, java.time.LocalDate nextServiceDate,
                                                  boolean takeOutOfService)
            throws VehicleNotFoundException, ValidationException {
        requireAdmin("record maintenance");
        Vehicle vehicle = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        if (maintenance.isReadOnly()) throw new ValidationException(maintenance.getReadOnlyMessage());
        if (takeOutOfService) requireWritable();

        String work = description == null ? "" : description.trim();
        if (serviceDate == null) throw new ValidationException("Service date is required");
        if (serviceDate.isAfter(java.time.LocalDate.now())) {
            throw new ValidationException("Service date can't be in the future");
        }
        if (work.isEmpty()) throw new ValidationException("Describe the work done, e.g. \"Oil change\"");
        if (work.length() > MAX_MAINTENANCE_DESCRIPTION) {
            throw new ValidationException("Work done can be at most " + MAX_MAINTENANCE_DESCRIPTION + " characters");
        }
        if (cost == null || cost.signum() < 0) throw new ValidationException("Cost can't be negative");
        if (cost.compareTo(MAX_MAINTENANCE_COST) > 0) {
            throw new ValidationException("Cost can be at most \u20B1" + String.format("%,.2f", MAX_MAINTENANCE_COST));
        }
        if (nextServiceDate != null && !nextServiceDate.isAfter(serviceDate)) {
            throw new ValidationException("Next service date must be after the service date");
        }

        MaintenanceRecord record = new MaintenanceRecord(maintenance.nextId(), vehicle.getVehicleId(), serviceDate,
                work, cost, nextServiceDate);
        try {
            maintenance.add(record);
        } catch (IOException e) {
            throw new ValidationException(e.getMessage());
        }
        audit.record(AuditLog.MAINTENANCE_ADDED, null, record.getRecordId() + " " + vehicle.getVehicleId() + ": "
                + work + ", \u20B1" + record.getCost().toPlainString() + " on " + serviceDate
                + (nextServiceDate == null ? "" : ", next service " + nextServiceDate));

        if (takeOutOfService && vehicle.isAvailable()) {
            vehicle.setAvailable(false);
            try {
                saveOrUndo(() -> vehicle.setAvailable(true));
            } catch (ValidationException e) {
                throw new ValidationException("The maintenance record was saved, but the vehicle could not be taken out of service: "
                        + e.getMessage());
            }
            logIfSaved(AuditLog.VEHICLE_EDITED, vehicle.getVehicleId() + ": in service true -> false (maintenance "
                    + record.getRecordId() + ")");
        }
        return record;
    }

    /** One vehicle's maintenance records, newest service first. Empty unless an admin is logged in. */
    public List<MaintenanceRecord> getMaintenanceRecords(String vehicleId) {
        return getAllMaintenanceRecords().stream()
                .filter(r -> r.getVehicleId().equals(vehicleId))
                .collect(Collectors.toList());
    }

    /** Every maintenance record (deleted vehicles' too), newest service first. Empty unless an admin is logged in. */
    public List<MaintenanceRecord> getAllMaintenanceRecords() {
        if (!isAdmin()) return new ArrayList<>();
        List<MaintenanceRecord> all = maintenance.all();
        all.sort(java.util.Comparator.comparing(MaintenanceRecord::getServiceDate)
                .thenComparing(MaintenanceRecord::getRecordId).reversed());
        return all;
    }

    /** When the vehicle's next service is due, from its latest record; empty if none is planned. */
    public Optional<java.time.LocalDate> getNextServiceDate(String vehicleId) {
        List<MaintenanceRecord> records = getMaintenanceRecords(vehicleId);
        return records.isEmpty() ? Optional.empty() : Optional.ofNullable(records.get(0).getNextServiceDate());
    }

    /** Vehicles whose next service date is on or before {@code today}, most overdue first. Empty unless an admin is logged in. */
    public List<Vehicle> getVehiclesDueForService(java.time.LocalDate today) {
        // records come newest first, so the first one seen per vehicle is its latest
        Map<String, MaintenanceRecord> latest = new HashMap<>();
        for (MaintenanceRecord r : getAllMaintenanceRecords()) latest.putIfAbsent(r.getVehicleId(), r);
        Map<String, java.time.LocalDate> due = new HashMap<>();
        for (MaintenanceRecord r : latest.values()) {
            if (r.getNextServiceDate() != null && !r.getNextServiceDate().isAfter(today)) {
                due.put(r.getVehicleId(), r.getNextServiceDate());
            }
        }
        return vehicles.stream()
                .filter(v -> due.containsKey(v.getVehicleId()))
                .sorted(java.util.Comparator.comparing((Vehicle v) -> due.get(v.getVehicleId()))
                        .thenComparing(Vehicle::getVehicleId))
                .map(Vehicle::copy)
                .collect(Collectors.toList());
    }

    /** Deletes images/name, but only if it really is inside images/ (so "../data/accounts.txt" is left alone). */
    private void deleteInsideImages(String name) {
        try {
            File dir = imagesDir.getCanonicalFile();
            File target = new File(imagesDir, name).getCanonicalFile();
            if (dir.equals(target.getParentFile())) {
                target.delete();
            }
        } catch (IOException e) {
            System.err.println("Could not remove photo " + name + ": " + e.getMessage());
        }
    }

    /** A bare file name such as "VEH003.png": no folders, no "..". */
    static boolean isPlainFileName(String name) {
        return !name.contains("/") && !name.contains("\\") && !name.contains(":")
                && !name.equals(".") && !name.equals("..");
    }

    /** True when another vehicle (not {@code self}) has this plate; case and spaces around it don't matter. */
    private boolean plateTaken(String plate, Vehicle self) {
        String wanted = plate.trim();
        return vehicles.stream().anyMatch(v -> v != self && v.getLicensePlate().trim().equalsIgnoreCase(wanted));
    }

    /** Marks the vehicle in service or under maintenance, and saves. */
    public void setVehicleInService(String vehicleId, boolean inService) throws VehicleNotFoundException, ValidationException {
        requireAdmin("take vehicles in or out of service");
        Vehicle vehicle = find(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found: " + vehicleId));
        requireWritable();
        if (vehicle.isAvailable() != inService) {
            vehicle.setAvailable(inService);
            saveOrUndo(() -> vehicle.setAvailable(!inService));
            logIfSaved(AuditLog.VEHICLE_EDITED, vehicleId + ": in service " + !inService + " -> " + inService);
        }
    }
}