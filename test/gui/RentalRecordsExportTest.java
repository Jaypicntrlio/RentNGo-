package gui;

import model.Customer;
import model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.DataStore;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Rental Records "Export CSV" output goes through CsvWriter: quotes and formulas are made safe. */
class RentalRecordsExportTest {

    @TempDir
    Path dir;

    @Test
    void exportedRecordsAreEscaped() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display to build the dialog");
        DataStore store = new DataStore(dir.resolve("data").toFile(), null);
        User admin = store.getAuthService().login("admin", "admin123");
        String formulaName = "=HYPERLINK(\"http://evil.example\",\"Click\")";
        Customer evil = store.getAuthService().addCustomer(formulaName, "", "0917-000-0001", "N02-00-000001");
        Customer quoted = store.getAuthService().addCustomer("Juan \"JR\" Cruz, Sr.", "", "0917-000-0002", "N02-00-000002");
        LocalDate t = LocalDate.now();
        store.getBookingService().createBooking(evil.getUserId(), "VEH001", t.plusDays(1), t.plusDays(3));
        store.getBookingService().createBooking(quoted.getUserId(), "VEH002", t.plusDays(1), t.plusDays(2));

        File file = dir.resolve("records.csv").toFile();
        SwingUtilities.invokeAndWait(() -> {
            RentalRecordsDialog dialog = new RentalRecordsDialog(null, admin, store.getBookingService(),
                    store.getVehicleService(), store.getAuthService());
            try {
                dialog.exportTo(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                dialog.dispose();
            }
        });

        byte[] bytes = Files.readAllBytes(file.toPath());
        assertEquals((byte) 0xEF, bytes[0], "UTF-8 BOM");
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<List<String>> rows = service.CsvWriterTest.parse(text);

        assertEquals(List.of("Rental ID", "Customer", "Vehicle", "Start Date", "End Date", "Total Amount", "Status"), rows.get(0));
        assertEquals("'" + formulaName, rows.get(1).get(1), "the formula is turned into text");
        assertEquals("Juan \"JR\" Cruz, Sr.", rows.get(2).get(1), "quotes and the comma survive");
        assertEquals("₱5,000.00", rows.get(1).get(5));
        assertEquals(3, rows.size());
    }
}
