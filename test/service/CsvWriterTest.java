package service;

import model.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for CsvWriter: quotes, formulas, commas, newlines and the peso sign. */
public class CsvWriterTest {

    @TempDir
    Path dir;

    /** Minimal RFC 4180 reader, to check that what was written reads back as the same values. */
    public static List<List<String>> parse(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<>(); i++;
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static String readWithoutBom(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    // ===== Quotes =====

    @Test
    void quotesAreDoubledAndTheValueIsWrapped() {
        assertEquals("\"Juan \"\"JR\"\" Cruz\"", CsvWriter.escape("Juan \"JR\" Cruz"));
        assertEquals("\"\"\"\"", CsvWriter.escape("\""));
        assertEquals("\"Toyota\"", CsvWriter.escape("Toyota"));
        assertEquals("\"\"", CsvWriter.escape(null));
        assertEquals("\"\"", CsvWriter.escape(""));
    }

    @Test
    void quotedNameReadsBackUnchanged() {
        String name = "Maria \"Mia\" Santos";
        assertEquals(List.of(List.of(name, "ok")), parse(CsvWriter.row(List.of(name, "ok")) + "\r\n"));
    }

    // ===== Formulas =====

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://evil\",\"x\")", "=1+1", "+63 917", "-2+3", "@SUM(A1:A9)",
            "\t=1+1", "\r=1+1", "=cmd|' /C calc'!A0"})
    void valuesThatLookLikeFormulasGetAnApostrophe(String value) {
        String escaped = CsvWriter.escape(value);
        assertTrue(escaped.startsWith("\"'"), escaped);
        assertEquals("'" + value, parse(escaped + "\r\n").get(0).get(0), "only the apostrophe is added");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a=b", "Toyota Camry (ABC-1234)", " =1+1", "BKG0001", "2026-10-29", "₱5,000.00"})
    void ordinaryValuesAreNotPrefixed(String value) {
        assertEquals("\"" + value + "\"", CsvWriter.escape(value));
    }

    // ===== Commas and newlines =====

    @Test
    void commasStayInsideTheirField() {
        List<List<String>> rows = parse(CsvWriter.row(List.of("Cruz, Juan", "Makati, Metro Manila", "x")) + "\r\n");
        assertEquals(List.of(List.of("Cruz, Juan", "Makati, Metro Manila", "x")), rows);
    }

    @Test
    void newlinesStayInsideTheirField() throws Exception {
        File file = dir.resolve("records.csv").toFile();
        CsvWriter.write(file, List.of("ID", "Note"),
                List.of(List.of("BKG0001", "line one\nline two\r\nline three"), List.of("BKG0002", "plain")));

        List<List<String>> rows = parse(readWithoutBom(file));
        assertEquals(3, rows.size(), "header + 2 rows, the newlines don't start new rows");
        assertEquals("line one\nline two\r\nline three", rows.get(1).get(1));
        assertEquals(List.of("BKG0002", "plain"), rows.get(2));
    }

    // ===== Peso sign, BOM and file layout =====

    @Test
    void fileIsUtf8WithBomSoThePesoSignShowsInExcel() throws Exception {
        File file = dir.resolve("money.csv").toFile();
        CsvWriter.write(file, List.of("Total Amount"), List.of(List.of("₱5,000.00")));

        byte[] bytes = Files.readAllBytes(file.toPath());
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3), "UTF-8 byte-order mark");
        String text = readWithoutBom(file);
        assertEquals("\"Total Amount\"\r\n\"₱5,000.00\"\r\n", text, "CRLF line endings, peso sign intact");
    }

    @Test
    void writeReplacesTheFileAndTurnsValuesIntoText() throws Exception {
        File file = dir.resolve("out.csv").toFile();
        Files.writeString(file.toPath(), "old content that is much longer than the new one");
        CsvWriter.write(file, List.of("Status", "Days", "Missing"), List.of(Arrays.asList(BookingStatus.CONFIRMED, 3, null)));
        assertEquals(List.of(List.of("Status", "Days", "Missing"), List.of("CONFIRMED", "3", "")), parse(readWithoutBom(file)));
    }
}
