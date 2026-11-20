package service;

import model.Booking;
import model.BookingStatus;
import model.Review;
import model.exceptions.BookingException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ratings and reviews of vehicles, saved in data/reviews.txt. A customer can review each of their
 * own COMPLETED rentals once; an admin can hide a review (and show it again). Created by
 * BookingService, which it needs for the "completed and yours" check; screens reach it through
 * {@link BookingService#getReviewService()}.
 */
public class ReviewService {
    private static final String HEADER = "RentNGo reviews - id\tbookingId\tcustomerId\tvehicleId\trating\tcomment\tdate\thidden";
    private static final String ID_PREFIX = "RVW";

    /** Average stars and number of visible reviews for one vehicle. */
    public static final class Summary {
        private final double average;
        private final int count;

        Summary(double average, int count) {
            this.average = average;
            this.count = count;
        }

        public double getAverage() { return average; }
        public int getCount() { return count; }

        /** "★ 4.5 (2)" */
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "★ %.1f (%d)", average, count); // always ".", so the table can sort it
        }
    }

    private final BookingService bookingService;
    private final AuthService authService;
    private final File dataFile; // null = in memory only (tests)
    private final List<Review> reviews = new ArrayList<>();
    private int counter = 1;
    private TsvFile.Stamp stamp;
    private boolean readOnly;
    private String readOnlyMessage;
    private String loadError;

    ReviewService(BookingService bookingService, AuthService authService, File dataFile) {
        this.bookingService = bookingService;
        this.authService = authService;
        this.dataFile = dataFile;
        if (dataFile == null || !dataFile.isFile()) {
            stamp = dataFile == null ? null : TsvFile.stampOrNull(dataFile);
            return; // no reviews yet
        }
        try {
            load();
        } catch (IOException e) {
            readOnly = true;
            loadError = TsvFile.describeUnreadable(dataFile, e);
            readOnlyMessage = loadError;
        }
    }

    // ===== Loading and saving =====

    private void load() throws IOException {
        TsvFile.Contents contents = TsvFile.read(dataFile);
        stamp = contents.stamp;
        TsvFile.LoadWarnings warnings = new TsvFile.LoadWarnings(dataFile);
        int lastUsed = TsvFile.readSequence(contents, ID_PREFIX, warnings);
        for (TsvFile.Row row : contents.rows) {
            String[] f = row.fields;
            lastUsed = Math.max(lastUsed, TsvFile.idNumber(f.length > 0 ? f[0] : "", ID_PREFIX));
            try {
                reviews.add(parse(f));
            } catch (RuntimeException e) {
                warnings.skip(row, e);
            }
        }
        counter = lastUsed + 1;
        loadError = warnings.summary();
    }

    private static Review parse(String[] f) {
        TsvFile.requireFields(f, 8);
        if (TsvFile.idNumber(f[0], ID_PREFIX) == 0) throw new IllegalArgumentException("bad review ID '" + f[0] + "'");
        int rating = Integer.parseInt(f[4]);
        if (rating < Review.MIN_RATING || rating > Review.MAX_RATING) {
            throw new IllegalArgumentException("rating " + rating + " is not 1 to 5");
        }
        return new Review(f[0], f[1], f[2], f[3], rating, f[5], LocalDate.parse(f[6]), Boolean.parseBoolean(f[7]));
    }

    private void write() throws IOException {
        if (dataFile == null) return;
        if (readOnly) throw new IOException(readOnlyMessage);
        List<String[]> rows = new ArrayList<>();
        for (Review r : reviews) {
            rows.add(new String[]{r.getReviewId(), r.getBookingId(), r.getCustomerId(), r.getVehicleId(),
                    String.valueOf(r.getRating()), r.getComment(), r.getDate().toString(), String.valueOf(r.isHidden())});
        }
        try {
            stamp = TsvFile.write(dataFile, HEADER, TsvFile.sequenceMeta(ID_PREFIX, counter - 1), rows, stamp);
        } catch (TsvFile.DataChangedException e) {
            readOnly = true; // another program wrote the file: never overwrite it
            readOnlyMessage = e.getMessage();
            throw e;
        } catch (IOException e) {
            throw new IOException("Could not save reviews: " + e.getMessage(), e);
        }
    }

    /** Why reviews.txt couldn't be (fully) loaded, or null. */
    String getLoadError() {
        return loadError;
    }

    private AuditLog audit() {
        return bookingService.getAuditLog();
    }

    // ===== Writing a review (customers) =====

    /** The review already written for this booking, if any (hidden ones included). */
    public Optional<Review> getReviewForBooking(String bookingId) {
        return reviews.stream().filter(r -> r.getBookingId().equals(bookingId)).findFirst().map(Review::copy);
    }

    /**
     * Null when the logged-in customer can review this booking now; otherwise the reason, so the
     * screen can explain why the button does nothing.
     */
    public String whyCannotReview(String bookingId) {
        if (authService == null || !authService.isCustomerSession()) return "Only the customer who rented the vehicle can review it.";
        Optional<Booking> booking = bookingService.getBookingById(bookingId); // another customer's booking isn't visible
        if (!booking.isPresent() || !booking.get().getCustomerId().equals(authService.getCurrentUser().getUserId())) {
            return "Booking not found: " + bookingId;
        }
        if (booking.get().getStatus() != BookingStatus.COMPLETED) {
            return "You can review a rental once the vehicle has been returned.";
        }
        if (getReviewForBooking(bookingId).isPresent()) return "You already reviewed this rental.";
        if (readOnly) return readOnlyMessage;
        return null;
    }

    /** Saves the logged-in customer's review of their completed rental. The comment may be empty. */
    public Review addReview(String bookingId, int rating, String comment) throws BookingException {
        String reason = whyCannotReview(bookingId);
        if (reason != null) throw new BookingException(reason);
        if (rating < Review.MIN_RATING || rating > Review.MAX_RATING) {
            throw new BookingException("Choose 1 to 5 stars");
        }
        String text = comment == null ? "" : comment.trim().replaceAll("\\s*\\R\\s*", " ");
        if (text.length() > Review.MAX_COMMENT_LENGTH) {
            throw new BookingException("The comment can be at most " + Review.MAX_COMMENT_LENGTH + " characters");
        }
        Booking booking = bookingService.getBookingById(bookingId).orElseThrow();
        Review review = new Review(ID_PREFIX + String.format("%04d", counter), bookingId, booking.getCustomerId(),
                booking.getVehicleId(), rating, text, LocalDate.now(), false);
        reviews.add(review);
        counter++;
        try {
            write();
        } catch (IOException e) {
            reviews.remove(review);
            counter--;
            throw new BookingException(e.getMessage());
        }
        audit().record(AuditLog.REVIEW_ADDED, bookingId, review.getReviewId() + " " + booking.getVehicleId() + ": "
                + rating + (rating == 1 ? " star" : " stars") + (text.isEmpty() ? "" : ", \"" + text + "\""));
        return review.copy();
    }

    // ===== Reading reviews (everyone) =====

    /** A vehicle's visible reviews, newest first. */
    public List<Review> getVisibleReviews(String vehicleId) {
        return reviews.stream()
                .filter(r -> r.getVehicleId().equals(vehicleId) && !r.isHidden())
                .sorted(Comparator.comparing(Review::getDate).thenComparing(Review::getReviewId).reversed())
                .map(Review::copy)
                .collect(Collectors.toList());
    }

    /** Average rating per vehicle ID, from visible reviews only; vehicles without reviews are left out. */
    public Map<String, Summary> getRatingSummaries() {
        Map<String, int[]> totals = new HashMap<>(); // vehicleId -> {sum, count}
        for (Review r : reviews) {
            if (r.isHidden()) continue;
            int[] t = totals.computeIfAbsent(r.getVehicleId(), k -> new int[2]);
            t[0] += r.getRating();
            t[1]++;
        }
        Map<String, Summary> summaries = new HashMap<>();
        totals.forEach((id, t) -> summaries.put(id, new Summary((double) t[0] / t[1], t[1])));
        return summaries;
    }

    /** The vehicle's rating, or empty when it has no visible reviews. */
    public Optional<Summary> getRatingSummary(String vehicleId) {
        return Optional.ofNullable(getRatingSummaries().get(vehicleId));
    }

    // ===== Moderation (admins) =====

    /** Every review, hidden ones included, newest first. Empty unless an admin is logged in. */
    public List<Review> getAllReviews() {
        if (authService == null || !authService.isAdminSession()) return new ArrayList<>();
        return reviews.stream()
                .sorted(Comparator.comparing(Review::getDate).thenComparing(Review::getReviewId).reversed())
                .map(Review::copy)
                .collect(Collectors.toList());
    }

    /** Hides (true) or shows again (false) a review; admin only, recorded in the Audit Log. */
    public void setHidden(String reviewId, boolean hidden) throws BookingException {
        String denied = authService == null ? AuthService.NOT_LOGGED_IN : authService.adminOnly("hide reviews");
        if (denied != null) throw new BookingException(denied);
        Review review = reviews.stream().filter(r -> r.getReviewId().equals(reviewId)).findFirst()
                .orElseThrow(() -> new BookingException("Review not found: " + reviewId));
        if (review.isHidden() == hidden) return;
        review.setHidden(hidden);
        try {
            write();
        } catch (IOException e) {
            review.setHidden(!hidden);
            throw new BookingException(e.getMessage());
        }
        audit().record(hidden ? AuditLog.REVIEW_HIDDEN : AuditLog.REVIEW_SHOWN, review.getBookingId(),
                reviewId + " " + review.getVehicleId() + " (" + review.getRating() + " stars"
                        + (review.getComment().isEmpty() ? "" : ", \"" + review.getComment() + "\"") + ")");
    }
}
