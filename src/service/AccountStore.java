package service;

import model.Admin;
import model.Customer;
import model.User;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes all accounts to data/accounts.txt, one tab-separated line per user:
 * role, id, username, password hash, full name, email, phone, license/admin level, registered date,
 * active, mustChangePassword (true/false; older files without them load as active / false).
 * The last-used ID numbers are saved too, so an ID is never handed out again.
 */
public class AccountStore {

    private static final String HEADER = "RentNGo accounts - role\tid\tusername\tpasswordHash\tfullName\temail\tphone\tlicenseOrLevel\tregistered\tactive\tmustChangePassword";

    private final File file;
    private final Map<String, Integer> sequences = new HashMap<>();
    private TsvFile.LoadWarnings warnings;
    /** The file as it was when last loaded or saved; a save is refused if it changed since. */
    private TsvFile.Stamp stamp;

    public AccountStore() {
        this(new File(ProjectPaths.dir("data"), "accounts.txt"));
    }

    public AccountStore(File file) {
        this.file = file;
        this.warnings = new TsvFile.LoadWarnings(file);
        this.stamp = TsvFile.stampOrNull(file);
    }

    public boolean exists() {
        return file.isFile();
    }

    public File getFile() {
        return file;
    }

    /** Last-used number for an ID prefix ("CUS", "ADM") as saved in the file, or 0. */
    public int getSequence(String prefix) {
        return sequences.getOrDefault(prefix, 0);
    }

    /** Lines skipped by the last load(), or an empty list before the first load. */
    public TsvFile.LoadWarnings getWarnings() {
        return warnings;
    }

    /**
     * Reads every account that can be read. A bad row or "#seq" line is skipped and reported in
     * getWarnings(); IOException is thrown only when the file can't be read at all.
     */
    public List<User> load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(file);
        warnings = new TsvFile.LoadWarnings(file);
        stamp = contents.stamp;
        sequences.clear();
        for (TsvFile.Row m : contents.meta) {
            if (m.fields.length == 0 || !"seq".equals(m.fields[0])) continue;
            try {
                TsvFile.requireFields(m.fields, 3);
                int value = Integer.parseInt(m.fields[2].trim());
                if (value < 0) throw new IllegalArgumentException("negative ID number " + value);
                sequences.put(m.fields[1], value);
            } catch (RuntimeException e) {
                warnings.skip(m, e);
            }
        }
        List<User> users = new ArrayList<>();
        for (TsvFile.Row row : contents.rows) {
            try {
                users.add(parseUser(row.fields));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        return users;
    }

    private static User parseUser(String[] f) {
        TsvFile.requireFields(f, 8);
        for (int i = 1; i <= 3; i++) {
            if (f[i].isBlank()) throw new IllegalArgumentException("ID, username and password hash are required");
        }
        User user;
        switch (f[0]) {
            case "ADMIN":
                user = new Admin(f[1], f[2], f[3], f[4], f[5], f[6], f[7]);
                break;
            case "CUSTOMER":
                Customer c = new Customer(f[1], f[2], f[3], f[4], f[5], f[6], f[7]);
                if (f.length > 8 && !f[8].isEmpty()) {
                    c.setRegisteredDate(LocalDate.parse(f[8]));
                }
                user = c;
                break;
            default:
                throw new IllegalArgumentException("unknown role '" + f[0] + "'");
        }
        user.setActive(parseFlag(f.length > 9 ? f[9] : "", true, "active"));
        user.setMustChangePassword(parseFlag(f.length > 10 ? f[10] : "", false, "mustChangePassword"));
        return user;
    }

    /** The flag columns were added later: missing or empty means the default. */
    private static boolean parseFlag(String value, boolean missing, String name) {
        String v = value.trim();
        if (v.isEmpty()) return missing;
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(name + " must be true or false, not '" + value + "'");
    }

    public void save(List<User> users, Map<String, Integer> idSequences) throws IOException {
        List<String[]> meta = new ArrayList<>();
        for (Map.Entry<String, Integer> e : idSequences.entrySet()) {
            meta.add(new String[]{"seq", e.getKey(), String.valueOf(e.getValue())});
        }
        List<String[]> rows = new ArrayList<>();
        for (User u : users) {
            String extra = u instanceof Customer ? ((Customer) u).getLicenseNumber()
                    : u instanceof Admin ? ((Admin) u).getAdminLevel() : "";
            String registered = u instanceof Customer && ((Customer) u).getRegisteredDate() != null
                    ? ((Customer) u).getRegisteredDate().toString() : "";
            rows.add(new String[]{u.getRole(), u.getUserId(), u.getUsername(), u.getPassword(),
                    u.getFullName(), u.getEmail(), u.getPhone(), extra, registered, String.valueOf(u.isActive()),
                    String.valueOf(u.isMustChangePassword())});
        }
        stamp = TsvFile.write(file, HEADER, meta, rows, stamp);
    }
}
