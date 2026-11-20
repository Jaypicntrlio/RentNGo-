package gui;

import model.Booking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.BookingService;
import service.DataStore;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Clicking days on the availability calendar: start, return day, booked days and limits. */
class AvailabilityCalendarClickTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dir;

    private DataStore store;
    private AvailabilityCalendar calendar;
    private final List<LocalDate[]> picked = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        store = new DataStore(dir.resolve("data").toFile(), null);
        store.getAuthService().login("admin", "admin123");
        store.getBookingService().createBooking("CUS002", "VEH001", TODAY.plusDays(5), TODAY.plusDays(8));
        store.getAuthService().logout();
        store.getAuthService().login("john", "john123");
        Booking johns = store.getBookingService().createBooking("CUS001", "VEH001", TODAY.plusDays(12), TODAY.plusDays(13));
        assertNotNull(johns);
        calendar = new AvailabilityCalendar(store.getBookingService(), "VEH001");
        calendar.setOnSelect((s, e) -> picked.add(new LocalDate[]{s, e}));
    }

    @Test
    void dayStates() {
        assertEquals(AvailabilityCalendar.DayState.PAST, calendar.stateOf(TODAY.minusDays(1)));
        assertEquals(AvailabilityCalendar.DayState.FREE, calendar.stateOf(TODAY));
        assertEquals(AvailabilityCalendar.DayState.BOOKED, calendar.stateOf(TODAY.plusDays(5)));
        assertEquals(AvailabilityCalendar.DayState.BOOKED, calendar.stateOf(TODAY.plusDays(7)));
        assertEquals(AvailabilityCalendar.DayState.FREE, calendar.stateOf(TODAY.plusDays(8)), "free again on the return day");
        assertEquals(AvailabilityCalendar.DayState.MINE, calendar.stateOf(TODAY.plusDays(12)));
        assertEquals(AvailabilityCalendar.DayState.TOO_FAR, calendar.stateOf(TODAY.plusDays(BookingService.MAX_DAYS_AHEAD + 1)));
    }

    @Test
    void pickStartThenReturnDay() {
        calendar.clickDay(TODAY.plusDays(1));
        assertTrue(picked.isEmpty(), "only the start so far");
        calendar.clickDay(TODAY.plusDays(5)); // return on the day the next rental starts
        assertEquals(1, picked.size());
        assertEquals(TODAY.plusDays(1), picked.get(0)[0]);
        assertEquals(TODAY.plusDays(5), picked.get(0)[1]);
        assertTrue(calendar.getStatusText().startsWith("Free"), calendar.getStatusText());
    }

    @Test
    void cantRunIntoABookedDay() {
        calendar.clickDay(TODAY.plusDays(2));
        calendar.clickDay(TODAY.plusDays(9));
        assertTrue(picked.isEmpty());
        assertTrue(calendar.getStatusText().contains("Booked on " + TODAY.plusDays(5)), calendar.getStatusText());
        calendar.clickDay(TODAY.plusDays(4)); // a shorter rental still works
        assertEquals(TODAY.plusDays(4), picked.get(0)[1]);
    }

    @Test
    void bookedPastAndTooLongAreRefused() {
        calendar.clickDay(TODAY.plusDays(6));
        assertNull(calendar.getSelectionStart(), "a booked day can't be the start");
        assertTrue(calendar.getStatusText().contains("already booked"));
        calendar.clickDay(TODAY.minusDays(2));
        assertNull(calendar.getSelectionStart(), "nor a past day");

        calendar.clickDay(TODAY.plusDays(20));
        calendar.clickDay(TODAY.plusDays(20 + BookingService.MAX_RENTAL_DAYS + 1));
        assertTrue(picked.isEmpty());
        assertTrue(calendar.getStatusText().contains("at most " + BookingService.MAX_RENTAL_DAYS), calendar.getStatusText());

        calendar.clickDay(TODAY.plusDays(15)); // an earlier day starts a new selection
        assertEquals(TODAY.plusDays(15), calendar.getSelectionStart());
    }

    @Test
    void datesTypedElsewhereAreShown() {
        calendar.setSelection(TODAY.plusDays(40), TODAY.plusDays(42));
        assertEquals(YearMonth.from(TODAY.plusDays(40)), calendar.getShownMonth(), "jumps to the start's month");
        assertTrue(picked.isEmpty(), "setting the dates from the fields doesn't send them back");
        calendar.setSelection(TODAY.plusDays(4), TODAY.plusDays(6));
        assertTrue(calendar.getStatusText().contains("Not available: already booked on " + TODAY.plusDays(5)), calendar.getStatusText());
        calendar.showMonth(YearMonth.now().minusMonths(3));
        assertEquals(YearMonth.now(), calendar.getShownMonth(), "can't go back before this month");
    }
}
