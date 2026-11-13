package service;

import model.Booking;
import model.BookingStatus;
import model.Car;
import model.SUV;
import model.Truck;
import model.Vehicle;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for BookingService. Every test gets its own temporary data folder,
 * so the real data/ folder is never touched.
 */
class BookingServiceTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String CUSTOMER = "CUS001"; // demo customer created on first run

    @TempDir
    Path dataDir;

    private AuthService auth;
    private VehicleService vehicles;
    private BookingService bookings;

    @BeforeEach
    void setUp() throws Exception {
        startServices();
    }

    /** (Re)creates the services from the files in dataDir, like reopening the app. */
    private void startServices() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        auth.login("admin", "admin123"); // the services check who is calling
    }

    /** The booking's status now: bookings handed out by the service are copies, so read it again. */
    private BookingStatus statusOf(Booking b) {
        return bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus();
    }

    private static BigDecimal peso(String amount) {
        return new BigDecimal(amount);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, peso(expected).compareTo(actual), () -> "expected ₱" + expected + " but was ₱" + actual);
    }

    // ===== Overlapping bookings =====

    @Nested
    @DisplayName("Overlapping bookings")
    class Overlaps {

        @BeforeEach
        void bookDays10To15() throws Exception {
            bookings.createBooking(CUSTOMER, "VEH002", TODAY.plusDays(10), TODAY.plusDays(15));
        }

        @ParameterizedTest(name = "days {0}-{1} overlap days 10-15")
        @CsvSource({
                "8, 11",   // overlaps the start
                "14, 20",  // overlaps the end
                "11, 13",  // inside
                "9, 16",   // covers it
                "10, 15",  // same dates
        })
        void overlappingDatesAreRejected(int startOffset, int endOffset) {
            BookingException e = assertThrows(BookingException.class, () ->
                    bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(startOffset), TODAY.plusDays(endOffset)));
            assertTrue(e.getMessage().contains("already booked"), e.getMessage());
        }

        @Test
        void separateDatesAreAccepted() throws Exception {
            Booking before = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(2), TODAY.plusDays(5));
            Booking after = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(16), TODAY.plusDays(18));
            assertEquals(BookingStatus.CONFIRMED, before.getStatus());
            assertEquals(BookingStatus.CONFIRMED, after.getStatus());
        }

        @Test
        void sameDatesOnAnotherVehicleAreAccepted() throws Exception {
            assertDoesNotThrow(() -> bookings.createBooking("CUS002", "VEH001", TODAY.plusDays(10), TODAY.plusDays(15)));
        }

        @Test
        void cancelledBookingNoLongerBlocks() throws Exception {
            Booking second = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(20), TODAY.plusDays(22));
            bookings.cancelBooking(second.getBookingId());
            assertDoesNotThrow(() -> bookings.createBooking(CUSTOMER, "VEH002", TODAY.plusDays(20), TODAY.plusDays(22)));
        }
    }

    // ===== Cost calculation =====

    @Nested
    @DisplayName("Rental cost (3-day booking)")
    class Costs {

        private BigDecimal bookThreeDays(Vehicle vehicle) throws Exception {
            vehicles.addVehicle(vehicle);
            return bookings.createBooking(CUSTOMER, vehicle.getVehicleId(), TODAY.plusDays(1), TODAY.plusDays(4)).getTotalCost();
        }

        @Test
        void electricCarGets10PercentDiscount() throws Exception {
            // 2,000 x 3 days = 6,000, less 10%
            assertMoney("5400.00", bookThreeDays(new Car("VEH101", "BYD", "Dolphin", 2025, peso("2000"), "EV-0001", 4, "Electric")));
        }

        @Test
        void gasolineCarPaysTheBaseRate() throws Exception {
            assertMoney("6000.00", bookThreeDays(new Car("VEH102", "Toyota", "Vios", 2024, peso("2000"), "GAS-0001", 4, "Gasoline")));
        }

        @Test
        void fourWheelDriveSuvGets15PercentSurcharge() throws Exception {
            // 3,000 x 3 days = 9,000, plus 15%
            assertMoney("10350.00", bookThreeDays(new SUV("VEH103", "Toyota", "Fortuner", 2024, peso("3000"), "SUV-0001", true, 7)));
        }

        @Test
        void twoWheelDriveSuvPaysTheBaseRate() throws Exception {
            assertMoney("9000.00", bookThreeDays(new SUV("VEH104", "Nissan", "Terra", 2024, peso("3000"), "SUV-0002", false, 7)));
        }

        @Test
        void heavyTruckGets20PercentSurcharge() throws Exception {
            // 3,500 x 3 days = 10,500, plus 20% for a load capacity over 5 tons
            assertMoney("12600.00", bookThreeDays(new Truck("VEH105", "Isuzu", "Forward", 2023, peso("3500"), "TRK-0001", 6.0, true)));
        }

        @Test
        void fiveTonTruckIsNotHeavyLoad() throws Exception {
            assertMoney("10500.00", bookThreeDays(new Truck("VEH106", "Isuzu", "Elf", 2023, peso("3500"), "TRK-0002", 5.0, false)));
        }

        @Test
        void costIsRoundedToCentavos() throws Exception {
            // 1,234.56 x 3 = 3,703.68, less 10% = 3,333.312 -> 3,333.31
            BigDecimal cost = bookThreeDays(new Car("VEH107", "MG", "4", 2025, peso("1234.56"), "EV-0002", 4, "Electric"));
            assertMoney("3333.31", cost);
            assertEquals(2, cost.scale());
        }
    }

    // ===== Status transitions =====

    @Nested
    @DisplayName("Status transitions")
    class StatusTransitions {

        private Booking confirmed() throws Exception {
            return bookings.createBooking(CUSTOMER, "VEH001", TODAY, TODAY.plusDays(2));
        }

        @Test
        void confirmedCannotSkipStraightToCompleted() throws Exception {
            Booking b = confirmed();
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.COMPLETED));
            assertEquals(BookingStatus.CONFIRMED, statusOf(b), "a rejected change must leave the status alone");
        }

        @Test
        void confirmedCannotGoBackToPending() throws Exception {
            Booking b = confirmed();
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.PENDING));
        }

        @Test
        void activeRentalCannotBeCancelledOrUnconfirmed() throws Exception {
            Booking b = confirmed();
            bookings.markPickedUp(b.getBookingId());
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CANCELLED));
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), BookingStatus.CONFIRMED));
            assertEquals(BookingStatus.ACTIVE, statusOf(b));
        }

        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void completedIsFinal(BookingStatus target) throws Exception {
            Booking b = confirmed();
            bookings.markPickedUp(b.getBookingId());
            bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
            BookingException e = assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), target));
            assertTrue(e.getMessage().contains("final") || e.getMessage().contains("already returned"), e.getMessage());
            assertEquals(BookingStatus.COMPLETED, statusOf(b));
        }

        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void cancelledIsFinal(BookingStatus target) throws Exception {
            Booking b = bookings.createBooking(CUSTOMER, "VEH001", TODAY.plusDays(5), TODAY.plusDays(6));
            bookings.cancelBooking(b.getBookingId());
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(b.getBookingId(), target));
            assertEquals(BookingStatus.CANCELLED, statusOf(b));
        }

        @Test
        void unknownBookingIsRejected() {
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus("BKG9999", BookingStatus.ACTIVE));
        }

        @Test
        void allowedPathStillWorks() throws Exception {
            Booking b = confirmed();
            bookings.updateBookingStatus(b.getBookingId(), BookingStatus.ACTIVE);
            bookings.updateBookingStatus(b.getBookingId(), BookingStatus.COMPLETED);
            assertEquals(BookingStatus.COMPLETED, statusOf(b));
        }
    }

    // ===== Late fees =====

    @Nested
    @DisplayName("Late fees (1.5x the daily rate per late day)")
    class LateFees {

        @Test
        void onTimeOrEarlyReturnHasNoLateFee() throws Exception {
            Booking b = bookings.createBooking(CUSTOMER, "VEH001", TODAY, TODAY.plusDays(3));
            assertMoney("0", bookings.calculateLateFee(b, TODAY.plusDays(3)));
            assertMoney("0", bookings.calculateLateFee(b, TODAY.plusDays(1)));
        }

        @ParameterizedTest(name = "{0} late day(s) on a 2,500/day car = {1}")
        @CsvSource({
                "1, 3750.00",
                "2, 7500.00",
                "4, 15000.00",
        })
        void chargedPerLateDay(int lateDays, String expected) throws Exception {
            Booking b = bookings.createBooking(CUSTOMER, "VEH001", TODAY, TODAY.plusDays(2)); // Toyota Camry, 2,500/day
            assertMoney(expected, bookings.calculateLateFee(b, TODAY.plusDays(2 + lateDays)));
        }

        @Test
        void lateFeeUsesTheDailyRateWithoutTypeSurcharges() throws Exception {
            // VEH004 is a 4WD SUV at 3,500/day: the late fee is 1.5 x 3,500, not 1.15 x that
            Booking b = bookings.createBooking(CUSTOMER, "VEH004", TODAY, TODAY.plusDays(1));
            assertMoney("5250.00", bookings.calculateLateFee(b, TODAY.plusDays(2)));
        }

        @Test
        void lateReturnStoresTheFeeOnTheBooking() throws Exception {
            // An overdue rental (ended 2 days ago) can only exist by loading it from the saved file.
            File file = dataDir.resolve("bookings.txt").toFile();
            Files.writeString(file.toPath(), "# bookings\nBKG0001\t" + CUSTOMER + "\tVEH001\t" + TODAY.minusDays(5) + "\t"
                    + TODAY.minusDays(2) + "\t7500.00\tACTIVE\t\t0.00\t0.00\t0.00\t0.00\n");
            startServices();

            Booking b = bookings.returnBooking("BKG0001", TODAY, BigDecimal.ZERO);
            assertMoney("7500.00", b.getLateFee());          // 2 days x 1.5 x 2,500
            assertMoney("15000.00", b.getFinalAmount());     // 7,500 rental + 7,500 late fee
            assertEquals(BookingStatus.COMPLETED, statusOf(b));
        }
    }
}
