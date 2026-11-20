package gui;

import model.Booking;
import service.BookingService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * A month calendar showing which days a vehicle is free or booked. Click a free day to start the
 * rental, then click the return day: the listener gets the dates (end = return day, as everywhere
 * in RentNGo). The return day may be the first day of the next booking (same-day handover).
 *
 * Customers only see that a day is booked (their own bookings are marked "Yours"); admins also see
 * the booking and customer IDs in the tooltip. The dates come from
 * {@link BookingService#getBookedPeriods}, which uses the same rule as booking itself.
 */
public class AvailabilityCalendar extends JPanel {

    /** How a day is shown. */
    enum DayState { PAST, FREE, BOOKED, MINE, TOO_FAR }

    private static final Color FREE_BG = new Color(46, 204, 113, 40);
    private static final Color BOOKED_BG = new Color(231, 76, 60, 70);
    private static final Color MINE_BG = new Color(52, 152, 219, 90);

    private final BookingService bookingService;
    private final String vehicleId;
    private List<BookingService.BookedPeriod> periods = new ArrayList<>();
    private YearMonth shown = YearMonth.now();
    private LocalDate selStart;
    private LocalDate selEnd; // the return day, or null while only the start is picked
    private BiConsumer<LocalDate, LocalDate> onSelect;

    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton prevButton = navButton("‹");
    private final JButton nextButton = navButton("›");
    private final JPanel grid = new JPanel(new GridLayout(7, 7, 3, 3));
    private final JLabel statusLabel = new JLabel(" ");

    public AvailabilityCalendar(BookingService bookingService, String vehicleId) {
        super(new BorderLayout(0, UIConstants.SPACING_SM));
        this.bookingService = bookingService;
        this.vehicleId = vehicleId;
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        monthLabel.setFont(UIConstants.FONT_BODY_BOLD);
        monthLabel.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        prevButton.addActionListener(e -> showMonth(shown.minusMonths(1)));
        nextButton.addActionListener(e -> showMonth(shown.plusMonths(1)));
        header.add(prevButton, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(nextButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        grid.setOpaque(false);
        JPanel south = new JPanel(new BorderLayout(0, UIConstants.SPACING_XS));
        south.setOpaque(false);
        statusLabel.setFont(UIConstants.FONT_SMALL);
        statusLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(legend(), BorderLayout.SOUTH);
        // the grid keeps its own height (it doesn't stretch in a tall window); the status sits right under it
        JPanel body = new JPanel(new BorderLayout(0, UIConstants.SPACING_SM));
        body.setOpaque(false);
        body.add(grid, BorderLayout.NORTH);
        JPanel rest = new JPanel(new BorderLayout());
        rest.setOpaque(false);
        rest.add(south, BorderLayout.NORTH);
        body.add(rest, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        reload();
        setStatus("Click a free day to start, then click your return day.", false);
    }

    /** Called with (start, return day) whenever the user picks a complete, free range. */
    public void setOnSelect(BiConsumer<LocalDate, LocalDate> onSelect) {
        this.onSelect = onSelect;
    }

    /** Fetches the booked days again (e.g. after a booking was made). */
    public void reload() {
        periods = bookingService.getBookedPeriods(vehicleId);
        rebuild();
    }

    /**
     * Shows dates chosen elsewhere (the date fields), without calling the listener. The calendar
     * moves to the start's month, and says whether the range is free.
     */
    public void setSelection(LocalDate start, LocalDate end) {
        selStart = start;
        selEnd = end != null && start != null && end.isAfter(start) ? end : null;
        if (start != null && !YearMonth.from(start).equals(shown)) {
            shown = clampMonth(YearMonth.from(start));
        }
        rebuild();
        describeSelection();
    }

    private void describeSelection() {
        if (selStart == null || selEnd == null) return;
        LocalDate clash = BookingService.firstBookedDay(periods, selStart, selEnd);
        if (clash != null) {
            setStatus("Not available: already booked on " + clash + ".", true);
        } else {
            long days = Booking.calculateDays(selStart, selEnd);
            setStatus("Free: " + selStart + " to " + selEnd + " (" + days + (days == 1 ? " day" : " days") + ").", false);
        }
    }

    // ===== Day states =====

    DayState stateOf(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.isBefore(today)) return DayState.PAST;
        if (day.isAfter(today.plusDays(BookingService.MAX_DAYS_AHEAD))) return DayState.TOO_FAR;
        for (BookingService.BookedPeriod p : periods) {
            if (p.covers(day)) return p.isMine() ? DayState.MINE : DayState.BOOKED;
        }
        return DayState.FREE;
    }

    private String tooltipOf(LocalDate day, DayState state) {
        switch (state) {
            case PAST: return day + ": in the past";
            case TOO_FAR: return day + ": more than " + BookingService.MAX_DAYS_AHEAD + " days ahead";
            case FREE: return day + ": free";
            default:
                for (BookingService.BookedPeriod p : periods) {
                    if (!p.covers(day)) continue;
                    String who = p.getBookingId() == null ? "" : " (" + p.getBookingId() + ", " + p.getCustomerId() + ")";
                    return day + ": " + (p.isMine() ? "your booking" : "booked") + who
                            + ", free again from " + p.getEndExclusive();
                }
                return day + ": booked";
        }
    }

    // ===== Clicking =====

    /** A click on a day: the first picks the start, the second the return day. */
    void clickDay(LocalDate day) {
        DayState state = stateOf(day);
        boolean picking = selStart != null && selEnd == null;
        if (picking && day.isAfter(selStart)) {
            LocalDate clash = BookingService.firstBookedDay(periods, selStart, day);
            long days = Booking.calculateDays(selStart, day);
            if (clash != null) {
                setStatus("Booked on " + clash + ": pick a return day on or before " + clash + ".", true);
                return;
            }
            if (days > BookingService.MAX_RENTAL_DAYS) {
                setStatus("A rental can be at most " + BookingService.MAX_RENTAL_DAYS + " days.", true);
                return;
            }
            if (state == DayState.TOO_FAR) {
                setStatus("That return day is too far ahead.", true);
                return;
            }
            selEnd = day;
            rebuild();
            describeSelection();
            if (onSelect != null) onSelect.accept(selStart, selEnd);
            return;
        }
        // (re)start the selection
        if (state != DayState.FREE) {
            setStatus(state == DayState.PAST ? "That day has passed." : state == DayState.TOO_FAR
                    ? "Bookings can be made at most " + BookingService.MAX_DAYS_AHEAD + " days ahead."
                    : "That day is already booked.", true);
            return;
        }
        selStart = day;
        selEnd = null;
        rebuild();
        setStatus("Start " + day + ". Now click your return day.", false);
    }

    LocalDate getSelectionStart() { return selStart; }
    LocalDate getSelectionEnd() { return selEnd; }
    String getStatusText() { return statusLabel.getText(); }
    YearMonth getShownMonth() { return shown; }

    // ===== Drawing =====

    private YearMonth clampMonth(YearMonth m) {
        YearMonth first = YearMonth.now();
        YearMonth last = YearMonth.from(LocalDate.now().plusDays(BookingService.MAX_DAYS_AHEAD));
        return m.isBefore(first) ? first : m.isAfter(last) ? last : m;
    }

    void showMonth(YearMonth month) {
        shown = clampMonth(month);
        rebuild();
    }

    private void rebuild() {
        grid.removeAll();
        monthLabel.setText(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + shown.getYear());
        prevButton.setEnabled(shown.isAfter(YearMonth.now()));
        nextButton.setEnabled(shown.isBefore(YearMonth.from(LocalDate.now().plusDays(BookingService.MAX_DAYS_AHEAD))));
        for (DayOfWeek d : DayOfWeek.values()) {
            JLabel l = new JLabel(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2), SwingConstants.CENTER);
            l.setFont(UIConstants.FONT_SMALL);
            l.setForeground(UIConstants.DARK_TEXT_MUTED);
            grid.add(l);
        }
        LocalDate first = shown.atDay(1);
        int blanks = first.getDayOfWeek().getValue() - 1; // weeks start on Monday
        for (int i = 0; i < blanks; i++) grid.add(blank());
        for (int d = 1; d <= shown.lengthOfMonth(); d++) grid.add(new DayCell(shown.atDay(d)));
        for (int i = blanks + shown.lengthOfMonth(); i < 42; i++) grid.add(blank());
        grid.revalidate();
        grid.repaint();
    }

    private static JComponent blank() {
        JLabel l = new JLabel();
        l.setOpaque(false);
        return l;
    }

    private boolean inSelection(LocalDate day) {
        if (selStart == null) return false;
        if (selEnd == null) return day.equals(selStart);
        return !day.isBefore(selStart) && day.isBefore(selEnd);
    }

    private void setStatus(String text, boolean problem) {
        statusLabel.setText(text);
        statusLabel.setForeground(problem ? UIConstants.DANGER : UIConstants.DARK_TEXT_MUTED);
    }

    private JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 0));
        p.setOpaque(false);
        p.add(swatch(FREE_BG, "Free"));
        p.add(swatch(BOOKED_BG, "Booked"));
        p.add(swatch(MINE_BG, "Yours"));
        p.add(swatch(UIConstants.DARK_ACCENT_BLUE, "Selected"));
        return p;
    }

    private static JComponent swatch(Color color, String text) {
        JLabel l = new JLabel(text, new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRoundRect(x, y, 10, 10, 3, 3);
            }
            @Override public int getIconWidth() { return 10; }
            @Override public int getIconHeight() { return 10; }
        }, SwingConstants.LEFT);
        l.setFont(UIConstants.FONT_SMALL);
        l.setForeground(UIConstants.DARK_TEXT_MUTED);
        return l;
    }

    private static JButton navButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 10, 0, 10));
        return b;
    }

    /** One day of the month. */
    private final class DayCell extends JComponent {
        private final LocalDate day;
        private final DayState state;

        DayCell(LocalDate day) {
            this.day = day;
            this.state = stateOf(day);
            setPreferredSize(new Dimension(34, 30));
            setToolTipText(tooltipOf(day, state));
            boolean clickable = state != DayState.PAST && state != DayState.TOO_FAR;
            setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    clickDay(day);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean selected = inSelection(day);
            Color bg = selected ? UIConstants.DARK_ACCENT_BLUE
                    : state == DayState.FREE ? FREE_BG
                    : state == DayState.BOOKED ? BOOKED_BG
                    : state == DayState.MINE ? MINE_BG : null;
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(1, 1, w - 2, h - 2, 8, 8);
            }
            if (day.equals(selEnd)) { // the return day: outlined
                g2.setColor(UIConstants.DARK_ACCENT_BLUE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(2, 2, w - 4, h - 4, 8, 8);
            }
            if (day.equals(LocalDate.now())) {
                g2.setColor(UIConstants.CREST_GOLD);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            }
            String text = String.valueOf(day.getDayOfMonth());
            g2.setFont(selected ? UIConstants.FONT_BODY_BOLD : UIConstants.FONT_BODY);
            g2.setColor(selected ? Color.WHITE
                    : state == DayState.PAST || state == DayState.TOO_FAR ? UIConstants.DARK_TEXT_MUTED
                    : UIConstants.DARK_TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2, ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, tx, ty);
            if (state == DayState.BOOKED && !selected) { // struck through, readable without colour
                g2.setColor(UIConstants.DANGER);
                g2.drawLine(tx - 2, ty - fm.getAscent() / 2 + 1, tx + fm.stringWidth(text) + 2, ty - fm.getAscent() / 2 + 1);
            }
            g2.dispose();
        }
    }
}
