import model.*;
import model.exceptions.*;
import service.*;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/**
 * Plain-Java tests for the rental rules (no test library needed). They use a temporary folder,
 * so your real data/ and images/ are never touched.
 *
 * Run from the RentNGo folder:
 *   javac -encoding UTF-8 -cp src -d test-out test/RentNGoTests.java
 *   java -cp "test-out;src" RentNGoTests
 */
public class RentNGoTests {

    private static int passed = 0;
    private static int failed = 0;
    private static File tmp;

    public static void main(String[] args) throws Exception {
        tmp = Files.createTempDirectory("rentngo-tests").toFile();

        run("Return charge after payment is still collected", RentNGoTests::returnChargeAfterPayment);
        run("Future booking keeps vehicle available today", RentNGoTests::futureBookingDoesNotLockVehicle);
        run("Overlapping dates are rejected", RentNGoTests::overlappingBookingRejected);
        run("Admin sign-up needs an existing admin's approval", RentNGoTests::adminNeedsApproval);
        run("Vehicles, bookings and payments survive a restart", RentNGoTests::dataSurvivesRestart);
        run("Account IDs are never reused", RentNGoTests::accountIdsNotReused);
        run("Late fee is charged per late day", RentNGoTests::lateFeeCharged);
        run("Return date before start / in future is rejected", RentNGoTests::badReturnDatesRejected);
        run("Cancelling a paid booking records a refund", RentNGoTests::cancelPaidBookingRefunds);
        run("A rental that already started can't be cancelled", RentNGoTests::startedRentalCannotBeCancelled);
        run("Paying twice or a stale amount is rejected", RentNGoTests::duplicateAndStalePaymentsRejected);
        run("Early return frees the remaining dates", RentNGoTests::earlyReturnFreesDates);
        run("Cancelling one booking keeps the vehicle's other bookings", RentNGoTests::cancelKeepsOtherBookings);
        run("Out-of-service vehicle can't be booked", RentNGoTests::outOfServiceCannotBeBooked);
        run("Editing a vehicle keeps or changes In Service as chosen", RentNGoTests::editSetsInService);
        run("Old saved files don't mark vehicles out of service", RentNGoTests::legacyVehicleFileLoadsInService);
        run("Only allowed status changes are accepted", RentNGoTests::statusTransitionRules);
        run("Finished bookings can't change status", RentNGoTests::finalStatusesAreFinal);
        run("Picked-up rental can be returned but not cancelled", RentNGoTests::pickedUpFlow);
        run("Vehicle can't be picked up before the start date", RentNGoTests::noEarlyPickup);
        run("Rental days are counted between the dates", RentNGoTests::dayCounting);
        run("Vehicle with open bookings can't be deleted", RentNGoTests::deleteBlockedByBookings);
        run("Demo data is created only when no saved data exists", RentNGoTests::demoDataOnlyOnFirstRun);
        run("Bad lines are skipped, backed up and reported", RentNGoTests::corruptFilesHandled);
        run("Passwords are stored salted and hashed", RentNGoTests::passwordsHashed);
        run("Sign-up rules are enforced by AuthService", RentNGoTests::registrationValidation);
        run("License numbers must be unique", RentNGoTests::licenseUnique);
        run("Vehicle rules are enforced by VehicleService", RentNGoTests::vehicleValidation);
        run("Money is BigDecimal rounded to 2 decimals", RentNGoTests::moneyRounding);
        run("Bookings need an existing customer", RentNGoTests::bookingNeedsCustomer);
        run("Return records the payment and completes the booking", RentNGoTests::returnWithPayment);
        run("Late return charges 1.5x per late day", RentNGoTests::lateReturnWithPayment);
        run("Short payment blocks the return", RentNGoTests::shortPaymentBlocksReturn);
        run("Prepaid rental returns without a new payment", RentNGoTests::prepaidReturn);
        run("Receipt lists the charges and payment", RentNGoTests::receiptText);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ===== Setup helpers =====

    private static int counter = 0;

    /** A fresh set of services saving into their own temporary files. */
    private static class World {
        final File dir;
        AuthService auth;
        VehicleService vehicles;
        BookingService bookings;
        PaymentService payments;

        World() {
            dir = new File(tmp, "world" + (++counter));
            dir.mkdirs();
            restart();
        }

        /** The booking as stored now: the services hand out copies, so read it again after a change. */
        Booking fresh(Booking b) {
            return bookings.getBookingById(b.getBookingId()).orElseThrow();
        }

        /** The password hash saved in accounts.txt (the services never hand hashes out). */
        String storedHash(String username) throws Exception {
            for (String line : Files.readAllLines(new File(dir, "accounts.txt").toPath())) {
                String[] f = line.split("\t", -1);
                if (f.length > 3 && f[2].equalsIgnoreCase(username)) return f[3];
            }
            throw new AssertionError(username + " not in accounts.txt");
        }

        /** Re-creates the services from the saved files, like reopening the app. */
        void restart() {
            auth = new AuthService(new AccountStore(new File(dir, "accounts.txt")));
            vehicles = new VehicleService(new File(dir, "vehicles.txt"), null);
            bookings = new BookingService(vehicles, auth, new File(dir, "bookings.txt"));
            payments = new PaymentService(bookings, new File(dir, "payments.txt"));
            try {
                auth.login("admin", "admin123"); // the services check who is calling
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final LocalDate TODAY = LocalDate.now();

    // ===== Tests =====

    static void returnChargeAfterPayment() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        w.payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Cash", b.getBalanceDue());
        check(w.fresh(b).isPaid(), "booking should be paid");

        w.bookings.returnBooking(b.getBookingId(), TODAY, peso(1000));
        check(!w.fresh(b).isPaid(), "extra charge should reopen the booking");
        check(near(w.fresh(b).getBalanceDue(), peso(1000)), "balance should be the 1,000 charge, was " + w.fresh(b).getBalanceDue());
        check(w.bookings.getUnpaidBookings().stream().anyMatch(x -> x.getBookingId().equals(b.getBookingId())), "booking should be back in the unpaid list");

        w.payments.processPayment(b.getBookingId(), "CUS001", peso(1000), "Cash", peso(1000));
        check(w.fresh(b).isPaid(), "booking should be fully paid after paying the charge");
    }

    static void futureBookingDoesNotLockVehicle() throws Exception {
        World w = new World();
        w.bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(30), TODAY.plusDays(32));
        check(w.bookings.isVehicleAvailableToday("VEH001"), "vehicle should still be available today");
        check(!w.bookings.isVehicleAvailable("VEH001", TODAY.plusDays(31), TODAY.plusDays(31)), "booked dates should be unavailable");
        // a different, non-overlapping range must still be bookable
        w.bookings.createBooking("CUS002", "VEH001", TODAY, TODAY.plusDays(2));
        check(!w.bookings.isVehicleAvailableToday("VEH001"), "vehicle should be out once a rental starts today");
    }

    static void overlappingBookingRejected() throws Exception {
        World w = new World();
        w.bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(10), TODAY.plusDays(15));
        expectThrows(BookingException.class,
                () -> w.bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(14), TODAY.plusDays(20)));
    }

