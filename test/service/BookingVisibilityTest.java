package service;

import model.Admin;
import model.Booking;
import model.Customer;
import model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** JUnit 5 tests for BookingService.getBookingsVisibleTo(): customers only ever see their own bookings. */
class BookingVisibilityTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;
    private Booking johnFirst;
    private Booking johnSecond;
    private Booking janeBooking;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        auth.login("admin", "admin123"); // to create the bookings below

        // demo accounts: CUS001 = john, CUS002 = jane, ADM001 = admin
        johnFirst = bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(3));
        johnSecond = bookings.createBooking("CUS001", "VEH004", TODAY.plusDays(5), TODAY.plusDays(6));
        janeBooking = bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(1), TODAY.plusDays(2));
    }

    private User user(String username, String password) throws Exception {
        return auth.login(username, password);
    }

    private static Set<String> ids(List<Booking> list) {
        return list.stream().map(Booking::getBookingId).collect(Collectors.toSet());
    }

    @Test
    void adminSeesEveryBooking() throws Exception {
        User admin = user("admin", "admin123");
        assertInstanceOf(Admin.class, admin);
        assertEquals(Set.of(johnFirst.getBookingId(), johnSecond.getBookingId(), janeBooking.getBookingId()),
                ids(bookings.getBookingsVisibleTo(admin)));
    }

    @Test
    void customerSeesOnlyOwnBookings() throws Exception {
        User john = user("john", "john123");
        assertEquals(Set.of(johnFirst.getBookingId(), johnSecond.getBookingId()), ids(bookings.getBookingsVisibleTo(john)));

        User jane = user("jane", "jane123");
        List<Booking> janes = bookings.getBookingsVisibleTo(jane);
        assertEquals(Set.of(janeBooking.getBookingId()), ids(janes));
        assertTrue(janes.stream().allMatch(b -> b.getCustomerId().equals("CUS002")), "no other customer's booking may leak");
    }

    @Test
    void customerWithNoBookingsSeesNothing() throws Exception {
        Customer newbie = auth.registerCustomer("newbie", "secret12", "New Person", "new@example.com", "", "N05-11-223344");
        assertTrue(bookings.getBookingsVisibleTo(newbie).isEmpty());
    }

    @Test
    void noUserSeesNothing() {
        assertTrue(bookings.getBookingsVisibleTo(null).isEmpty());
    }

    @Test
    void customerWithoutIdSeesNothing() {
        Customer broken = new Customer(null, "ghost", "x", "Ghost", "g@example.com", "", "L-0");
        assertTrue(bookings.getBookingsVisibleTo(broken).isEmpty());
    }

    @Test
    void cancelledOwnBookingsAreStillVisible() throws Exception {
        bookings.cancelBooking(johnSecond.getBookingId());
        assertEquals(2, bookings.getBookingsVisibleTo(user("john", "john123")).size());
    }

    @Test
    void returnedListCannotChangeTheService() throws Exception {
        User admin = user("admin", "admin123");
        bookings.getBookingsVisibleTo(admin).clear();
        bookings.getBookingsVisibleTo(user("john", "john123")).clear();
        user("admin", "admin123");
        assertEquals(3, bookings.getAllBookings().size());
    }
}
