package gui;

import model.Booking;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.VehicleService;
import model.exceptions.ValidationException;
import model.exceptions.VehicleNotFoundException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ManageVehiclesFrame extends JFrame {
    private final VehicleService vehicleService;

    private JTable vehicleTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;

    private JLabel previewName;
    private JLabel previewPlate;
    private JLabel previewType;
    private JLabel previewPrice;
    private JLabel previewStatus;
    private SilhouettePanel previewImage;

    private BookingService bookingService;
    private final AuthService authService;

    public ManageVehiclesFrame(JFrame parent, AuthService authService, VehicleService vehicleService, BookingService bookingService) {
        this.authService = authService;
        this.bookingService = bookingService;
        this.vehicleService = vehicleService;
        initializeUI(parent);
        loadVehicles();
    }

    private void initializeUI(JFrame parent) {
        UIConstants.styleDarkFrame(this, "RentNGo - Manage Vehicles", 1150, 680);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.DARK_BG);

        root.add(createToolbar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(UIConstants.SPACING_LG, 0));
        center.setBackground(UIConstants.DARK_BG);
        center.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        center.add(createTablePanel(), BorderLayout.CENTER);
        center.add(createPreviewPanel(), BorderLayout.EAST);

        root.add(center, BorderLayout.CENTER);
        add(root);
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_LG)
        ));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 0));
        buttons.setBackground(UIConstants.DARK_CARD);

        JButton addButton = UIConstants.createSuccessButton("+ Add");
        addButton.addActionListener(e -> showVehicleDialog(null));
        buttons.add(addButton);

        JButton editButton = UIConstants.createPrimaryButton("Edit");
        editButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.EDIT));
        editButton.addActionListener(e -> editSelected());
        buttons.add(editButton);

        JButton deleteButton = UIConstants.createDangerButton("Delete");
        deleteButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.DELETE));
        deleteButton.addActionListener(e -> deleteSelected());
        buttons.add(deleteButton);

        panel.add(buttons, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        searchPanel.setBackground(UIConstants.DARK_CARD);
        searchField = UIConstants.createDarkTextField(20);
        searchField.setPreferredSize(new Dimension(220, UIConstants.INPUT_HEIGHT));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { loadVehicles(); }
            @Override public void removeUpdate(DocumentEvent e) { loadVehicles(); }
            @Override public void changedUpdate(DocumentEvent e) { loadVehicles(); }
        });
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setForeground(UIConstants.DARK_TEXT_MUTED);
        searchPanel.add(searchIcon);
        searchPanel.add(searchField);
        panel.add(searchPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_BG);

        String[] columns = {"ID", "Brand", "Model", "Type", "Year", "Plate No.", "Price/Day", "Status", "In Service"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        vehicleTable = UIConstants.createDarkStyledTable(tableModel);
        vehicleTable.getColumnModel().getColumn(7).setCellRenderer(new StatusRenderer());
        vehicleTable.getColumnModel().getColumn(8).setCellRenderer(new StatusRenderer());
        vehicleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview();
        });

        panel.add(UIConstants.createDarkScrollPane(vehicleTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));
        panel.setPreferredSize(new Dimension(280, 0));

        JLabel heading = UIConstants.createDarkSubheadingLabel("Vehicle Details");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        previewImage = new SilhouettePanel(SilhouettePanel.Kind.CAR);
        previewImage.setDarkMode(true);
        previewImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewImage.setMaximumSize(new Dimension(260, 170));
        panel.add(previewImage);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        previewName = UIConstants.createDarkSubheadingLabel("Select a vehicle");
        previewName.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewName);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        previewPlate = UIConstants.createDarkSecondaryLabel("Plate No.: -");
        previewPlate.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewPlate);

        previewType = UIConstants.createDarkSecondaryLabel("Type: -");
        previewType.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewType);

        previewPrice = UIConstants.createDarkSecondaryLabel("Price/Day: -");
        previewPrice.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewPrice);

        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));
        previewStatus = new JLabel("Status: -");
        previewStatus.setFont(UIConstants.FONT_BODY_BOLD);
        previewStatus.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        previewStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewStatus);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void updatePreview() {
        int row = vehicleTable.getSelectedRow();
        if (row == -1) {
            previewName.setText("Select a vehicle");
            previewPlate.setText("Plate No.: -");
            previewType.setText("Type: -");
            previewPrice.setText("Price/Day: -");
            previewStatus.setText("Status: -");
            previewStatus.setForeground(UIConstants.DARK_TEXT_PRIMARY);
            previewImage.setPhoto(null);
            return;
        }
        String vehicleId = (String) tableModel.getValueAt(row, 0);
        vehicleService.getVehicleById(vehicleId).ifPresent(v -> {
            previewName.setText(v.getBrand() + " " + v.getModel());
            previewPlate.setText("Plate No.: " + v.getLicensePlate());
            previewType.setText("Type: " + v.getType());
            previewPrice.setText(String.format("Price/Day: \u20B1%,.2f", v.getPricePerDay()));
            boolean freeToday = bookingService.isVehicleAvailableToday(v.getVehicleId());
            String status = !v.isAvailable() ? "Out of Service" : freeToday ? "Available" : "Rented";
            previewStatus.setText("Status: " + status);
            previewStatus.setForeground(v.isAvailable() && freeToday ? UIConstants.SUCCESS : UIConstants.DANGER);
            previewImage.setKind(SilhouettePanel.Kind.of(v));
            previewImage.setPhoto(VehicleImages.load(v));
        });
    }

    private void loadVehicles() {
        tableModel.setRowCount(0);
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        List<Vehicle> vehicles = vehicleService.getAllVehicles();
        for (Vehicle v : vehicles) {
            String haystack = (v.getVehicleId() + v.getBrand() + v.getModel() + v.getLicensePlate()).toLowerCase();
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            tableModel.addRow(new Object[]{
                    v.getVehicleId(), v.getBrand(), v.getModel(), v.getType(), v.getYear(),
                    v.getLicensePlate(), String.format("\u20B1%,.2f", v.getPricePerDay()),
                    bookingService.isVehicleAvailableToday(v.getVehicleId()) ? "Available" : "Rented",
                    v.isAvailable() ? "Yes" : "No"
            });
        }
    }

    private void showVehicleDialog(Vehicle vehicle) {
        if (!WindowSession.requireAdmin(this, authService)) return;
        VehicleDialog dialog = new VehicleDialog(this, vehicleService, vehicle);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadVehicles();
            updatePreview();
        }
    }

    private void editSelected() {
        int row = vehicleTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a vehicle to edit", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String vehicleId = (String) tableModel.getValueAt(row, 0);
        vehicleService.getVehicleById(vehicleId).ifPresent(this::showVehicleDialog);
    }

    private void deleteSelected() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        int row = vehicleTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a vehicle to delete", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String vehicleId = (String) tableModel.getValueAt(row, 0);

        List<Booking> vehicleBookings = bookingService.getBookingsByVehicle(vehicleId);
        try {
            // check before asking, so the admin isn't asked to confirm a delete that will fail
            vehicleService.checkCanDelete(vehicleId, vehicleBookings);
        } catch (ValidationException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Delete", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Delete this vehicle?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                vehicleService.deleteVehicle(vehicleId, vehicleBookings);
                loadVehicles();
                updatePreview();
            } catch (VehicleNotFoundException | ValidationException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot Delete", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UIConstants.FONT_BODY_BOLD);
            boolean good = "Available".equals(value) || "Yes".equals(value);
            c.setForeground(good ? UIConstants.SUCCESS : UIConstants.DANGER);
            c.setBackground(isSelected ? UIConstants.DARK_ROW_SELECTED : (row % 2 == 0 ? UIConstants.DARK_CARD : UIConstants.DARK_BG));
            return c;
        }
    }
}