    static void adminNeedsApproval() throws Exception {
        World w = new World();
        expectThrows(ValidationException.class,
                () -> w.auth.registerAdmin("boss", "secret12", "Boss", "b@x.com", "", "admin", "wrongpass"));
        expectThrows(ValidationException.class,
                () -> w.auth.registerAdmin("boss", "secret12", "Boss", "b@x.com", "", "john", "john123"));
        Admin a = w.auth.registerAdmin("boss", "secret12", "Boss", "b@x.com", "", "admin", "admin123");
        check(a.getUserId().startsWith("ADM"), "approved admin should be created");
    }

    static void dataSurvivesRestart() throws Exception {
        World w = new World();
        w.vehicles.addVehicle(new Car("VEH100", "Mitsubishi", "Mirage", 2024, peso(1800), "NEW-0001", 4, "Gasoline"));
        Booking b = w.bookings.createBooking("CUS001", "VEH100", TODAY.plusDays(3), TODAY.plusDays(4));
        w.payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "GCash", b.getBalanceDue());

        w.restart();
        check(w.vehicles.getVehicleById("VEH100").isPresent(), "added vehicle should be reloaded");
        Booking again = w.bookings.getBookingById(b.getBookingId()).orElse(null);
        check(again != null, "booking should be reloaded");
        check(again.isPaid(), "payment status should be reloaded");
        check(w.payments.getAllPayments().size() == 1, "payment record should be reloaded");
        Booking next = w.bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        check(!next.getBookingId().equals(b.getBookingId()), "new booking IDs must not repeat after restart");
    }

    static void accountIdsNotReused() throws Exception {
        World w = new World();
        Customer c = w.auth.registerCustomer("temp1", "secret12", "Temp", "t@x.com", "", "L1");
        w.auth.deactivateCustomer(c.getUserId(), w.bookings);
        w.restart();
        Customer d = w.auth.registerCustomer("temp2", "secret12", "Temp Two", "t2@x.com", "", "L2");
        check(!d.getUserId().equals(c.getUserId()), "deleted ID " + c.getUserId() + " was reused");
    }

    static void lateFeeCharged() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(1));
        BigDecimal rate = w.vehicles.getVehicleById("VEH001").orElseThrow().getPricePerDay();
        check(near(w.bookings.calculateLateFee(b, TODAY.plusDays(4)), rate.multiply(new BigDecimal("4.5"))),
                "3 late days should cost 3 x 1.5 x rate");
        check(w.bookings.calculateLateFee(b, TODAY).signum() == 0, "early return has no late fee");
    }

    static void badReturnDatesRejected() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY, TODAY.plusDays(2));
        expectThrows(BookingException.class, () -> w.bookings.returnBooking(b.getBookingId(), TODAY.minusDays(1), Money.ZERO));
        expectThrows(BookingException.class, () -> w.bookings.returnBooking(b.getBookingId(), TODAY.plusDays(1), Money.ZERO));
    }

    static void cancelPaidBookingRefunds() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH003", TODAY.plusDays(5), TODAY.plusDays(6));
        BigDecimal paid = b.getBalanceDue();
        w.payments.processPayment(b.getBookingId(), "CUS001", paid, "Card", paid);

        BigDecimal refund = w.payments.cancelBooking(b.getBookingId());
        check(near(refund, paid), "full amount should be refunded");
        check(near(b.getNetPaid(), Money.ZERO), "revenue kept should be zero after refund");
        List<Payment> list = w.payments.getAllPayments();
        check(list.stream().anyMatch(p -> p.isRefund() && near(p.getAmount(), paid.negate())), "refund payment should be recorded");
    }

    static void startedRentalCannotBeCancelled() throws Exception {
        World w = new World();
        // a rental that began yesterday can only be created by loading it from the saved file
        File f = new File(w.dir, "bookings.txt");
        Files.writeString(f.toPath(), "# bookings\nBKG0001\tCUS001\tVEH001\t" + TODAY.minusDays(1) + "\t"
                + TODAY.plusDays(3) + "\t10000.0\tCONFIRMED\t\t0.0\t0.0\t0.0\t0.0\n");
        w.restart();
        check(!w.bookings.isVehicleAvailableToday("VEH001"), "vehicle should be out");
        expectThrows(BookingException.class, () -> w.bookings.cancelBooking("BKG0001"));
    }

    static void duplicateAndStalePaymentsRejected() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH004", TODAY.plusDays(1), TODAY.plusDays(2));
        BigDecimal due = b.getBalanceDue();
        expectThrows(ValidationException.class,
                () -> w.payments.processPayment(b.getBookingId(), "CUS001", due.subtract(peso(100)), "Cash", due));
        w.payments.processPayment(b.getBookingId(), "CUS001", due, "Cash", due);
        expectThrows(ValidationException.class,
                () -> w.payments.processPayment(b.getBookingId(), "CUS001", due, "Cash", due));
        check(w.payments.getAllPayments().size() == 1, "only one payment should exist");
    }

    static void earlyReturnFreesDates() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH006", TODAY, TODAY.plusDays(6));
        w.bookings.returnBooking(b.getBookingId(), TODAY, Money.ZERO);
        check(w.bookings.isVehicleAvailableToday("VEH006"), "vehicle should be available after return");
        w.bookings.createBooking("CUS002", "VEH006", TODAY.plusDays(1), TODAY.plusDays(3));
    }

    static void cancelKeepsOtherBookings() throws Exception {
        World w = new World();
        Booking today = w.bookings.createBooking("CUS001", "VEH002", TODAY, TODAY.plusDays(1));
        Booking later = w.bookings.createBooking("CUS002", "VEH002", TODAY.plusDays(10), TODAY.plusDays(11));
        w.payments.cancelBooking(later.getBookingId());
        check(!w.bookings.isVehicleAvailableToday("VEH002"), "today's rental must still block the vehicle");
        check(w.bookings.isVehicleAvailable("VEH002", TODAY.plusDays(10), TODAY.plusDays(11)), "cancelled dates should be free");
        check(today.getStatus() == BookingStatus.CONFIRMED, "other booking untouched");
    }

    static void outOfServiceCannotBeBooked() throws Exception {
        World w = new World();
        w.vehicles.setVehicleInService("VEH007", false);
        expectThrows(BookingException.class,
                () -> w.bookings.createBooking("CUS001", "VEH007", TODAY.plusDays(1), TODAY.plusDays(2)));
        check(w.vehicles.getInServiceVehicles().stream().noneMatch(v -> v.getVehicleId().equals("VEH007")),
                "out-of-service vehicle should be hidden from the in-service list");
        w.restart();
        check(!w.vehicles.getVehicleById("VEH007").orElseThrow().isAvailable(), "In Service flag should be saved");
    }

    static void editSetsInService() throws Exception {
        World w = new World();
        Car edited = new Car("VEH001", "Toyota", "Camry", 2023, peso(2600), "ABC-1234", 4, "Hybrid");
        edited.setAvailable(false);
        w.vehicles.updateVehicle("VEH001", edited);
        Vehicle v = w.vehicles.getVehicleById("VEH001").orElseThrow();
        check(!v.isAvailable(), "edit should be able to take the vehicle out of service");
        check("Hybrid".equals(((Car) v).getFuelType()), "subclass fields should still be copied");
        SUV asSuv = new SUV("VEH001", "Toyota", "RAV4", 2023, peso(2900), "ABC-1234", true, 5);
        asSuv.setAvailable(true);
        w.vehicles.updateVehicle("VEH001", asSuv);
        check(w.vehicles.getVehicleById("VEH001").orElseThrow() instanceof SUV, "type change should replace the object");
        check(w.vehicles.getVehicleById("VEH001").orElseThrow().isAvailable(), "type change keeps the chosen flag");
    }

    static void legacyVehicleFileLoadsInService() throws Exception {
        World w = new World();
        File f = new File(w.dir, "vehicles.txt");
        Files.writeString(f.toPath(), "# RentNGo vehicles - type\tid\tbrand\tmodel\tyear\tpricePerDay\tplate\tavailable\timagePath\tdetail1\tdetail2\n"
                + "Car\tVEH001\tToyota\tCamry\t2023\t2500.0\tABC-1234\tfalse\t\t4\tGasoline\n");
        w.restart();
        check(w.vehicles.getVehicleById("VEH001").orElseThrow().isAvailable(),
                "an old 'rented' value must not become 'out of service'");
    }

    static void statusTransitionRules() throws Exception {
        check(BookingService.canTransition(BookingStatus.PENDING, BookingStatus.CONFIRMED), "PENDING -> CONFIRMED");
        check(BookingService.canTransition(BookingStatus.PENDING, BookingStatus.CANCELLED), "PENDING -> CANCELLED");
        check(BookingService.canTransition(BookingStatus.CONFIRMED, BookingStatus.ACTIVE), "CONFIRMED -> ACTIVE");
        check(BookingService.canTransition(BookingStatus.CONFIRMED, BookingStatus.CANCELLED), "CONFIRMED -> CANCELLED");
        check(BookingService.canTransition(BookingStatus.ACTIVE, BookingStatus.COMPLETED), "ACTIVE -> COMPLETED");
        check(!BookingService.canTransition(BookingStatus.PENDING, BookingStatus.ACTIVE), "PENDING -> ACTIVE not allowed");
        check(!BookingService.canTransition(BookingStatus.CONFIRMED, BookingStatus.COMPLETED), "CONFIRMED -> COMPLETED not allowed");
        check(!BookingService.canTransition(BookingStatus.ACTIVE, BookingStatus.CANCELLED), "ACTIVE -> CANCELLED not allowed");

        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(2), TODAY.plusDays(3));
        expectThrows(BookingException.class, () -> w.bookings.updateBookingStatus(b.getBookingId(), BookingStatus.COMPLETED));
        check(b.getStatus() == BookingStatus.CONFIRMED, "rejected change must leave the status alone");

        // a PENDING booking (only possible from saved data) can be confirmed
        File f = new File(w.dir, "bookings.txt");
        Files.writeString(f.toPath(), "# bookings\nBKG0009\tCUS001\tVEH004\t" + TODAY.plusDays(5) + "\t"
                + TODAY.plusDays(6) + "\t7000.0\tPENDING\t\t0.0\t0.0\t0.0\t0.0\n");
        w.restart();
        w.bookings.updateBookingStatus("BKG0009", BookingStatus.CONFIRMED);
        check(w.bookings.getBookingById("BKG0009").orElseThrow().getStatus() == BookingStatus.CONFIRMED, "PENDING should confirm");
    }

    static void finalStatusesAreFinal() throws Exception {
        World w = new World();
        Booking cancelled = w.bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(4), TODAY.plusDays(5));
        w.bookings.cancelBooking(cancelled.getBookingId());
        try {
            w.bookings.updateBookingStatus(cancelled.getBookingId(), BookingStatus.COMPLETED);
            throw new AssertionError("CANCELLED -> COMPLETED should be rejected");
        } catch (BookingException e) {
            check(e.getMessage().contains("cancelled") && e.getMessage().contains("final"), "clear message expected: " + e.getMessage());
        }
        Booking done = w.bookings.createBooking("CUS001", "VEH003", TODAY, TODAY.plusDays(1));
        w.bookings.returnBooking(done.getBookingId(), TODAY, Money.ZERO);
        expectThrows(BookingException.class, () -> w.bookings.updateBookingStatus(done.getBookingId(), BookingStatus.ACTIVE));
        expectThrows(BookingException.class, () -> w.bookings.cancelBooking(done.getBookingId()));
    }

    static void pickedUpFlow() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH005", TODAY, TODAY.plusDays(2));
        w.bookings.markPickedUp(b.getBookingId());
        check(w.fresh(b).getStatus() == BookingStatus.ACTIVE, "should be ACTIVE after pickup");
        expectThrows(BookingException.class, () -> w.bookings.markPickedUp(b.getBookingId()));
        expectThrows(BookingException.class, () -> w.bookings.cancelBooking(b.getBookingId()));
        w.bookings.returnBooking(b.getBookingId(), TODAY, Money.ZERO);
        check(w.fresh(b).getStatus() == BookingStatus.COMPLETED, "ACTIVE -> COMPLETED on return");
    }

    static void noEarlyPickup() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH006", TODAY.plusDays(3), TODAY.plusDays(4));
        expectThrows(BookingException.class, () -> w.bookings.markPickedUp(b.getBookingId()));
        check(b.getStatus() == BookingStatus.CONFIRMED, "status unchanged");
    }

    static void dayCounting() throws Exception {
        check(Booking.calculateDays(TODAY, TODAY.plusDays(1)) == 1, "Mon -> Tue should be 1 day");
        check(Booking.calculateDays(TODAY, TODAY.plusDays(3)) == 3, "3 days apart should be 3 days");
        check(Booking.calculateDays(TODAY, TODAY) == 1, "never less than 1 day");

        World w = new World();
        Vehicle camry = w.vehicles.getVehicleById("VEH001").orElseThrow();
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2));
        check(b.getNumberOfDays() == 1, "booking should show 1 day");
        check(near(b.getTotalCost(), camry.getPricePerDay()), "1-day rental costs one day's rate, was " + b.getTotalCost());
        expectThrows(BookingException.class,
                () -> w.bookings.createBooking("CUS001", "VEH002", TODAY.plusDays(1), TODAY.plusDays(1)));
    }

    static void deleteBlockedByBookings() throws Exception {
        World w = new World();
        w.bookings.createBooking("CUS001", "VEH004", TODAY, TODAY.plusDays(1));
        Booking later = w.bookings.createBooking("CUS002", "VEH004", TODAY.plusDays(10), TODAY.plusDays(12));
        w.bookings.markPickedUp(w.bookings.getBookingsByVehicle("VEH004").get(0).getBookingId());
        try {
            w.vehicles.deleteVehicle("VEH004", w.bookings.getBookingsByVehicle("VEH004"));
            throw new AssertionError("delete should be refused");
        } catch (ValidationException e) {
            check(e.getMessage().equals("Cannot delete: vehicle has 2 upcoming or active bookings"), "message was: " + e.getMessage());
        }
        check(w.vehicles.getVehicleById("VEH004").isPresent(), "vehicle must still exist");

        // once nothing is open (returned + cancelled), deleting works
        w.bookings.returnBooking(w.bookings.getBookingsByVehicle("VEH004").get(0).getBookingId(), TODAY, Money.ZERO);
        w.bookings.cancelBooking(later.getBookingId());
        w.vehicles.deleteVehicle("VEH004", w.bookings.getBookingsByVehicle("VEH004"));
        check(w.vehicles.getVehicleById("VEH004").isEmpty(), "vehicle should be deleted");
    }

    static void demoDataOnlyOnFirstRun() throws Exception {
        File dir = new File(tmp, "store" + (++counter));
        DataStore first = new DataStore(dir, null);
        first.getAuthService().login("admin", "admin123");
        check(first.getLoadProblems().isEmpty(), "fresh start should have no problems");
        check(first.getVehicleService().getAllVehicles().size() == 7, "demo vehicles on first run");
        first.getVehicleService().deleteVehicle("VEH007", List.of());
        Customer c = first.getAuthService().registerCustomer("newbie", "secret12", "New Person", "n@x.com", "", "L9");

        DataStore second = new DataStore(dir, null);
        second.getAuthService().login("admin", "admin123");
        check(second.getVehicleService().getVehicleById("VEH007").isEmpty(), "deleted demo vehicle must not come back");
        check(second.getAuthService().getAllCustomers().stream().anyMatch(x -> x.getUserId().equals(c.getUserId())), "customer kept");
        Customer next = second.getAuthService().registerCustomer("newbie2", "secret12", "Next", "x@x.com", "", "L8");
        check(next.getUserId().equals("CUS004"), "next ID should follow the highest CUS number, was " + next.getUserId());
    }

    static void corruptFilesHandled() throws Exception {
        File dir = new File(tmp, "store" + (++counter));
        new DataStore(dir, null);
        File vehicles = new File(dir, "vehicles.txt");
        File bookings = new File(dir, "bookings.txt");
        Files.writeString(vehicles.toPath(), "# vehicles\nCar\tVEH001\tToyota\tCamry\tNOT-A-YEAR\t2500\tABC\ttrue\t\t4\tGas\n");
        Files.writeString(bookings.toPath(), "# bookings\nBKG0001\tCUS001\tVEH001\t2026-99-99\t2026-10-02\t1\tCONFIRMED\t\t0\t0\t0\t0\n");

        DataStore store = new DataStore(dir, null);
        check(store.getLoadProblems().size() == 2, "two problems expected, got " + store.getLoadProblems());
        check(store.getVehicleService().getAllVehicles().isEmpty(), "the bad row is skipped and no demo vehicles are added");
        check(store.getLoadProblems().get(0).contains("vehicles.txt line 2 skipped"), "skipped line reported, got " + store.getLoadProblems());
        File[] backups = dir.listFiles((d, name) -> name.contains(".corrupt-"));
        check(backups != null && backups.length == 2, "both damaged files should be backed up");
        boolean kept = false;
        for (File f : backups) kept |= Files.readString(f.toPath()).contains("NOT-A-YEAR");
        check(kept, "the backup must contain the original damaged content");
    }

    static void passwordsHashed() throws Exception {
        World w = new World();
        for (User u : w.auth.getAllUsers()) {
            check(u.getPassword() == null, "password hashes must not be handed out: " + u.getUsername());
            String stored = w.storedHash(u.getUsername());
            check(!stored.contains("123"), "plain password stored for " + u.getUsername());
            check(stored.contains("$"), "expected rounds$salt$hash for " + u.getUsername());
        }
        check(w.auth.login("admin", "admin123") instanceof Admin, "demo admin login");
        check(w.auth.login("john", "john123") instanceof Customer, "demo customer login");
        Customer a = w.auth.registerCustomer("same1", "samepass1", "A", "a@x.com", "", "LIC-A");
        Customer b = w.auth.registerCustomer("same2", "samepass1", "B", "b@x.com", "", "LIC-B");
        check(!w.storedHash(a.getUsername()).equals(w.storedHash(b.getUsername())), "same password must hash differently (random salt)");
        expectThrows(AuthenticationException.class, () -> w.auth.login("same1", "wrongpass"));
    }

    static void registrationValidation() throws Exception {
        World w = new World();
        expectMessage(() -> w.auth.registerCustomer("ann", "secret12", "Ann", "not-an-email", "", "L-100"), "valid email");
        expectMessage(() -> w.auth.registerCustomer("ann", "secret12", "Ann", "", "", "L-100"), "Email is required");
        expectMessage(() -> w.auth.registerCustomer("ann", "123", "Ann", "ann@x.com", "", "L-100"), "at least 8");
        expectMessage(() -> w.auth.registerCustomer("ann", "lettersonly", "Ann", "ann@x.com", "", "L-100"), "a letter and a number");
        expectMessage(() -> w.auth.registerCustomer("ann", "12345678", "Ann", "ann@x.com", "", "L-100"), "a letter and a number");
        expectMessage(() -> w.auth.registerCustomer("ann", "secret12", "Ann", "ann@x.com", "", " "), "license");
        expectMessage(() -> w.auth.registerCustomer("ann", "secret12", "", "ann@x.com", "", "L-100"), "Full name");
        expectMessage(() -> w.auth.registerCustomer("JOHN", "secret12", "Ann", "ann@x.com", "", "L-100"), "Username already exists");
        Customer ok = w.auth.registerCustomer("ann", "secret12", "Ann", "ann@x.com", "", "L-100");
        check(ok.getUserId().equals("CUS003"), "failed attempts must not use up IDs, got " + ok.getUserId());
    }

    static void licenseUnique() throws Exception {
        World w = new World();
        expectMessage(() -> w.auth.registerCustomer("dup", "secret12", "Dup", "d@x.com", "", " dl123456 "), "already registered");
        expectMessage(() -> w.auth.addCustomer("Dup", "", "0917", "DL789012"), "already registered");
        w.auth.updateCustomer("CUS001", "John Doe", "john@email.com", "555-1111", "DL123456");
        expectMessage(() -> w.auth.updateCustomer("CUS001", "John Doe", "john@email.com", "555-1111", "DL789012"), "already registered");
    }

    static void vehicleValidation() throws Exception {
        World w = new World();
        int next = java.time.Year.now().getValue() + 1;
        expectMessage(() -> w.vehicles.addVehicle(new Car("VEH200", " ", "X", 2020, peso(1000), "P-1", 4, "Gas")), "Brand is required");
        expectMessage(() -> w.vehicles.addVehicle(new Car("VEH200", "Kia", "Rio", 2020, peso(0), "P-1", 4, "Gas")), "greater than 0");
        expectMessage(() -> w.vehicles.addVehicle(new Car("VEH200", "Kia", "Rio", 1989, peso(1000), "P-1", 4, "Gas")), "Year must be between");
        expectMessage(() -> w.vehicles.addVehicle(new Car("VEH200", "Kia", "Rio", next + 1, peso(1000), "P-1", 4, "Gas")), "Year must be between");
        w.vehicles.addVehicle(new Car("VEH200", "Kia", "Rio", next, peso(1000), "P-1", 4, "Gas"));

        expectMessage(() -> w.vehicles.updateVehicle("VEH001", new Car("VEH001", "Toyota", "Camry", 2023, peso(-5), "ABC-1234", 4, "Gasoline")), "greater than 0");
        check(w.vehicles.getVehicleById("VEH001").orElseThrow().getPricePerDay().compareTo(peso(2500)) == 0, "rejected edit must not change the vehicle");
    }

    private static void expectMessage(Body body, String fragment) {
        try {
            body.run();
        } catch (ValidationException e) {
            if (e.getMessage().toLowerCase().contains(fragment.toLowerCase())) return;
            throw new AssertionError("message '" + e.getMessage() + "' should mention '" + fragment + "'");
        } catch (Throwable t) {
            throw new AssertionError("expected ValidationException but got " + t);
        }
        throw new AssertionError("expected ValidationException mentioning '" + fragment + "'");
    }

    static void moneyRounding() throws Exception {
        World w = new World();
        BigDecimal tesla = w.vehicles.getVehicleById("VEH003").orElseThrow().calculateRentalCost(1);
        check(tesla.toPlainString().equals("4050.00"), "electric 10% off 4500 should be 4050.00, was " + tesla);
        BigDecimal jeep = w.vehicles.getVehicleById("VEH005").orElseThrow().calculateRentalCost(3);
        check(jeep.toPlainString().equals("13110.00"), "4WD 3800 x 3 x 1.15 should be 13110.00, was " + jeep);
        check(Money.parse("2,500.555").toPlainString().equals("2500.56"), "half-up rounding to 2 decimals");

        // amounts saved by older versions as doubles ("8100.0") still load exactly
        File f = new File(w.dir, "bookings.txt");
        Files.writeString(f.toPath(), "# bookings\nBKG0001\tCUS001\tVEH003\t" + TODAY.plusDays(1) + "\t"
                + TODAY.plusDays(3) + "\t8100.0\tCONFIRMED\t\t0.0\t0.0\t0.0\t0.0\n");
        w.restart();
        check(w.bookings.getBookingById("BKG0001").orElseThrow().getTotalCost().toPlainString().equals("8100.00"), "legacy amount");
    }

    static void bookingNeedsCustomer() throws Exception {
        World w = new World();
        expectThrows(BookingException.class, () -> w.bookings.createBooking("CUS999", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2)));
        Booking b = w.bookings.createBooking("CUS001", "VEH001", TODAY.plusDays(1), TODAY.plusDays(2));
        check(w.bookings.getBookingsByCustomer("CUS001").stream().anyMatch(x -> x.getBookingId().equals(b.getBookingId())), "customer's bookings come from BookingService");
    }

    /** A rental that started 5 days ago and was due back 2 days ago, already picked up. */
    private static World worldWithOverdueRental() throws Exception {
        World w = new World();
        File f = new File(w.dir, "bookings.txt");
        Files.writeString(f.toPath(), "# bookings\nBKG0001\tCUS001\tVEH001\t" + TODAY.minusDays(5) + "\t"
                + TODAY.minusDays(2) + "\t7500.00\tACTIVE\t\t0.00\t0.00\t0.00\t0.00\n");
        w.restart();
        return w;
    }

    static void returnWithPayment() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH002", TODAY, TODAY.plusDays(2));
        w.bookings.markPickedUp(b.getBookingId());
        Payment p = w.payments.completeReturn(b.getBookingId(), TODAY, Money.ZERO, "Cash", peso(6000)); // only cash gets change
        check(w.fresh(b).getStatus() == BookingStatus.COMPLETED, "booking should be COMPLETED");
        check(p != null && near(p.getAmount(), b.getTotalCost()), "payment should cover the rental");
        check(near(p.getChange(), peso(6000).subtract(b.getTotalCost())), "change should be calculated");
        check(w.fresh(b).isPaid(), "booking should be fully paid");
        w.restart();
        check(w.payments.getAllPayments().stream().anyMatch(x -> x.getPaymentId().equals(p.getPaymentId())), "payment saved");
        check(w.bookings.getBookingById(b.getBookingId()).orElseThrow().getStatus() == BookingStatus.COMPLETED, "status saved");
    }

    static void lateReturnWithPayment() throws Exception {
        World w = worldWithOverdueRental();
        Booking b = w.bookings.getBookingById("BKG0001").orElseThrow();
        BigDecimal rate = w.vehicles.getVehicleById("VEH001").orElseThrow().getPricePerDay();
        BigDecimal expectedLate = rate.multiply(new BigDecimal("3.0")); // 2 days x 1.5
        BigDecimal due = w.bookings.previewAmountDue(b, TODAY, peso(500));
        check(near(due, peso(7500).add(expectedLate).add(peso(500))), "due should include 1.5x late fee, was " + due);
        Payment p = w.payments.completeReturn("BKG0001", TODAY, peso(500), "Cash", due);
        check(near(w.fresh(b).getLateFee(), expectedLate), "late fee stored");
        check(near(p.getAmount(), due), "paid amount equals amount due");
        check(p.getChange().signum() == 0, "exact payment has no change");
    }

    static void shortPaymentBlocksReturn() throws Exception {
        World w = worldWithOverdueRental();
        expectMessage(() -> w.payments.completeReturn("BKG0001", TODAY, Money.ZERO, "Cash", peso(100)), "less than the amount due");
        expectMessage(() -> w.payments.completeReturn("BKG0001", TODAY, Money.ZERO, "Bank Transfer", peso(99999)), "Cash, Card or GCash");
        Booking b = w.bookings.getBookingById("BKG0001").orElseThrow();
        check(b.getStatus() == BookingStatus.ACTIVE, "a failed payment must not return the vehicle");
        check(w.payments.getAllPayments().isEmpty(), "no payment should be recorded");
    }

    static void prepaidReturn() throws Exception {
        World w = new World();
        Booking b = w.bookings.createBooking("CUS001", "VEH003", TODAY, TODAY.plusDays(1));
        w.payments.processPayment(b.getBookingId(), "CUS001", b.getBalanceDue(), "Card", b.getBalanceDue());
        w.bookings.markPickedUp(b.getBookingId());
        Payment p = w.payments.completeReturn(b.getBookingId(), TODAY, Money.ZERO, "Cash", null);
        check(p == null, "nothing owed, so no new payment");
        check(w.fresh(b).getStatus() == BookingStatus.COMPLETED && w.fresh(b).isPaid(), "completed and paid");
    }

    static void receiptText() throws Exception {
        World w = worldWithOverdueRental();
        Booking b = w.bookings.getBookingById("BKG0001").orElseThrow();
        BigDecimal due = w.bookings.previewAmountDue(b, TODAY, Money.ZERO);
        Payment p = w.payments.completeReturn("BKG0001", TODAY, Money.ZERO, "Cash", due.add(peso(100)));
        String text = gui.ReceiptDialog.buildReceipt(w.fresh(b), w.payments.getPaymentsByBookingId("BKG0001"), p, "John Doe",
                w.vehicles.getVehicleById("VEH001").orElseThrow());
        for (String expected : new String[]{p.getPaymentId(), "John Doe", "Toyota Camry", "Late fee", "Cash", "change", "₱100.00"}) {
            check(text.contains(expected), "receipt should mention " + expected + ":\n" + text);
        }
    }

    // ===== Tiny test framework =====

    interface Body { void run() throws Exception; }

    private static void run(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + "\n      " + t);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Same amount of money, ignoring scale (2500 == 2500.00). */
    private static boolean near(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }

    private static BigDecimal peso(long amount) {
        return Money.of(amount);
    }

    private static void expectThrows(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
