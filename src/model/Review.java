package model;

import java.time.LocalDate;

/**
 * A customer's rating (1 to 5 stars) and optional comment for a completed rental. Only the
 * "hidden" flag can change after it is written: an admin hides an offensive review instead of
 * deleting it, so the decision can be undone and stays in the Audit Log.
 */
public class Review {
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 300;

    private final String reviewId;
    private final String bookingId;
    private final String customerId;
    private final String vehicleId;
    private final int rating;
    private final String comment; // "" when the customer only gave stars
    private final LocalDate date;
    private boolean hidden;

    public Review(String reviewId, String bookingId, String customerId, String vehicleId, int rating, String comment,
                  LocalDate date, boolean hidden) {
        this.reviewId = reviewId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.rating = rating;
        this.comment = comment == null ? "" : comment;
        this.date = date;
        this.hidden = hidden;
    }

    /** An independent copy: changing it never changes the review held by ReviewService. */
    public Review copy() {
        return new Review(reviewId, bookingId, customerId, vehicleId, rating, comment, date, hidden);
    }

    public String getReviewId() {
        return reviewId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDate getDate() {
        return date;
    }

    /** Hidden reviews are left out of averages and of what customers see. */
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /** "★★★★☆" for a rating of 4. */
    public static String stars(int rating) {
        return "★".repeat(Math.max(0, rating)) + "☆".repeat(Math.max(0, MAX_RATING - rating));
    }

    @Override
    public String toString() {
        return "Review{" +
                "reviewId='" + reviewId + '\'' +
                ", bookingId='" + bookingId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", rating=" + rating +
                ", hidden=" + hidden +
                '}';
    }
}
