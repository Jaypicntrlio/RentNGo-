package service;

import model.Car;
import model.Money;
import model.exceptions.ValidationException;
import model.exceptions.VehicleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for customers' favorite vehicles (feature 1). */
class FavoritesTest {

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;

    @BeforeEach
    void setUp() throws Exception {
        open();
        auth.login("john", "john123");
    }

    /** Opens the services on dataDir, like starting the app again. */
    private void open() {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
    }

    @Test
    void starsSurviveLoggingOutAndRestarting() throws Exception {
        vehicles.setFavorite("VEH003", true);
        vehicles.setFavorite("VEH001", true);
        assertEquals(List.of("VEH003", "VEH001"), List.copyOf(vehicles.getFavoriteVehicleIds()));

        auth.logout();
        open();
        auth.login("john", "john123");
        assertEquals(Set.of("VEH003", "VEH001"), vehicles.getFavoriteVehicleIds());
        assertTrue(vehicles.isFavorite("VEH003"));
        assertFalse(vehicles.isFavorite("VEH002"));
    }

    @Test
    void unstarringRemovesIt() throws Exception {
        vehicles.setFavorite("VEH002", true);
        vehicles.setFavorite("VEH002", false);
        vehicles.setFavorite("VEH002", false); // unstarring twice is fine
        assertTrue(vehicles.getFavoriteVehicleIds().isEmpty());
    }

    @Test
    void eachCustomerHasTheirOwnFavorites() throws Exception {
        vehicles.setFavorite("VEH004", true);
        auth.logout();
        auth.login("jane", "jane123");
        assertTrue(vehicles.getFavoriteVehicleIds().isEmpty(), "Jane doesn't see John's stars");
        vehicles.setFavorite("VEH005", true);
        assertEquals(Set.of("VEH005"), vehicles.getFavoriteVehicleIds());
    }

    @Test
    void onlyCustomersCanStar() throws Exception {
        auth.logout();
        assertThrows(ValidationException.class, () -> vehicles.setFavorite("VEH001", true));
        auth.login("admin", "admin123");
        assertThrows(ValidationException.class, () -> vehicles.setFavorite("VEH001", true));
        assertTrue(vehicles.getFavoriteVehicleIds().isEmpty());
    }

    @Test
    void unknownVehicleIsRefused() {
        assertThrows(VehicleNotFoundException.class, () -> vehicles.setFavorite("NOPE", true));
    }

    @Test
    void deletedVehicleDropsOutOfFavorites() throws Exception {
        vehicles.setFavorite("VEH007", true);
        vehicles.setFavorite("VEH001", true);
        auth.logout();
        auth.login("admin", "admin123");
        vehicles.deleteVehicle("VEH007", bookings.getBookingsByVehicle("VEH007"));
        // a new vehicle that reuses the ID must not come back starred
        vehicles.addVehicle(new Car("VEH007", "Kia", "Rio", 2024, Money.of(1500), "NEW-0007", 4, "Gasoline"));

        auth.logout();
        auth.login("john", "john123");
        assertEquals(Set.of("VEH001"), vehicles.getFavoriteVehicleIds());
    }

    @Test
    void starForAVehicleMissingFromTheFileIsIgnored() throws Exception {
        vehicles.setFavorite("VEH001", true);
        File file = dataDir.resolve("favorites.txt").toFile();
        String johnId = auth.getCurrentUser().getUserId();
        Files.writeString(file.toPath(), Files.readString(file.toPath()) + johnId + "\tGONE99\n");

        open();
        auth.login("john", "john123");
        assertNull(vehicles.getLoadError());
        assertEquals(Set.of("VEH001"), vehicles.getFavoriteVehicleIds(), "no error, the missing vehicle is just left out");
    }
}
