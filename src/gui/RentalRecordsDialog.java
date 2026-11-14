package gui;

import model.Booking;
import model.BookingStatus;
import model.Customer;
import model.User;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.VehicleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rental records with search, status filter and CSV export. Admins see every booking; a customer
 * sees only their own (via BookingService.getBookingsVisibleTo) and no customer-name column.
 */
public class RentalRecordsDialog extends JDialog {
    private BookingService bookingService;
    private VehicleService vehicleService;
    private AuthService authService;
    private final User viewer;
    /** Only admins see (and export) customer names. */
    private final boolean showCustomerColumn;
    private int statusColumn;

    private JTable recordsTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JLabel recordsCountLabel;

    /** @param viewer the logged-in user; decides which bookings are shown and exported */
    public RentalRecordsDialog(JFrame parent, User viewer, BookingService bookingService, VehicleService vehicleService, AuthService authService) {
        super(parent, viewer instanceof Customer ? "My Rental Records" : "Rental Records", true);
        this.viewer = viewer;
        this.showCustomerColumn = !(viewer instanceof Customer);
        this.bookingService = bookingService;
        this.vehicleService = vehicleService;
        this.authService = authService;
        initializeUI();
        loadRecords();
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, getTitle());
        setSize(1150, 700);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());

        // Background
        JPanel backgroundPanel = new JPanel(new BorderLayout());
        backgroundPanel.setBackground(UIConstants.DARK_BG);

        // Header Panel
        JPanel headerPanel = createHeaderPanel();
        backgroundPanel.add(headerPanel, BorderLayout.NORTH);

        // Center - Table
        JPanel tablePanel = createTablePanel();
        backgroundPanel.add(tablePanel, BorderLayout.CENTER);

        // Bottom - Status bar
        JPanel statusPanel = createStatusPanel();
        backgroundPanel.add(statusPanel, BorderLayout.SOUTH);

        add(backgroundPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.SPACING_LG, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_LG, UIConstants.SPACING_XL)
        ));

        // Title
        JLabel titleLabel = UIConstants.createDarkHeadingLabel(getTitle());
        panel.add(titleLabel, BorderLayout.WEST);

        // Right side - Search and Filter
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MD, 0));
        rightPanel.setBackground(UIConstants.DARK_CARD);

        // Status Filter
        JLabel filterLabel = UIConstants.createDarkBodyLabel("Status:");
        rightPanel.add(filterLabel);

        statusFilterCombo = UIConstants.createDarkComboBox(new String[]{"All", "PENDING", "CONFIRMED", "ACTIVE", "COMPLETED", "CANCELLED"});
        statusFilterCombo.setPreferredSize(new Dimension(150, UIConstants.INPUT_HEIGHT));
        statusFilterCombo.addActionListener(e -> applyFilters());
        rightPanel.add(statusFilterCombo);

        // Search Field
        JLabel searchLabel = UIConstants.createDarkBodyLabel("Search:");
        rightPanel.add(searchLabel);

        searchField = UIConstants.createDarkTextField(25);
        searchField.setPreferredSize(new Dimension(250, UIConstants.INPUT_HEIGHT));
        searchField.putClientProperty("JTextField.placeholderText",
                showCustomerColumn ? "Search by ID, customer, vehicle..." : "Search by ID or vehicle...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        rightPanel.add(searchField);

        // Refresh Button
        JButton refreshButton = UIConstants.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadRecords());
        rightPanel.add(refreshButton);

        // Export Button
        JButton exportButton = UIConstants.createSuccessButton("Export CSV");
        exportButton.addActionListener(e -> exportToCSV());
        rightPanel.add(exportButton);

        panel.add(rightPanel, BorderLayout.EAST);
        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_BG);
        panel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, 0, UIConstants.SPACING_XL));

        List<String> columnNames = new java.util.ArrayList<>(List.of("Rental ID", "Customer", "Vehicle", "Start Date", "End Date", "Total Amount", "Status"));
        List<Integer> widths = new java.util.ArrayList<>(List.of(100, 160, 200, 120, 120, 130, 130));
        if (!showCustomerColumn) {
            // left out of the model entirely, so it can't be shown or exported
            columnNames.remove(1);
            widths.remove(1);
        }
        String[] columns = columnNames.toArray(new String[0]);
        statusColumn = columns.length - 1;
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        recordsTable = UIConstants.createDarkStyledTable(tableModel);

        // Column widths
        for (int i = 0; i < widths.size(); i++) {
            recordsTable.getColumnModel().getColumn(i).setPreferredWidth(widths.get(i));
        }

        // Custom renderer for Status column
        recordsTable.getColumnModel().getColumn(statusColumn).setCellRenderer(new StatusCellRenderer());

        rowSorter = new TableRowSorter<>(tableModel);
        recordsTable.setRowSorter(rowSorter);

        JScrollPane scrollPane = UIConstants.createDarkScrollPane(recordsTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_XL, UIConstants.SPACING_MD, UIConstants.SPACING_XL)
        ));

        recordsCountLabel = UIConstants.createDarkMutedLabel("Total records: 0");
        panel.add(recordsCountLabel, BorderLayout.WEST);

        // Legend for status colors
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MD, 0));
        legendPanel.setBackground(UIConstants.DARK_CARD);
        legendPanel.add(createLegendItem("Rented", UIConstants.STATUS_CONFIRMED_FG, UIConstants.STATUS_CONFIRMED_BG));
        legendPanel.add(createLegendItem("Returned", UIConstants.STATUS_COMPLETED_FG, UIConstants.STATUS_COMPLETED_BG));
        legendPanel.add(createLegendItem("Pending", UIConstants.STATUS_PENDING_FG, UIConstants.STATUS_PENDING_BG));
        legendPanel.add(createLegendItem("Confirmed", UIConstants.STATUS_CONFIRMED_FG, UIConstants.STATUS_CONFIRMED_BG));
        legendPanel.add(createLegendItem("Cancelled", UIConstants.STATUS_CANCELLED_FG, UIConstants.STATUS_CANCELLED_BG));
        panel.add(legendPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createLegendItem(String text, Color fgColor, Color bgColor) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_XS, 0));
        item.setBackground(UIConstants.DARK_CARD);
        JLabel colorBox = new JLabel("  ");
        colorBox.setOpaque(true);
        colorBox.setBackground(bgColor);
        colorBox.setBorder(new LineBorder(fgColor.darker()));
        colorBox.setPreferredSize(new Dimension(14, 14));
        item.add(colorBox);
        JLabel label = UIConstants.createDarkMutedLabel(text);
        label.setFont(UIConstants.FONT_SMALL);
        item.add(label);
        return item;
    }

    private void loadRecords() {
        tableModel.setRowCount(0);
        List<Booking> bookings = bookingService.getBookingsVisibleTo(viewer);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // customer names are only looked up for admins
        java.util.Map<String, String> customerNames = new java.util.HashMap<>();
        if (showCustomerColumn) {
            for (User u : authService.getAllUsers()) customerNames.put(u.getUserId(), u.getFullName());
        }

        for (Booking b : bookings) {
            String vehicleInfo = vehicleService.getVehicleById(b.getVehicleId())
                    .map(v -> v.getBrand() + " " + v.getModel() + " (" + v.getLicensePlate() + ")")
                    .orElse("Unknown");

            List<Object> row = new java.util.ArrayList<>();
            row.add(b.getBookingId());
            if (showCustomerColumn) row.add(customerNames.getOrDefault(b.getCustomerId(), "Unknown"));
            row.add(vehicleInfo);
            row.add(b.getStartDate().format(formatter));
            row.add(b.getEndDate().format(formatter));
            row.add(String.format("\u20B1%,.2f", b.getTotalCost()));
            row.add(b.getStatus());
            tableModel.addRow(row.toArray());
        }
        updateRecordsCount();
    }

    private void applyFilters() {
        RowFilter<DefaultTableModel, Object> searchFilter = RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(searchField.getText().trim()));
        RowFilter<DefaultTableModel, Object> statusFilter = RowFilter.regexFilter(".*");

        String selectedStatus = (String) statusFilterCombo.getSelectedItem();
        if (!"All".equals(selectedStatus)) {
            statusFilter = RowFilter.regexFilter("^" + selectedStatus + "$", statusColumn);
        }

        rowSorter.setRowFilter(RowFilter.andFilter(List.of(searchFilter, statusFilter)));
        updateRecordsCount();
    }

    private void updateRecordsCount() {
        int count = recordsTable.getRowCount();
        recordsCountLabel.setText("Showing " + count + " record" + (count != 1 ? "s" : ""));
    }

    /** Writes the rows as shown (search and status filter applied); CsvWriter quotes values and neutralises formulas. */
    void exportTo(java.io.File file) throws java.io.IOException {
        List<String> header = new java.util.ArrayList<>();
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            header.add(tableModel.getColumnName(i));
        }
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int row = 0; row < recordsTable.getRowCount(); row++) {
            int modelRow = recordsTable.convertRowIndexToModel(row);
            List<Object> values = new java.util.ArrayList<>();
            for (int col = 0; col < tableModel.getColumnCount(); col++) {
                values.add(tableModel.getValueAt(modelRow, col));
            }
            rows.add(values);
        }
        service.CsvWriter.write(file, header, rows);
    }

    private void exportToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Rental Records to CSV");
        fileChooser.setSelectedFile(new java.io.File("rental_records_" + java.time.LocalDate.now() + ".csv"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
            }
            if (!FileSaving.okToWrite(this, file)) return;
            try {
                exportTo(file);
                JOptionPane.showMessageDialog(this, "Exported successfully to " + file.getName(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Custom Cell Renderer for Status Column
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof BookingStatus) {
                BookingStatus status = (BookingStatus) value;
                setText(status.name());
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UIConstants.FONT_BODY_BOLD);

                Color bgColor, fgColor;
                switch (status) {
                    case CONFIRMED:
                    case ACTIVE:
                        bgColor = UIConstants.STATUS_CONFIRMED_BG;
                        fgColor = UIConstants.STATUS_CONFIRMED_FG;
                        break;
                    case COMPLETED:
                        bgColor = UIConstants.STATUS_COMPLETED_BG;
                        fgColor = UIConstants.STATUS_COMPLETED_FG;
                        break;
                    case PENDING:
                        bgColor = UIConstants.STATUS_PENDING_BG;
                        fgColor = UIConstants.STATUS_PENDING_FG;
                        break;
                    case CANCELLED:
                        bgColor = UIConstants.STATUS_CANCELLED_BG;
                        fgColor = UIConstants.STATUS_CANCELLED_FG;
                        break;
                    default:
                        bgColor = UIConstants.DARK_CARD;
                        fgColor = UIConstants.DARK_TEXT_PRIMARY;
                }

                if (!isSelected) {
                    c.setBackground(bgColor);
                } else {
                    c.setBackground(bgColor.darker());
                }
                c.setForeground(fgColor);
                setBorder(new EmptyBorder(6, 12, 6, 12));
            }
            return c;
        }
    }

    // Public method to refresh from outside (e.g., after booking created/returned)
    public void refreshRecords() {
        loadRecords();
    }
}