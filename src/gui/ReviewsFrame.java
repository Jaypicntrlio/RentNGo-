package gui;

import model.Review;
import model.exceptions.BookingException;
import service.AuthService;
import service.ReviewService;
import service.VehicleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Admin "Reviews" window: every review (hidden ones too) with Hide / Show. Hiding takes a review
 * out of the averages and out of what customers see; it can be shown again. Both are audited.
 */
public class ReviewsFrame extends JFrame {
    private static final String VISIBLE = "Visible";
    private static final String HIDDEN = "Hidden";

    private final AuthService authService;
    private final ReviewService reviewService;
    private final VehicleService vehicleService;

    private DefaultTableModel tableModel;
    private JTable table;
    private JButton toggleButton;

    public ReviewsFrame(JFrame parent, AuthService authService, ReviewService reviewService, VehicleService vehicleService) {
        this.authService = authService;
        this.reviewService = reviewService;
        this.vehicleService = vehicleService;
        UIConstants.styleDarkFrame(this, "RentNGo - Reviews", 1100, 580);
        setMinimumSize(new Dimension(900, 480));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_MD));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("Customer Reviews"), BorderLayout.WEST);
        toggleButton = UIConstants.createDangerButton("Hide Review");
        toggleButton.addActionListener(e -> toggleSelected());
        header.add(toggleButton, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        String[] columns = {"Review", "Date", "Booking", "Vehicle", "Customer", "Rating", "Comment", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = UIConstants.createDarkStyledTable(tableModel);
        int[] widths = {90, 90, 80, 150, 80, 90, 360, 80};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(5).setCellRenderer(new StarsRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(new StatusRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateToggleButton();
        });
        root.add(UIConstants.createDarkScrollPane(table), BorderLayout.CENTER);

        root.add(UIConstants.createDarkMutedLabel("Hidden reviews don't count in a vehicle's rating and customers can't see them. "
                + "Hiding and showing are recorded in the Audit Log."), BorderLayout.SOUTH);
        add(root);
        loadReviews();
    }

    private void loadReviews() {
        tableModel.setRowCount(0);
        for (Review r : reviewService.getAllReviews()) {
            String vehicle = vehicleService.getVehicleById(r.getVehicleId())
                    .map(v -> v.getVehicleId() + " " + v.getBrand() + " " + v.getModel())
                    .orElse(r.getVehicleId() + " (deleted)");
            tableModel.addRow(new Object[]{r.getReviewId(), r.getDate().toString(), r.getBookingId(), vehicle,
                    r.getCustomerId(), Review.stars(r.getRating()), r.getComment().isEmpty() ? "-" : r.getComment(),
                    r.isHidden() ? HIDDEN : VISIBLE});
        }
        updateToggleButton();
    }

    private void updateToggleButton() {
        int row = table.getSelectedRow();
        boolean hidden = row != -1 && HIDDEN.equals(tableModel.getValueAt(row, 7));
        toggleButton.setText(hidden ? "Show Review" : "Hide Review");
        toggleButton.setEnabled(row != -1);
    }

    private void toggleSelected() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        int row = table.getSelectedRow();
        if (row == -1) return;
        String reviewId = (String) tableModel.getValueAt(row, 0);
        boolean hide = VISIBLE.equals(tableModel.getValueAt(row, 7));
        if (hide && JOptionPane.showConfirmDialog(this, "Hide review " + reviewId + "? Customers won't see it and it won't count in the rating.",
                "Hide Review", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            reviewService.setHidden(reviewId, hide);
            loadReviews();
            table.setRowSelectionInterval(row, row);
        } catch (BookingException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reviews", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Color rowBackground(boolean selected, int row) {
        return selected ? UIConstants.DARK_ROW_SELECTED : (row % 2 == 0 ? UIConstants.DARK_CARD : UIConstants.DARK_BG);
    }

    private static class StarsRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(new Font("Segoe UI Symbol", Font.PLAIN, 14));
            c.setForeground(UIConstants.CREST_GOLD);
            c.setBackground(rowBackground(isSelected, row));
            return c;
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UIConstants.FONT_BODY_BOLD);
            c.setForeground(VISIBLE.equals(value) ? UIConstants.SUCCESS : UIConstants.DANGER);
            c.setBackground(rowBackground(isSelected, row));
            return c;
        }
    }
}
