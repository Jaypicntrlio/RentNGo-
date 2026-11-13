package gui;

import service.ReportService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Admin "Reports" view: total revenue, revenue this month, most-rented vehicle, active rentals
 * and bookings per vehicle type. Figures come from ReportService and are recomputed on refresh().
 */
public class ReportsPanel extends JPanel {
    private final ReportService reportService;

    private JPanel kpiRow;
    private BarChartPanel typeChart;
    private JPanel breakdownBody;

    public ReportsPanel(ReportService reportService) {
        this.reportService = reportService;
        setBackground(UIConstants.DARK_BG);
        buildUI();
        refresh();
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, UIConstants.SPACING_LG));
        setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        kpiRow = new JPanel(new GridLayout(1, 4, UIConstants.SPACING_LG, 0));
        kpiRow.setOpaque(false);
        add(kpiRow, BorderLayout.NORTH);

        JPanel typeCard = card("Bookings per Vehicle Type");
        JPanel body = new JPanel(new BorderLayout(UIConstants.SPACING_XL, 0));
        body.setOpaque(false);

        typeChart = new BarChartPanel(ReportService.VEHICLE_TYPES, new double[3], UIConstants.DARK_ACCENT_BLUE);
        typeChart.setDarkMode(true);
        typeChart.setCountValues(true);
        typeChart.setPreferredSize(new Dimension(420, 240));
        body.add(typeChart, BorderLayout.CENTER);

        breakdownBody = new JPanel();
        breakdownBody.setOpaque(false);
        breakdownBody.setLayout(new BoxLayout(breakdownBody, BoxLayout.Y_AXIS));
        breakdownBody.setPreferredSize(new Dimension(300, 0));
        body.add(breakdownBody, BorderLayout.EAST);
        typeCard.add(body, BorderLayout.CENTER);

        JLabel note = new JLabel("Revenue is payments received minus refunds. Cancelled bookings are not counted.");
        note.setFont(UIConstants.FONT_SMALL);
        note.setForeground(UIConstants.DARK_TEXT_MUTED);
        typeCard.add(note, BorderLayout.SOUTH);

        add(typeCard, BorderLayout.CENTER);
    }

    private JPanel card(String title) {
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

    /** Recomputes every figure from the live services. */
    public void refresh() {
        refreshKpis();
        refreshTypeBreakdown();
    }

    private void refreshKpis() {
        kpiRow.removeAll();
        YearMonth month = YearMonth.now();

        kpiRow.add(kpiCard("Total Revenue", peso(reportService.getTotalRevenue()), "All time"));
        kpiRow.add(kpiCard("Revenue This Month", peso(reportService.getRevenueForMonth(month)),
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))));

        long active = reportService.getActiveRentalCount();
        kpiRow.add(kpiCard("Active Rentals", String.valueOf(active), active == 1 ? "Vehicle out now" : "Vehicles out now"));

        Optional<ReportService.VehicleRentals> top = reportService.getMostRentedVehicle();
        if (top.isPresent()) {
            ReportService.VehicleRentals t = top.get();
            kpiRow.add(kpiCard("Most-Rented Vehicle", t.getName(),
                    t.getRentals() + (t.getRentals() == 1 ? " booking" : " bookings") + " · " + t.getVehicleId()));
        } else {
            kpiRow.add(kpiCard("Most-Rented Vehicle", "—", "No bookings yet"));
        }

        kpiRow.revalidate();
        kpiRow.repaint();
    }

    private JPanel kpiCard(String label, String value, String detail) {
        JPanel card = card(null);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(UIConstants.FONT_SMALL);
        labelComp.setForeground(UIConstants.DARK_TEXT_MUTED);
        card.add(labelComp, BorderLayout.NORTH);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(new Font("Segoe UI", Font.BOLD, value.length() > 12 ? 20 : 26));
        valueComp.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        valueComp.setToolTipText(value);
        card.add(valueComp, BorderLayout.CENTER);

        JLabel detailComp = new JLabel(detail);
        detailComp.setFont(UIConstants.FONT_SMALL);
        detailComp.setForeground(UIConstants.CREST_GOLD);
        card.add(detailComp, BorderLayout.SOUTH);
        return card;
    }

    private void refreshTypeBreakdown() {
        Map<String, Long> byType = reportService.getBookingsByVehicleType();
        long total = byType.values().stream().mapToLong(Long::longValue).sum();

        double[] values = new double[ReportService.VEHICLE_TYPES.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = byType.getOrDefault(ReportService.VEHICLE_TYPES[i], 0L);
        }
        typeChart.setData(ReportService.VEHICLE_TYPES, values);

        breakdownBody.removeAll();
        breakdownBody.add(breakdownRow("Type", "Bookings", "Share", true));
        for (String type : ReportService.VEHICLE_TYPES) {
            long count = byType.getOrDefault(type, 0L);
            String share = total == 0 ? "—" : String.format("%.0f%%", count * 100.0 / total);
            breakdownBody.add(breakdownRow(type, String.valueOf(count), share, false));
        }
        breakdownBody.add(breakdownRow("Total", String.valueOf(total), total == 0 ? "—" : "100%", true));
        breakdownBody.add(Box.createVerticalGlue());
        breakdownBody.revalidate();
        breakdownBody.repaint();
    }

    private JPanel breakdownRow(String type, String count, String share, boolean emphasis) {
        JPanel row = new JPanel(new GridLayout(1, 3));
        row.setOpaque(false);
        row.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        String[] cells = {type, count, share};
        for (int i = 0; i < cells.length; i++) {
            JLabel l = new JLabel(cells[i]);
            l.setFont(emphasis ? UIConstants.FONT_BODY_BOLD : UIConstants.FONT_BODY);
            l.setForeground(emphasis ? UIConstants.DARK_TEXT_PRIMARY : UIConstants.DARK_TEXT_MUTED);
            if (i > 0) l.setHorizontalAlignment(SwingConstants.RIGHT);
            row.add(l);
        }
        return row;
    }

    private static String peso(java.math.BigDecimal amount) {
        return String.format("₱%,.2f", amount);
    }
}
