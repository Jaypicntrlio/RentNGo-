package service;

import model.PromoCode;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the promo codes in data/promocodes.txt (one tab-separated line per code). Only
 * BookingService uses it: the permission checks and rules live there, this class just loads
 * and saves the codes the same way the other data files are handled.
 */
class PromoCodeStore {
    private static final String HEADER = "RentNGo promo codes - code\tpercentOff\texpiryDate\tusesLeft\tid";
    static final String ID_PREFIX = "PRM";

    private final File dataFile; // null = in memory only (tests)
    private final List<PromoCode> codes = new ArrayList<>();
    /** The next ID number; IDs are never reused, even for deleted codes (saved as "#seq PRM n"). */
    private int counter = 1;
    private TsvFile.Stamp stamp;
    private boolean readOnly;
    private String readOnlyMessage;
    private String loadError;

    PromoCodeStore(File dataFile) {
        this.dataFile = dataFile;
        if (dataFile == null || !dataFile.isFile()) {
            stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
            return; // no codes yet: the file is created when the first code is added
        }
        try {
            load();
        } catch (IOException e) {
            readOnly = true;
            loadError = TsvFile.describeUnreadable(dataFile, e);
            readOnlyMessage = loadError;
        }
    }

    private void load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(dataFile);
        stamp = contents.stamp;
        TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
        int lastUsed = TsvFile.readSequence(contents, ID_PREFIX, warnings);
        List<String[]> withoutId = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (TsvFile.Row row : contents.rows) {
            String[] f = row.fields;
            lastUsed = Math.max(lastUsed, TsvFile.idNumber(f.length > 4 ? f[4] : "", ID_PREFIX));
            try {
                PromoCode code = parse(f, null);
                if (!names.add(code.getCode())) {
                    throw new IllegalArgumentException("code " + code.getCode() + " is listed twice");
                }
                if (f.length > 4 && TsvFile.idNumber(f[4], ID_PREFIX) > 0) {
                    codes.add(parse(f, f[4]));
                } else {
                    withoutId.add(f); // saved before codes had IDs: numbered below, after every ID in the file
                }
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        counter = lastUsed + 1;
        for (String[] f : withoutId) {
            codes.add(parse(f, nextId()));
            counter++;
        }
        loadError = warnings.summary();
    }

    private static PromoCode parse(String[] f, String id) {
        TsvFile.requireFields(f, 4);
        if (f[0].isBlank()) throw new IllegalArgumentException("code is required");
        int uses = Integer.parseInt(f[3]);
        if (uses < 0) throw new IllegalArgumentException("negative uses left " + uses);
        return new PromoCode(id, f[0], Integer.parseInt(f[1]), LocalDate.parse(f[2]), uses);
    }

    /** The ID the next new code gets, e.g. "PRM0004". */
    String nextId() {
        return ID_PREFIX + String.format("%04d", counter);
    }

    /** The stored code with this permanent ID, if it still exists. */
    Optional<PromoCode> findById(String id) {
        return id == null ? Optional.empty() : codes.stream().filter(c -> id.equals(c.getId())).findFirst();
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

    /** The stored code (not a copy), for changes made by BookingService; case doesn't matter. */
    Optional<PromoCode> find(String code) {
        String wanted = PromoCode.normalize(code);
        return codes.stream().filter(c -> c.getCode().equals(wanted)).findFirst();
    }

    /** Copies of every code, sorted by code. */
    List<PromoCode> all() {
        List<PromoCode> copies = new ArrayList<>();
        for (PromoCode c : codes) copies.add(c.copy());
        copies.sort(java.util.Comparator.comparing(PromoCode::getCode));
        return copies;
    }

    /** Adds a code that was given nextId(); the number is used up only if it is saved. */
    void add(PromoCode code) throws IOException {
        codes.add(code);
        counter++;
        try {
            write();
        } catch (IOException e) {
            codes.remove(code);
            counter--;
            throw e;
        }
    }

    void remove(PromoCode code) throws IOException {
        int index = codes.indexOf(code);
        codes.remove(index);
        try {
            write();
        } catch (IOException e) {
            codes.add(index, code);
            throw e;
        }
    }

    /** Changes a code's uses left and saves; if it can't be saved, the old number is put back. */
    void setUsesLeft(PromoCode code, int usesLeft) throws IOException {
        int before = code.getUsesLeft();
        code.setUsesLeft(usesLeft);
        try {
            write();
        } catch (IOException e) {
            code.setUsesLeft(before);
            throw e;
        }
    }

    private void write() throws IOException {
        if (dataFile == null) return;
        if (readOnly) throw new IOException(readOnlyMessage);
        List<String[]> rows = new ArrayList<>();
        for (PromoCode c : codes) {
            rows.add(new String[]{c.getCode(), String.valueOf(c.getPercentOff()), c.getExpiryDate().toString(),
                    String.valueOf(c.getUsesLeft()), c.getId()});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, counter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            // another program wrote the file: never overwrite it
            readOnly = true;
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save promo codes: " + e.getMessage(), e);
        }
    }
}
