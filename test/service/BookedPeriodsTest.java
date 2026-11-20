package service;

import model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The booked periods behind the availability calendar: same days as booking, and private. */
class BookedPeriodsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private AuthService auth;
    private BookingService bookings;

    @BeforeEach
    void setUp() throws Exception {
        open();
    }

    private void open() throws Exception {
        auth = new AuthService(new AccountStore(dataDir.resolve("accounts.txt").toFile()));
        VehicleService vehicles = new VehicleService(dataDir.resolve("vehicles.txt").toFile(), null);
        bookings = new BookingService(vehicles, auth, dataDir.resolve("bookings.txt").toFile());
        auth.login("admin", "admin123");
    }

    private Booking book(String customer, int from, int to) throws Exception {
        return bookings.createBooking(customer, "VEH001", TODAY.plusDays(from), TODAY.plusDays(to));
    }

    @Test
    void calendarAgreesWithBookingForEveryRange() throws Exception {
        book("CUS001", 3, 6);                   // the 3rd to the 6th
        book("CUS002", 6, 8);                   // same-day handover on the 6th
        bookings.cancelBooking(book("CUS001", 10, 12).getBookingId()); // cancelled: never blocks
        book("CUS002", 15, 20);
        Booking done = book("CUS001", 0, 1);    // returned: never blocks
        bookings.markPickedUp(done.getBookingId());
        bookings.returnBooking(done.getBookingId(), TODAY, BigDecimal.ZERO);

        List<BookingService.BookedPeriod> periods = bookings.getBookedPeriods("VEH001");
        assertEquals(3, periods.size());
        int checked = 0;
        for (int start = 0; start < 45; start++) {
            for (int len = 1; len <= 12; len++) {
                LocalDate s = TODAY.plusDays(start), e = s.plusDays(len);
                boolean free = BookingService.firstBookedDay(periods, s, e) == null;
                assertEquals(bookings.isVehicleAvailable("VEH001", s, e), free, s + " to " + e);
                checked++;
            }
        }
        assertEquals(540, checked);
    }

    @Test
    void firstBookedDayAndHandover() throws Exception {
        book("CUS001", 5, 8);
        List<BookingService.BookedPeriod> periods = bookings.getBookedPeriods("VEH001");
        assertNull(BookingService.firstBookedDay(periods, TODAY.plusDays(2), TODAY.plusDays(5)),
                "returning on the day the next rental starts is a handover");
        assertEquals(TODAY.plusDays(5), BookingService.firstBookedDay(periods, TODAY.plusDays(2), TODAY.plusDays(6)));
        assertEquals(TODAY.plusDays(6), BookingService.firstBookedDay(periods, TODAY.plusDays(6), TODAY.plusDays(10)));
        assertNull(BookingService.firstBookedDay(periods, TODAY.plusDays(8), TODAY.plusDays(9)), "free again on the return day");
        assertTrue(periods.get(0).covers(TODAY.plusDays(7)));
        assertFalse(periods.get(0).covers(TODAY.plusDays(8)));
    }

    @Test
    void customersSeeDatesButNotWhoseBooking() throws Exception {
        Booking johns = book("CUS001", 2, 4);
        Booking janes = book("CUS002", 6, 9);

        List<BookingService.BookedPeriod> asAdmin = bookings.getBookedPeriods("VEH001");
        assertEquals(janes.getBookingId(), asAdmin.get(1).getBookingId());
        assertEquals("CUS002", asAdmin.get(1).getCustomerId());
        assertFalse(asAdmin.get(0).isMine(), "admins have no bookings of their own");

        auth.logout();
        auth.login("john", "john123");
        List<BookingService.BookedPeriod> asJohn = bookings.getBookedPeriods("VEH001");
        assertEquals(2, asJohn.size(), "John sees both periods, so he can't double-book");
        assertTrue(asJohn.get(0).isMine());
        assertEquals(johns.getBookingId(), asJohn.get(0).getBookingId());
        assertFalse(asJohn.get(1).isMine());
        assertNull(asJohn.get(1).getBookingId(), "another customer's booking ID is hidden");
        assertNull(asJohn.get(1).getCustomerId(), "and so is who booked it");
        assertEquals(TODAY.plusDays(6), asJohn.get(1).getStart(), "but the dates are shown");

        auth.logout();
        assertTrue(bookings.getBookedPeriods("VEH001").isEmpty(), "nothing without logging in");
    }

    @Test
    void overdueRentalBlocksThroughToday() throws Exception {
        Booking late = book("CUS001", 0, 2);
        bookings.markPickedUp(late.getBookingId());
        Path f = dataDir.resolve("bookings.txt"); // move it into the past: due back 3 days ago, not returned
        Files.writeString(f, Files.readString(f)
                .replace(TODAY.plusDays(2).toString(), TODAY.minusDays(3).toString())
                .replace("\t" + TODAY + "\t", "\t" + TODAY.minusDays(5) + "\t"));
        open();
        List<BookingService.BookedPeriod> periods = bookings.getBookedPeriods("VEH001");
        assertEquals(1, periods.size());
        assertEquals(TODAY.plusDays(1), periods.get(0).getEndExclusive(), "still out: blocks through today");
        assertTrue(periods.get(0).covers(TODAY));
        assertFalse(bookings.isVehicleAvailable("VEH001", TODAY, TODAY.plusDays(1)));
    }
}
