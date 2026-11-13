package service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Safe CSV output for every export in the app. Each value is quoted with inner quotes doubled, and
 * a value that a spreadsheet would run as a formula gets a leading apostrophe. Files are written as
 * UTF-8 with a byte-order mark so Excel shows ₱ correctly, with CRLF line endings.
 */
public final class CsvWriter {

    /** Byte-order mark: tells Excel the file is UTF-8. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String LINE_END = "\r\n";

    private CsvWriter() {}

    /**
     * One CSV field: wrapped in quotes with every {@code "} doubled. A value starting with
     * {@code = + - @}, a tab or a carriage return is prefixed with {@code '}, so spreadsheets
     * show it as text instead of running it as a formula. Null is written as an empty field.
     */
    public static String escape(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** A whole line of escaped fields separated by commas (no line ending). Values are turned into text first. */
    public static String row(List<?> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            Object v = values.get(i);
            sb.append(escape(v == null ? null : v.toString()));
        }
        return sb.toString();
    }

    /** Writes the header and rows as UTF-8 with a BOM, replacing the file. */
    public static void write(File file, List<?> header, List<? extends List<?>> rows) throws IOException {
        StringBuilder sb = new StringBuilder(row(header)).append(LINE_END);
        for (List<?> r : rows) {
            sb.append(row(r)).append(LINE_END);
        }
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(UTF8_BOM);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
