package service;

import model.Booking;
import model.exceptions.BookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for availability: the end date is the return day (exclusive), the same rule
 * Booking.calculateDays uses, so a car can be handed over on the day it comes back.
 */
class BookingAvailabilityTest {

    private static final LocalDate TODAY = LocalDate.now();

    @TempDir
    Path dataDir;

    private BookingService bookings;

    @BeforeEach
    void setUp() {
        bookings = Stores.asAdmin(dataDir).getBookingService();
    }

    private Booking book(String customer, int startOffset, int endOffset) throws Exception {
        return bookings.createBooking(customer, "VEH001", TODAY.plusDays(startOffset), TODAY.plusDays(endOffset));
    }

    /** Replaces bookings.txt with one VEH001 rental and reloads, for dates in the past. */
    private void loadRental(String status, int startOffset, int endOffset) throws Exception {
        Files.writeString(dataDir.resolve("bookings.txt"), "# bookings\n" + String.join("\t", "BKG0001", "CUS001", "VEH001",
                TODAY.plusDays(startOffset).toString(), TODAY.plusDays(endOffset).toString(), "5000.00", status,
                "", "0.00", "0.00", "0.00", "0.00") + "\n");
        bookings = Stores.asAdmin(dataDir).getBookingService();
    }

    // ===== Handover on the same day =====

    @Test
    void sameDayHandoverIsAllowed() throws Exception {
        Booking first = book("CUS001", 5, 10);

        Booking next = book("CUS002", 10, 12);       // starts the day the first one is returned
        Booking before = book("CUS002", 2, 5);       // ends (is returned) the day the first one starts

        assertNotNull(next);
        assertNotNull(before);
        assertEquals(5 + 2 + 3, Booking.calculateDays(first.getStartDate(), first.getEndDate())
                + Booking.calculateDays(next.getStartDate(), next.getEndDate())
                + Booking.calculateDays(before.getStartDate(), before.getEndDate()),
                "back-to-back bookings charge every day exactly once");
    }

    // ===== Overlaps of a day or more are rejected =====

    @ParameterizedTest(name = "days {0}-{1} overlap booked days 5-10")
    @CsvSource({
            "9, 12",   // one day (the 9th) inside at the end
            "4, 6",    // one day (the 5th) inside at the start
            "5, 6",    // the first day only
            "9, 10",   // the last day only
            "5, 10",   // the same dates
            "3, 12",   // covers it
    })
    void anOverlapOfOneDayOrMoreIsRejected(int startOffset, int endOffset) throws Exception {
        book("CUS001", 5, 10);
        BookingException e = assertThrows(BookingException.class, () -> book("CUS002", startOffset, endOffset));
        assertTrue(e.getMessage().contains("already booked"), e.getMessage());
    }

    @Test
    void availabilityChecksUseTheSameRule() throws Exception {
        book("CUS001", 5, 10);
        assertTrue(bookings.isVehicleAvailable("VEH001", TODAY.plusDays(10), TODAY.plusDays(11)));
        assertTrue(bookings.isVehicleAvailable("VEH001", TODAY.plusDays(4), TODAY.plusDays(5)));
        assertFalse(bookings.isVehicleAvailable("VEH001", TODAY.plusDays(9), TODAY.plusDays(10)));
        assertFalse(bookings.isVehicleAvailable("VEH001", TODAY.plusDays(7), TODAY.plusDays(7)),
                "an empty range is checked as that single day");
        assertTrue(bookings.isVehicleAvailable("VEH001", TODAY.plusDays(10), TODAY.plusDays(10)));
    }

    // ===== Rentals that are out right now =====

    @Test
    void overdueRentalBlocksToday() throws Exception {
        loadRental("ACTIVE", -5, -2); // should have come back 2 days ago

        assertFalse(bookings.isVehicleAvailableToday("VEH001"));
        assertThrows(BookingException.class, () -> book("CUS002", 0, 2), "can't hand it out today");
        assertNotNull(book("CUS002", 1, 3), "but it can be booked from tomorrow");
    }

    @Test
    void overdueBookingThatWasNeverPickedUpAlsoBlocksToday() throws Exception {
        loadRental("CONFIRMED", -5, -2);
        assertFalse(bookings.isVehicleAvailableToday("VEH001"));
    }

    @Test
    void rentalDueBackTodayAllowsAHandoverToday() throws Exception {
        loadRental("ACTIVE", -3, 0); // return day is today
        assertTrue(bookings.isVehicleAvailableToday("VEH001"));
        assertNotNull(book("CUS002", 0, 2));
    }

    @Test
    void rentalRunningPastTodayBlocksToday() throws Exception {
        loadRental("ACTIVE", -1, 2);
        assertFalse(bookings.isVehicleAvailableToday("VEH001"));
        assertNotNull(book("CUS002", 2, 4), "free again from its return day");
    }

    @Test
    void finishedAndCancelledBookingsNeverBlock() throws Exception {
        Booking cancelled = book("CUS001", 5, 10);
        bookings.cancelBooking(cancelled.getBookingId());
        assertNotNull(book("CUS002", 5, 10));

        loadRental("COMPLETED", -5, -2);
        assertTrue(bookings.isVehicleAvailableToday("VEH001"));
    }

    @Test
    void earlyReturnFreesTheRestOfTheDates() throws Exception {
        Booking b = book("CUS001", 0, 5);
        bookings.markPickedUp(b.getBookingId());
        bookings.returnBooking(b.getBookingId(), TODAY, BigDecimal.ZERO);
        assertTrue(bookings.isVehicleAvailableToday("VEH001"));
        assertNotNull(book("CUS002", 0, 3));
    }

    @Test
    void overlapRuleOnItsOwn() {
        LocalDate d1 = TODAY, d3 = TODAY.plusDays(2), d5 = TODAY.plusDays(4), d7 = TODAY.plusDays(6);
        assertFalse(BookingService.overlaps(d1, d3, d3, d5), "touching at the return day");
        assertFalse(BookingService.overlaps(d3, d5, d1, d3), "same, the other way round");
        assertTrue(BookingService.overlaps(d1, d5, d3, d7));
        assertTrue(BookingService.overlaps(d1, d7, d3, d5), "one inside the other");
        assertFalse(BookingService.overlaps(d1, d3, d5, d7));
    }
}
