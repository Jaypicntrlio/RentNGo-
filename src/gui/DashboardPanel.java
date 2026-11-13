package gui;

import model.Booking;
import model.BookingStatus;
import model.Customer;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.ReportService;
import service.VehicleService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Dark "sales overview" style dashboard: KPI row with trend arrows, a
 * monthly revenue line chart, a revenue-by-vehicle-type bar chart, and a
 * recent rentals list. All figures computed live, no charting library.
 */
public class DashboardPanel extends JPanel {
    private final VehicleService vehicleService;
    private final AuthService authService;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    private JPanel kpiRow;
    private LineAreaChartPanel revenueChart;
    private BarChartPanel typeChart;
    private JPanel recentListBody;
    private JComboBox<String> rangeCombo;

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    public DashboardPanel(VehicleService vehicleService, AuthService authService,
                           BookingService bookingService, PaymentService paymentService) {
        this.vehicleService = vehicleService;
        this.authService = authService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        setBackground(UIConstants.DARK_BG);
        buildUI();
        refresh();
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, UIConstants.SPACING_LG));
        setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        kpiRow = new JPanel(new GridLayout(1, 4, UIConstants.SPACING_LG, 0));
        kpiRow.setOpaque(false);

        JPanel chartsRow = new JPanel(new GridLayout(1, 2, UIConstants.SPACING_LG, 0));
        chartsRow.setOpaque(false);
        chartsRow.add(buildRevenueChartCard());
        chartsRow.add(buildTypeChartCard());

        JPanel listCard = buildRecentRentalsCard();

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        chartsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        listCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(chartsRow);
        stack.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));
        stack.add(listCard);

        add(kpiRow, BorderLayout.NORTH);
        add(stack, BorderLayout.CENTER);
    }

    private JPanel darkCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, UIConstants.SPACING_SM));
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD)
        ));
        if (title != null) {
            JLabel heading = new JLabel(title);
            heading.setFont(UIConstants.FONT_SUBHEADING);
            heading.setForeground(UIConstants.DARK_TEXT_PRIMARY);
            card.add(heading, BorderLayout.NORTH);
        }
        return card;
    }

    private JPanel buildRevenueChartCard() {
        JPanel card = darkCard(null);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Monthly Revenue");
        title.setFont(UIConstants.FONT_SUBHEADING);
        title.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        header.add(title, BorderLayout.WEST);

        rangeCombo = new JComboBox<>(new String[]{"This Year", "Last 6 Months"});
        UIConstants.styleDarkCombo(rangeCombo);
        rangeCombo.setFont(UIConstants.FONT_SMALL);
        rangeCombo.setPreferredSize(new Dimension(130, 28));
        rangeCombo.addActionListener(e -> refreshRevenueChart());
        header.add(rangeCombo, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        revenueChart = new LineAreaChartPanel(MONTHS, new double[]{0, 0}, UIConstants.DARK_ACCENT_BLUE);
        revenueChart.setPreferredSize(new Dimension(420, 220));
        card.add(revenueChart, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildTypeChartCard() {
        JPanel card = darkCard("Revenue by Vehicle Type");
        typeChart = new BarChartPanel(new String[]{"Car", "SUV", "Truck"}, new double[3], UIConstants.DARK_ACCENT_BLUE);
        typeChart.setDarkMode(true);
        typeChart.setPreferredSize(new Dimension(420, 220));
        card.add(typeChart, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildRecentRentalsCard() {
        JPanel card = darkCard("Recent Rentals");
        recentListBody = new JPanel();
        recentListBody.setOpaque(false);
        recentListBody.setLayout(new BoxLayout(recentListBody, BoxLayout.Y_AXIS));
        recentListBody.setBorder(new EmptyBorder(UIConstants.SPACING_SM, 0, 0, 0));
        card.add(recentListBody, BorderLayout.CENTER);
        return card;
    }

    /** Recomputes every figure from the live services and repaints the dashboard. */
    public void refresh() {
        refreshKpis();
        refreshRevenueChart();
        refreshTypeChart();
        refreshRecentRentals();
    }

    // ===== KPI row =====

    private void refreshKpis() {
        kpiRow.removeAll();

        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);

        List<Booking> bookings = bookingService.getAllBookings().stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.toList());

        // revenue is money received (payments minus refunds), so cancelled bookings are included
        List<Booking> allBookings = bookingService.getAllBookings();
        java.math.BigDecimal revenueThis = sumRevenue(allBookings, thisMonth);
        java.math.BigDecimal revenueLast = sumRevenue(allBookings, lastMonth);

        long countThis = countInMonth(bookings, thisMonth);
        long countLast = countInMonth(bookings, lastMonth);

        long customersThis = customersRegisteredIn(thisMonth);
        long customersLast = customersRegisteredIn(lastMonth);

        java.math.BigDecimal avgThis = average(revenueThis, countThis);
        java.math.BigDecimal avgLast = average(revenueLast, countLast);

        kpiRow.add(kpiCard("Revenue", String.format("\u20B1%,.2f", revenueThis), percentChange(revenueLast.doubleValue(), revenueThis.doubleValue())));
        kpiRow.add(kpiCard("Rentals", String.valueOf(countThis), percentChange(countLast, countThis)));
        kpiRow.add(kpiCard("New Customers", String.valueOf(customersThis), percentChange(customersLast, customersThis)));
        kpiRow.add(kpiCard("Avg. Rental", String.format("\u20B1%,.2f", avgThis), percentChange(avgLast.doubleValue(), avgThis.doubleValue())));

        kpiRow.revalidate();
        kpiRow.repaint();
    }

    private java.math.BigDecimal sumRevenue(List<Booking> bookings, YearMonth month) {
        return bookings.stream()
                .filter(b -> YearMonth.from(b.getStartDate()).equals(month))
                .map(Booking::getNetPaid)
                .reduce(model.Money.ZERO, java.math.BigDecimal::add);
    }

    private static java.math.BigDecimal average(java.math.BigDecimal total, long count) {
        return count > 0
                ? total.divide(java.math.BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
                : model.Money.ZERO;
    }

    private long countInMonth(List<Booking> bookings, YearMonth month) {
        return bookings.stream().filter(b -> YearMonth.from(b.getStartDate()).equals(month)).count();
    }

    private long customersRegisteredIn(YearMonth month) {
        return authService.getAllCustomers().stream()
                .filter(c -> c.getRegisteredDate() != null && YearMonth.from(c.getRegisteredDate()).equals(month))
                .count();
    }

    private Double percentChange(double previous, double current) {
        if (previous == 0) return current == 0 ? null : 100.0;
        return ((current - previous) / previous) * 100;
    }

    private JPanel kpiCard(String label, String value, Double changePct) {
        JPanel card = darkCard(null);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(UIConstants.FONT_SMALL);
        labelComp.setForeground(UIConstants.DARK_TEXT_MUTED);
        card.add(labelComp, BorderLayout.NORTH);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, 26));
        valueComp.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        card.add(valueComp, BorderLayout.CENTER);

        if (changePct != null) {
            boolean up = changePct >= 0;
            JLabel trend = new JLabel((up ? "↑ " : "↓ ") + String.format("%.1f%%", Math.abs(changePct)));
            trend.setFont(UIConstants.FONT_SMALL);
            trend.setForeground(up ? UIConstants.DARK_TREND_UP : UIConstants.DARK_TREND_DOWN);
            card.add(trend, BorderLayout.SOUTH);
        }

        return card;
    }

    // ===== Monthly revenue line chart =====

    private void refreshRevenueChart() {
        boolean fullYear = rangeCombo == null || "This Year".equals(rangeCombo.getSelectedItem());
        List<Booking> bookings = bookingService.getAllBookings();

        YearMonth end = YearMonth.now();
        int monthsBack = fullYear ? end.getMonthValue() - 1 : 5;
        YearMonth start = end.minusMonths(monthsBack);

        Map<YearMonth, Double> totals = new TreeMap<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            totals.put(m, 0.0);
        }
        for (Booking b : bookings) {
            YearMonth m = YearMonth.from(b.getStartDate());
            if (totals.containsKey(m)) {
                totals.put(m, totals.get(m) + b.getNetPaid().doubleValue());
            }
        }

        String[] labels = new String[totals.size()];
        double[] values = new double[totals.size()];
        int i = 0;
        for (Map.Entry<YearMonth, Double> entry : totals.entrySet()) {
            labels[i] = MONTHS[entry.getKey().getMonthValue() - 1];
            values[i] = entry.getValue();
            i++;
        }
        revenueChart.setData(labels, values);
    }

    // ===== Revenue by vehicle type =====

    private void refreshTypeChart() {
        // Look up every vehicle's type once, then total the revenue per type in one pass over the bookings
        // (before, each booking searched the vehicle list and copied a vehicle).
        Map<String, String> typeById = vehicleService.getTypesById();
        Map<String, Double> revenue = new LinkedHashMap<>();
        for (String type : ReportService.VEHICLE_TYPES) revenue.put(type, 0.0);
        for (Booking b : bookingService.getAllBookings()) {
            String type = typeById.get(b.getVehicleId());
            if (revenue.containsKey(type)) revenue.merge(type, b.getNetPaid().doubleValue(), Double::sum);
        }
        typeChart.setData(revenue.keySet().toArray(new String[0]),
                revenue.values().stream().mapToDouble(Double::doubleValue).toArray());
    }

    // ===== Recent rentals =====

    private void refreshRecentRentals() {
        recentListBody.removeAll();

        List<Booking> all = bookingService.getAllBookings();
        List<Booking> recent = all.size() <= 5
                ? all
                : all.subList(all.size() - 5, all.size());

        if (recent.isEmpty()) {
            JLabel empty = new JLabel("No rentals yet");
            empty.setFont(UIConstants.FONT_SMALL);
            empty.setForeground(UIConstants.DARK_TEXT_MUTED);
            recentListBody.add(empty);
        } else {
            for (int i = recent.size() - 1; i >= 0; i--) {
                recentListBody.add(recentRow(recent.get(i)));
            }
        }

        recentListBody.revalidate();
        recentListBody.repaint();
    }

    private JPanel recentRow(Booking booking) {
        JPanel row = new JPanel(new BorderLayout(UIConstants.SPACING_MD, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(UIConstants.SPACING_SM, 0, UIConstants.SPACING_SM, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        String customerName = authService.getAllUsers().stream()
                .filter(u -> u.getUserId().equals(booking.getCustomerId()))
                .map(model.User::getFullName)
                .findFirst()
                .orElse("Unknown");

        JLabel left = new JLabel("#" + booking.getBookingId() + " · " + customerName);
        left.setFont(UIConstants.FONT_BODY);
        left.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        row.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MD, 0));
        right.setOpaque(false);

        JLabel amount = new JLabel(String.format("\u20B1%,.2f", booking.getFinalAmount()));
        amount.setFont(UIConstants.FONT_BODY_BOLD);
        amount.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        right.add(amount);

        right.add(statusPill(booking));
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private JLabel statusPill(Booking booking) {
        String text;
        Color color;
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            text = "Cancelled";
            color = UIConstants.DARK_TREND_DOWN;
        } else if (booking.isPaid()) {
            text = "Paid";
            color = UIConstants.DARK_TREND_UP;
        } else {
            text = "Pending";
            color = UIConstants.WARNING;
        }

        JLabel pill = new JLabel(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setFont(UIConstants.FONT_SMALL);
        pill.setForeground(color);
        pill.setHorizontalAlignment(SwingConstants.CENTER);
        pill.setBorder(new EmptyBorder(3, 10, 3, 10));
        return pill;
    }
}
