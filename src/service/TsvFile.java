package service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Small tab-separated text file used for all saved data (accounts, vehicles, bookings,
 * payments). One record per line; lines starting with "#" are comments or metadata.
 */
public final class TsvFile {

    /** Shown when a file changed on disk after RentNGo loaded it (another copy or program wrote it). */
    public static final String CHANGED_ON_DISK = "Data was changed by another program. Please restart RentNGo.";

    private static volatile Consumer<String> changedListener = System.err::println;

    private TsvFile() {}

    /**
     * Last-modified time and size of a data file, taken when it was loaded or last saved. Each service
     * keeps its own stamp, so it can tell when someone else wrote the file in the meantime.
     */
    public static final class Stamp {
        private final FileTime modified; // null when the file didn't exist
        private final long size;

        private Stamp(FileTime modified, long size) {
            this.modified = modified;
            this.size = size;
        }

        /** The file as it is on disk right now. */
        public static Stamp of(File file) throws IOException {
            try {
                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                return new Stamp(attrs.lastModifiedTime(), attrs.size());
            } catch (NoSuchFileException e) {
                return new Stamp(null, -1);
            }
        }

        boolean sameAs(Stamp other) {
            if (modified == null || other.modified == null) return modified == null && other.modified == null;
            return modified.equals(other.modified) && size == other.size;
        }
    }

    /** The file's stamp right now, or null (no check on the next save) if its details can't be read. */
    public static Stamp stampOrNull(File file) {
        try {
            return Stamp.of(file);
        } catch (IOException e) {
            return null;
        }
    }

    /** Thrown by write() when the file changed on disk since it was loaded; nothing is written. */
    public static final class DataChangedException extends IOException {
        private final File file;

        DataChangedException(File file) {
            super(CHANGED_ON_DISK);
            this.file = file;
        }

        public File getFile() {
            return file;
        }
    }

    /** Who is told when a save is refused because the file changed on disk (the app shows a dialog). */
    public static void setChangedListener(Consumer<String> listener) {
        changedListener = listener != null ? listener : System.err::println;
    }

    /** Passes a refused save on to the listener; services call this when they catch DataChangedException. */
    public static void reportChanged(DataChangedException e) {
        changedListener.accept(e.getMessage());
    }

    /** One line of a data file: its 1-based line number and its fields, already unescaped. */
    public static final class Row {
        public final int line;
        public final String[] fields;

        Row(int line, String[] fields) {
            this.line = line;
            this.fields = fields;
        }
    }

    /** Everything in a data file, read in one pass. */
    public static final class Contents {
        /** The file's last-modified time and size when it was read; pass it to write(). */
        public final Stamp stamp;
        /** The first line of the file (the header comment), or "" for an empty file. */
        public final String firstLine;
        /** Data rows (lines not starting with "#"). */
        public final List<Row> rows = new ArrayList<>();
        /** Metadata lines written as "#key\tvalue..." (e.g. "#seq\tBKG\t12"), without the "#". */
        public final List<Row> meta = new ArrayList<>();

        Contents(Stamp stamp, String firstLine) {
            this.stamp = stamp;
            this.firstLine = firstLine;
        }
    }

