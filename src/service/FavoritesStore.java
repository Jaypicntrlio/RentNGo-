package service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each customer's favorite vehicles, saved in data/favorites.txt as one "customerId, vehicleId"
 * line per star. Only VehicleService uses it: the checks live there, this class loads and saves.
 */
class FavoritesStore {
    private static final String HEADER = "RentNGo favorites - customerId\tvehicleId";

    private final File dataFile; // null = in memory only (tests)
    private final Map<String, Set<String>> favorites = new LinkedHashMap<>();
    private TsvFile.Stamp stamp;
    private boolean readOnly;
    private String readOnlyMessage;
    private String loadError;

    FavoritesStore(File dataFile) {
        this.dataFile = dataFile;
        if (dataFile == null || !dataFile.isFile()) {
            stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
            return; // nobody has starred anything yet
        }
        try {
            TsvFile.Contents contents = TsvFile.read(dataFile);
            stamp = contents.stamp;
            TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
            for (TsvFile.Row row : contents.rows) {
                try {
                    TsvFile.requireFields(row.fields, 2);
                    if (row.fields[0].isBlank() || row.fields[1].isBlank()) {
                        throw new IllegalArgumentException("customer ID and vehicle ID are required");
                    }
                    favorites.computeIfAbsent(row.fields[0], k -> new LinkedHashSet<>()).add(row.fields[1]);
                } catch (RuntimeException e) {
                    warnings.skip(row, e);
                }
            }
            loadError = warnings.summary();
        } catch (IOException e) {
            readOnly = true;
            loadError = TsvFile.describeUnreadable(dataFile, e);
            readOnlyMessage = loadError;
        }
    }

    String getLoadError() {
        return loadError;
    }

    boolean isReadOnly() {
        return readOnly;
    }

    String getReadOnlyMessage() {
        return readOnlyMessage;
    }

    /** A copy of the customer's starred vehicle IDs, in the order they were starred. */
    Set<String> of(String customerId) {
        return new LinkedHashSet<>(favorites.getOrDefault(customerId, new LinkedHashSet<>()));
    }

    /** Stars or unstars a vehicle and saves; if it can't be saved, nothing changes. */
    void set(String customerId, String vehicleId, boolean favorite) throws IOException {
        Set<String> mine = favorites.computeIfAbsent(customerId, k -> new LinkedHashSet<>());
        boolean changed = favorite ? mine.add(vehicleId) : mine.remove(vehicleId);
        if (!changed) return;
        try {
            write();
        } catch (IOException e) {
            if (favorite) mine.remove(vehicleId); else mine.add(vehicleId);
            throw e;
        }
    }

    /** Unstars a deleted vehicle for every customer, so a new vehicle given its ID starts unstarred. */
    void removeVehicle(String vehicleId) throws IOException {
        boolean changed = false;
        for (Set<String> mine : favorites.values()) changed |= mine.remove(vehicleId);
        if (changed) write();
    }

    private void write() throws IOException {
        if (dataFile == null) return;
        if (readOnly) throw new IOException(readOnlyMessage);
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : favorites.entrySet()) {
            for (String vehicleId : e.getValue()) rows.add(new String[]{e.getKey(), vehicleId});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, new ArrayList<>(), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another program wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save favorites: " + e.getMessage(), e);
        }
    }
}
