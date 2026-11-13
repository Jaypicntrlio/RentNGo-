package service;

import model.Booking;
import model.Car;
import model.Money;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the second safety net: a data file that changed on disk after it was loaded
 * is never overwritten. Two DataStores on one folder stand in for two copies of RentNGo.
 */
class DataChangedOnDiskTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    @AfterEach
    void resetListener() {
        TsvFile.setChangedListener(null);
    }

    @Test
    void secondCopyCannotBookTheSameCarAndKeepsFirstCopysBooking() throws Exception {
        DataStore first = Stores.asAdmin(dataDir);
        DataStore second = Stores.asAdmin(dataDir); // started before first saves

        Booking firstBooking = first.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(3));

        BookingException e = assertThrows(BookingException.class, () ->
                second.getBookingService().createBooking("CUS002", "VEH001", TODAY.plusDays(1), TODAY.plusDays(3)));
        assertEquals(TsvFile.CHANGED_ON_DISK, e.getMessage());
        assertTrue(second.getBookingService().getAllBookings().isEmpty(), "the refused booking is undone");
        assertTrue(second.getBookingService().isReadOnly(), "the second copy stops saving bookings");

        String saved = Files.readString(dataDir.resolve("bookings.txt"));
        assertTrue(saved.contains(firstBooking.getBookingId() + "\tCUS001\tVEH001"), saved);
        assertFalse(saved.contains("CUS002"), "the first copy's booking must not be erased or doubled");

        DataStore restarted = Stores.asAdmin(dataDir);
        assertEquals(1, restarted.getBookingService().getAllBookings().size());
    }

    @Test
    void ownSavesKeepWorking() throws Exception {
        DataStore store = Stores.asAdmin(dataDir);
        BookingService bookings = store.getBookingService();
        bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2));
        bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
        Booking third = bookings.createBooking("CUS002", "VEH003", TODAY.plusDays(1), TODAY.plusDays(2));
        bookings.cancelBooking(third.getBookingId());
        assertFalse(bookings.isReadOnly());
        assertEquals(3, Stores.asAdmin(dataDir).getBookingService().getAllBookings().size());
    }

    @Test
    void accountsEditedByAnotherProgramAreNotOverwritten() throws Exception {
        AuthService auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        Path file = dataDir.resolve("accounts.txt");
        String edited = Files.readString(file) + "# edited in Notepad\n";
        Files.writeString(file, edited, StandardCharsets.UTF_8);

        ValidationException e = assertThrows(ValidationException.class, () ->
                auth.registerCustomer("maria", "secret12", "Maria Santos", "m@example.com", "", "N01-23-456789"));
        assertEquals(TsvFile.CHANGED_ON_DISK, e.getMessage());
        assertEquals(edited, Files.readString(file), "the other program's version is kept");
        assertTrue(auth.getAllCustomers().stream().noneMatch(c -> c.getUsername().equals("maria")), "the refused account is undone");

        // later changes are refused with the same message instead of overwriting
        auth.login("admin", "admin123");
        ValidationException again = assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS001"));
        assertEquals(TsvFile.CHANGED_ON_DISK, again.getMessage());
    }

    @Test
    void refusedBackgroundSaveIsReportedOnce() throws Exception {
        List<String> reported = new ArrayList<>();
        TsvFile.setChangedListener(reported::add);

        DataStore first = Stores.asAdmin(dataDir);
        DataStore second = Stores.asAdmin(dataDir);
        first.getVehicleService().addVehicle(new Car("VEH100", "Toyota", "Vios", 2024, Money.of(1800), "NEW-0001", 4, "Gasoline"));
        String afterFirst = Files.readString(dataDir.resolve("vehicles.txt"));

        // the refusal comes back as an error and the change is undone, so nothing goes to the listener
        ValidationException refused = assertThrows(ValidationException.class,
                () -> second.getVehicleService().setVehicleInService("VEH002", false));
        assertEquals(TsvFile.CHANGED_ON_DISK, refused.getMessage());
        assertTrue(second.getVehicleService().getVehicleById("VEH002").orElseThrow().isAvailable());
        assertEquals(List.of(), reported);
        assertEquals(afterFirst, Files.readString(dataDir.resolve("vehicles.txt")), "vehicles.txt keeps the first copy's change");
        assertThrows(ValidationException.class, () ->
                second.getVehicleService().addVehicle(new Car("VEH101", "Honda", "City", 2024, Money.of(1900), "NEW-0002", 4, "Gasoline")));
    }

    @Test
    void writeRefusesWhenFileChangedSinceLoad() throws Exception {
        Path file = dataDir.resolve("sample.txt");
        TsvFile.Stamp stamp = TsvFile.write(file.toFile(), "sample", List.of(), List.<String[]>of(new String[]{"a"}), null);
        stamp = TsvFile.write(file.toFile(), "sample", List.of(), List.<String[]>of(new String[]{"b"}), stamp);

        Files.writeString(file, "# sample\nchanged elsewhere\n");
        TsvFile.Stamp stale = stamp;
        assertThrows(TsvFile.DataChangedException.class, () ->
                TsvFile.write(file.toFile(), "sample", List.of(), List.<String[]>of(new String[]{"c"}), stale));
        assertEquals("# sample\nchanged elsewhere\n", Files.readString(file));

        // a file that appeared after we saw it missing is not overwritten either
        Path created = dataDir.resolve("created.txt");
        TsvFile.Stamp missing = TsvFile.Stamp.of(created.toFile());
        Files.writeString(created, "someone else\n");
        assertThrows(TsvFile.DataChangedException.class, () ->
                TsvFile.write(created.toFile(), "x", List.of(), List.of(), missing));
    }
}