    /**
     * Reads the whole file. Throws IOException only when the file can't be read at all;
     * checking each row is up to the caller, so one bad row never rejects the whole file.
     */
    public static Contents read(File file) throws IOException {
        // stamp first: if the file changes while it is being read, the next save is refused
        Stamp stamp = Stamp.of(file);
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Contents contents = new Contents(stamp, lines.isEmpty() ? "" : lines.get(0));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("# ")) continue;
            if (line.startsWith("#")) {
                contents.meta.add(new Row(i + 1, split(line.substring(1))));
            } else {
                contents.rows.add(new Row(i + 1, split(line)));
            }
        }
        return contents;
    }

    /** Throws if the row has fewer fields than a valid record needs. */
    public static void requireFields(String[] fields, int count) {
        if (fields.length < count) {
            throw new IllegalArgumentException("expected " + count + " fields but found " + fields.length);
        }
    }

    /**
     * Collects "accounts.txt line 7 skipped: ..." messages while a file is loaded. When anything was
     * skipped, summary() keeps a ".corrupt-..." copy of the original file, because the skipped lines
     * are dropped the next time the file is saved.
     */
    public static final class LoadWarnings {
        private final File file;
        private final List<String> messages = new ArrayList<>();

        public LoadWarnings(File file) {
            this.file = file;
        }

        public void skip(Row row, Exception reason) {
            String detail = reason.getMessage() != null ? reason.getMessage() : reason.getClass().getSimpleName();
            if (reason instanceof NumberFormatException && detail.startsWith("For input string: ")) {
                detail = detail.substring("For input string: ".length()) + " is not a valid number";
            } else if (detail.startsWith("No enum constant ")) {
                detail = "unknown value " + detail.substring(detail.lastIndexOf('.') + 1);
            }
            messages.add(file.getName() + " line " + row.line + " skipped: " + detail);
        }

        public boolean isEmpty() {
            return messages.isEmpty();
        }

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }

        /** The warnings plus where the original file was backed up, or null when nothing was skipped. */
        public String summary() {
            if (messages.isEmpty()) return null;
            File backup = backupCorrupt(file);
            return String.join("\n", messages) + "\n"
                    + (backup != null ? "The original file was kept as " + backup.getName() + "." : "The original file could not be backed up.");
        }
    }

    /**
     * The last-used number saved as "#seq\tPREFIX\tN" (e.g. "#seq\tBKG\t12"), or 0 if there is none.
     * A damaged "#seq" line is skipped with a warning.
     */
    public static int readSequence(Contents contents, String prefix, LoadWarnings warnings) {
        int last = 0;
        for (Row m : contents.meta) {
            if (m.fields.length == 0 || !"seq".equals(m.fields[0])) continue;
            try {
                requireFields(m.fields, 3);
                if (!prefix.equals(m.fields[1])) continue;
                int value = Integer.parseInt(m.fields[2].trim());
                if (value < 0) throw new IllegalArgumentException("negative ID number " + value);
                last = Math.max(last, value);
            } catch (RuntimeException e) {
                warnings.skip(m, e);
            }
        }
        return last;
    }

    /** The "#seq" metadata line to save for a prefix's last-used number. */
    public static List<String[]> sequenceMeta(String prefix, int lastUsed) {
        List<String[]> meta = new ArrayList<>();
        meta.add(new String[]{"seq", prefix, String.valueOf(lastUsed)});
        return meta;
    }

    /** 12 for ("BKG0012", "BKG"); 0 when the ID isn't the prefix followed by digits. */
    public static int idNumber(String id, String prefix) {
        if (id == null || !id.matches(java.util.regex.Pattern.quote(prefix) + "\\d{1,9}")) return 0;
        return Integer.parseInt(id.substring(prefix.length()));
    }

    /** Message for a data file that exists but could not be read at all; it is left untouched. */
    public static String describeUnreadable(File file, IOException error) {
        return file.getName() + " could not be read (" + error.getMessage() + "). It was left unchanged, "
                + "and nothing will be saved to it until it can be read again. Fix or restore the file, then restart RentNGo.";
    }

    /**
     * Writes the header comment, optional metadata lines and rows. The file is written to a
     * temp file first, so a crash mid-save never leaves a half-written file behind.
     *
     * {@code loaded} is the stamp from when the caller last read or wrote the file. If the file has
     * changed on disk since then (another copy of RentNGo or another program wrote it), nothing is
     * written and DataChangedException is thrown, so the other changes aren't silently erased.
     * Returns the new stamp to keep for the next save.
     */
    public static Stamp write(File file, String header, List<String[]> meta, List<String[]> rows, Stamp loaded) throws IOException {
        if (loaded != null && !loaded.sameAs(Stamp.of(file))) {
            throw new DataChangedException(file);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# " + header);
        for (String[] m : meta) {
            lines.add("#" + join(m));
        }
        for (String[] row : rows) {
            lines.add(join(row));
        }
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return Stamp.of(file);
    }

    /**
     * Adds one row to the end of the file (creating it with a header comment if needed). Earlier
     * lines are never rewritten, so this suits logs that must keep their history.
     */
    public static synchronized void appendRow(File file, String header, String[] fields) throws IOException {
        file.getParentFile().mkdirs();
        StringBuilder text = new StringBuilder();
        if (!file.isFile() || file.length() == 0) {
            text.append("# ").append(header).append(System.lineSeparator());
        }
        text.append(join(fields)).append(System.lineSeparator());
        Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /**
     * Copies a file with unreadable lines to "name.corrupt-yyyyMMdd-HHmmss" next to it, so the
     * original content is kept when the file is next saved without those lines.
     * Returns the backup, or null if the copy failed.
     */
    public static File backupCorrupt(File file) {
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + stamp);
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            return null;
        }
    }

    private static String[] split(String line) {
        String[] f = line.split("\t", -1);
        for (int i = 0; i < f.length; i++) f[i] = unescape(f[i]);
        return f;
    }

    private static String join(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
