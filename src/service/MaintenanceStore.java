package service;

import model.MaintenanceRecord;
import model.Money;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the maintenance records in data/maintenance.txt (one tab-separated line per record).
 * Only VehicleService uses it: the permission checks and rules live there, this class just
 * loads, numbers and saves the records the same way the other data files are handled.
 */
class MaintenanceStore {
    private static final String HEADER = "RentNGo maintenance - id\tvehicleId\tserviceDate\tdescription\tcost\tnextServiceDate";
    static final String ID_PREFIX = "MNT";

    private final File dataFile; // null = in memory only (tests)
    private final List<MaintenanceRecord> records = new ArrayList<>();
    private int counter = 1;
    private TsvFile.Stamp stamp;
    private boolean readOnly;
    private String readOnlyMessage;
    private String loadError;

    MaintenanceStore(File dataFile) {
        this.dataFile = dataFile;
        if (dataFile == null || !dataFile.isFile()) {
            stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
            return; // nothing saved yet: start empty, the file is created on the first record
        }
        try {
            load();
        } catch (IOException e) {
            readOnly = true;
            loadError = TsvFile.describeUnreadable(dataFile, e);
            readOnlyMessage = loadError;
        }
    }

    /** Loads every readable row; bad rows are skipped and reported, IDs are never reused. */
    private void load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(dataFile);
        stamp = contents.stamp;
        TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
        int lastUsed = TsvFile.readSequence(contents, ID_PREFIX, warnings);
        for (TsvFile.Row row : contents.rows) {
            String[] f = row.fields;
            lastUsed = Math.max(lastUsed, TsvFile.idNumber(f.length > 0 ? f[0] : "", ID_PREFIX));
            try {
                records.add(parse(f));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        counter = lastUsed + 1;
        loadError = warnings.summary();
    }

    private static MaintenanceRecord parse(String[] f) {
        TsvFile.requireFields(f, 6);
        if (TsvFile.idNumber(f[0], ID_PREFIX) == 0) {
            throw new IllegalArgumentException("bad maintenance ID '" + f[0] + "'");
        }
        if (f[1].isBlank()) throw new IllegalArgumentException("vehicle ID is required");
        LocalDate next = f[5].isEmpty() ? null : LocalDate.parse(f[5]);
        return new MaintenanceRecord(f[0], f[1], LocalDate.parse(f[2]), f[3], Money.parse(f[4]), next);
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

    /** All records, in the order they were added. The records themselves can't be changed. */
    List<MaintenanceRecord> all() {
        return new ArrayList<>(records);
    }

    /** The ID the next record will get, e.g. "MNT0007". */
    String nextId() {
        return ID_PREFIX + String.format("%04d", counter);
    }

    /** Adds and saves the record; if it can't be saved, nothing changes and the reason is thrown. */
    void add(MaintenanceRecord record) throws IOException {
        records.add(record);
        counter++;
        try {
            write();
        } catch (IOException e) {
            records.remove(record);
            counter--;
            throw e;
        }
    }

    private void write() throws IOException {
        if (dataFile == null) return;
        List<String[]> rows = new ArrayList<>();
        for (MaintenanceRecord r : records) {
            rows.add(new String[]{r.getRecordId(), r.getVehicleId(), r.getServiceDate().toString(),
                    r.getDescription(), r.getCost().toPlainString(),
                    r.getNextServiceDate() == null ? "" : r.getNextServiceDate().toString()});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, counter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another program wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save maintenance records: " + e.getMessage(), e);
        }
    }
}
