package service;

import model.Booking;
import model.BookingStatus;
import model.Car;
import model.Customer;
import model.Money;
import model.SUV;
import model.User;
import model.Vehicle;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the permission checks inside the services: whatever a window calls, a customer
 * can only act on their own account and bookings, admin-only actions need an admin, and nothing
 * handed out by a service can be used to change stored data.
 */
class AccessControlTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private PaymentService payments;
    private VehicleService vehicles;
    private Booking janes;   // CUS002's booking, made by an admin
    private Booking johns;   // CUS001's booking, made by john himself

    @BeforeEach
    void setUp() throws Exception {
        DataStore store = Stores.asAdmin(dataDir);
        auth = store.getAuthService();
        bookings = store.getBookingService();
        payments = store.getPaymentService();
        vehicles = store.getVehicleService();
        janes = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(5), TODAY.plusDays(7));
        auth.login("john", "john123");
        johns = bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
    }

    private void asAdmin() throws Exception {
        auth.login("admin", "admin123");
    }

    private Booking stored(String bookingId) throws Exception {
        asAdmin();
        return bookings.getBookingById(bookingId).orElseThrow();
    }

    // ===== A customer acting on other people's data =====

    @Nested
    class CustomerLimits {

        @Test
        void canOnlyBookForThemselves() {
            BookingException e = assertThrows(BookingException.class,
                    () -> bookings.createBooking("CUS002", "VEH003", TODAY.plusDays(9), TODAY.plusDays(10)));
            assertEquals("You can only make bookings for your own account.", e.getMessage());
        }

        @Test
        void cannotCancelSomeoneElsesBookingAndItLooksNotFound() throws Exception {
            BookingException e = assertThrows(BookingException.class, () -> payments.cancelBooking(janes.getBookingId()));
            assertEquals("Booking not found: " + janes.getBookingId(), e.getMessage(), "doesn't reveal that the ID exists");
            assertThrows(BookingException.class, () -> bookings.cancelBooking(janes.getBookingId()));
            assertEquals(BookingStatus.CONFIRMED, stored(janes.getBookingId()).getStatus());
        }

        @Test
        void canCancelTheirOwnBooking() throws Exception {
            payments.cancelBooking(johns.getBookingId());
            assertEquals(BookingStatus.CANCELLED, stored(johns.getBookingId()).getStatus());
        }

        @Test
        void cannotRecordPaymentsPickUpsOrReturns() throws Exception {
            BigDecimal due = johns.getBalanceDue();
            ValidationException pay = assertThrows(ValidationException.class,
                    () -> payments.processPayment(johns.getBookingId(), "CUS001", due, "Card", due));
            assertEquals("Only an admin can record payments.", pay.getMessage());
            assertThrows(ValidationException.class,
                    () -> payments.completeReturn(johns.getBookingId(), TODAY, BigDecimal.ZERO, "Card", due));
            assertEquals("Only an admin can record pick-ups.",
                    assertThrows(BookingException.class, () -> bookings.markPickedUp(johns.getBookingId())).getMessage());
            assertThrows(BookingException.class, () -> bookings.returnBooking(johns.getBookingId(), TODAY, BigDecimal.ZERO));
            assertThrows(BookingException.class, () -> bookings.updateBookingStatus(johns.getBookingId(), BookingStatus.ACTIVE));

            Booking b = stored(johns.getBookingId());
            assertEquals(BookingStatus.CONFIRMED, b.getStatus());
            assertFalse(b.isPaid());
            assertTrue(payments.getAllPayments().isEmpty());
        }

        @Test
        void canEditOnlyTheirOwnDetails() throws Exception {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> auth.updateCustomer("CUS002", "Hacked", "", "000", "X-1"));
            assertEquals("You can only edit customer details for your own account.", e.getMessage());
            auth.updateCustomer("CUS001", "John Q. Doe", "john@email.com", "555-1111", "DL123456");
            asAdmin();
            assertEquals("Jane Smith", auth.getAllCustomers().stream().filter(c -> c.getUserId().equals("CUS002")).findFirst().orElseThrow().getFullName());
            assertEquals("John Q. Doe", auth.getAllCustomers().stream().filter(c -> c.getUserId().equals("CUS001")).findFirst().orElseThrow().getFullName());
        }

        @Test
        void cannotManageCustomerAccounts() {
            assertEquals("Only an admin can add customers.", assertThrows(ValidationException.class,
                    () -> auth.addCustomer("Fake Person", "", "000", "X-2")).getMessage());
            assertThrows(ValidationException.class, () -> auth.deactivateCustomer("CUS002", bookings));
            assertThrows(ValidationException.class, () -> auth.reactivateCustomer("CUS002"));
        }

        @Test
        void cannotChangeVehicles() throws Exception {
            String denied = "Only an admin can";
            for (ValidationException e : List.of(
                    assertThrows(ValidationException.class, () -> vehicles.addVehicle(new Car("VEH100", "Kia", "Rio", 2024, Money.of(1000), "NEW-1", 4, "Gas"))),
                    assertThrows(ValidationException.class, () -> vehicles.updateVehicle("VEH004", new SUV("VEH004", "Ford", "Explorer", 2023, Money.of(1), "JKL-3456", true, 7))),
                    assertThrows(ValidationException.class, () -> vehicles.deleteVehicle("VEH007", List.of())),
                    assertThrows(ValidationException.class, () -> vehicles.setVehicleInService("VEH003", false)),
                    assertThrows(ValidationException.class, () -> vehicles.setVehicleImage("VEH003", "x.png")))) {
                assertTrue(e.getMessage().startsWith(denied), e.getMessage());
            }
            assertEquals(7, vehicles.getAllVehicles().size());
            assertMoney("3500.00", vehicles.getVehicleById("VEH004").orElseThrow().getPricePerDay());
            assertTrue(vehicles.getVehicleById("VEH003").orElseThrow().isAvailable());
        }

        @Test
        void canStillChangeTheirOwnPassword() throws Exception {
            assertThrows(ValidationException.class, () -> auth.changePassword("CUS002", "jane123", "hijacked99"),
                    "not someone else's, even with their password");
            auth.changePassword("CUS001", "john123", "johnNew2026");
            assertNotNull(auth.login("john", "johnNew2026"));
        }
    }

    // ===== What a customer can read =====

    @Nested
    class CustomerReads {

        @Test
        void onlyTheirOwnBookingsAndPayments() throws Exception {
            assertEquals(List.of(johns.getBookingId()), bookings.getAllBookings().stream().map(Booking::getBookingId).toList());
            assertTrue(bookings.getBookingById(janes.getBookingId()).isEmpty());
            assertTrue(bookings.getBookingsByCustomer("CUS002").isEmpty());
            assertTrue(bookings.getBookingsByVehicle("VEH002").isEmpty());

            asAdmin();
            payments.processPayment(janes.getBookingId(), "CUS002", janes.getBalanceDue(), "Cash", janes.getBalanceDue());
            auth.login("john", "john123");
            assertTrue(payments.getAllPayments().isEmpty(), "jane's payment isn't visible to john");
            assertTrue(payments.getPaymentsByBookingId(janes.getBookingId()).isEmpty());
            assertTrue(payments.getPaymentsByCustomer("CUS002").isEmpty());
        }

        @Test
        void passingAnotherUserDoesntWidenTheView() throws Exception {
            asAdmin();
            User admin = auth.getCurrentUser();
            assertEquals(2, bookings.getBookingsVisibleTo(admin).size());

            auth.login("john", "john123");
            assertTrue(bookings.getBookingsVisibleTo(admin).isEmpty(), "an admin object from earlier is ignored");
            assertEquals(1, bookings.getBookingsVisibleTo(auth.getCurrentUser()).size());
        }

        @Test
        void accountsOnlyTheirOwnAndNeverWithPasswordHashes() throws Exception {
            List<User> users = auth.getAllUsers();
            assertEquals(1, users.size());
            assertEquals("CUS001", users.get(0).getUserId());
            assertEquals(List.of("CUS001"), auth.getAllCustomers().stream().map(Customer::getUserId).toList());

            asAdmin();
            assertEquals(2, auth.getAllCustomers().size(), "an admin sees both demo customers");
            assertEquals(3, auth.getAllUsers().size());
            assertTrue(auth.getAllUsers().stream().allMatch(u -> u.getPassword() == null), "no hashes, even for an admin");
            assertNull(auth.getCurrentUser().getPassword());
            assertNull(auth.login("admin", "admin123").getPassword());
        }

        @Test
        void auditLogIsForAdminsOnly() throws Exception {
            assertTrue(bookings.getAuditLog().readNewestFirst().isEmpty(), "a customer sees nothing");
            asAdmin();
            assertFalse(bookings.getAuditLog().readNewestFirst().isEmpty());
        }
    }

    // ===== Copies: nothing handed out can change stored data =====

    @Nested
    class Copies {

        @Test
        void changingAReturnedBookingChangesNothing() throws Exception {
            Booking mine = bookings.getBookingById(johns.getBookingId()).orElseThrow();
            mine.setAmountPaid(new BigDecimal("999999"));
            mine.setStatus(BookingStatus.COMPLETED);
            johns.setTotalCost(BigDecimal.ONE);
            bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(20), TODAY.plusDays(21)); // triggers a save

            Booking b = stored(johns.getBookingId());
            assertEquals(BookingStatus.CONFIRMED, b.getStatus());
            assertFalse(b.isPaid());
            assertMoney("5000.00", b.getTotalCost());
            assertFalse(Files.readString(dataDir.resolve("bookings.txt")).contains("999999"));
        }

        @Test
        void changingAReturnedVehicleOrUserChangesNothing() throws Exception {
            vehicles.getVehicleById("VEH001").orElseThrow().setPricePerDay(Money.of(1));
            vehicles.getAllVehicles().forEach(v -> v.setAvailable(false));
            assertMoney("2500.00", vehicles.getVehicleById("VEH001").orElseThrow().getPricePerDay());
            assertEquals(7, vehicles.getInServiceVehicles().size());

            asAdmin();
            User me = auth.getCurrentUser();
            me.setActive(false);
            me.setPassword("x");
            auth.getAllCustomers().forEach(c -> c.setActive(false));
            assertTrue(auth.getAllCustomers().stream().allMatch(User::isActive));
            assertNotNull(auth.login("admin", "admin123"));
        }

        @Test
        void theObjectPassedToAddVehicleIsNotKept() throws Exception {
            asAdmin();
            Car car = new Car("VEH100", "Kia", "Rio", 2024, Money.of(1500), "NEW-1", 4, "Gasoline");
            vehicles.addVehicle(car);
            car.setPricePerDay(Money.of(1));
            car.setLicensePlate("CHANGED");
            Vehicle stored = vehicles.getVehicleById("VEH100").orElseThrow();
            assertMoney("1500.00", stored.getPricePerDay());
            assertEquals("NEW-1", stored.getLicensePlate());
        }

        @Test
        void windowsCantSaveOrForgeAuditLines() throws Exception {
            assertFalse(Modifier.isPublic(BookingService.class.getDeclaredMethod("save").getModifiers()));
            assertFalse(Modifier.isPublic(AuditLog.class.getDeclaredMethod("record", String.class, String.class, String.class).getModifiers()));
            assertFalse(Modifier.isPublic(AuditLog.class.getDeclaredMethod("recordAs", String.class, String.class, String.class, String.class).getModifiers()));
        }
    }

    // ===== Nobody logged in, or a password change still required =====

    @Nested
    class NotFullyLoggedIn {

        @Test
        void nobodyLoggedInCanDoNothingAndSeesNothing() {
            auth.logout();
            assertEquals("Please log in first.", assertThrows(BookingException.class,
                    () -> bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(9), TODAY.plusDays(10))).getMessage());
            assertThrows(BookingException.class, () -> bookings.cancelBooking(johns.getBookingId()));
            assertThrows(ValidationException.class, () -> vehicles.setVehicleInService("VEH003", false));
            assertTrue(bookings.getAllBookings().isEmpty());
            assertTrue(payments.getAllPayments().isEmpty());
            assertTrue(auth.getAllUsers().isEmpty());
            assertTrue(bookings.getAuditLog().readNewestFirst().isEmpty());
        }

        @Test
        void aRequiredPasswordChangeBlocksEverythingElse() throws Exception {
            asAdmin();
            Customer pedro = auth.addCustomer("Pedro Cruz", "", "0917-000-0001", "N02-00-000001");
            String startPassword = auth.takeTemporaryPassword(pedro.getUserId());
            auth.login(pedro.getUsername(), startPassword);

            assertEquals("Please choose a new password first.", assertThrows(BookingException.class,
                    () -> bookings.createBooking(pedro.getUserId(), "VEH003", TODAY.plusDays(9), TODAY.plusDays(10))).getMessage());
            assertThrows(ValidationException.class, () -> auth.updateCustomer(pedro.getUserId(), "P", "", "1", "N02-00-000001"));
            assertTrue(bookings.getAllBookings().isEmpty());
            assertTrue(auth.getAllUsers().isEmpty());

            auth.changePassword(pedro.getUserId(), startPassword, "pedroPass77");
            assertNotNull(bookings.createBooking(pedro.getUserId(), "VEH003", TODAY.plusDays(9), TODAY.plusDays(10)),
                    "allowed once the password is changed");
        }
    }

    // ===== Admins keep full access =====

    @Test
    void adminCanStillDoEverything() throws Exception {
        asAdmin();
        Booking b = bookings.createBooking("CUS002", "VEH003", TODAY, TODAY.plusDays(1));
        payments.processPayment(b.getBookingId(), "CUS002", b.getBalanceDue(), "Cash", b.getBalanceDue());
        bookings.markPickedUp(b.getBookingId());
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        payments.cancelBooking(janes.getBookingId());
        vehicles.setVehicleInService("VEH006", false);
        auth.updateCustomer("CUS002", "Jane Smith", "jane@email.com", "555-2222", "DL789012");
        assertEquals(3, bookings.getAllBookings().size());
        assertEquals(BookingStatus.COMPLETED, bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus());
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
