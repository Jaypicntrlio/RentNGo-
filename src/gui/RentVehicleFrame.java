package gui;

import model.Customer;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.VehicleService;
import model.exceptions.BookingException;
import model.exceptions.VehicleNotFoundException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

public class RentVehicleFrame extends JFrame {
    private final AuthService authService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;

    private JComboBox<ComboItem> customerCombo;
    private JComboBox<ComboItem> vehicleCombo;
    private JSpinner startDateSpinner;
    private JSpinner endDateSpinner;
    private JLabel totalDaysLabel;
    private JLabel totalAmountLabel;
    private JLabel pricePerDayLabel;
    private JLabel plateLabel;
    private JLabel typeLabel;
    private JLabel statusLabel;
    private SilhouettePanel vehicleImage;

    public RentVehicleFrame(JFrame parent, AuthService authService, VehicleService vehicleService, BookingService bookingService) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        initializeUI(parent);
        updateTotal();
    }

    private void initializeUI(JFrame parent) {
        UIConstants.styleDarkFrame(this, "RentNGo - Rent Vehicle", 980, 620);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        root.add(createFormPanel(), BorderLayout.CENTER);
        root.add(createDetailsPanel(), BorderLayout.EAST);
        root.add(createButtonPanel(), BorderLayout.SOUTH);

        add(root);
        updateVehicleDetails();
    }

    private JPanel createFormPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 2;

        JLabel heading = UIConstants.createDarkHeadingLabel("Rental Information");
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(heading, gbc);

        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        JLabel customerLabel = UIConstants.createDarkBodyLabel("Customer:");
        gbc.gridx = 0; gbc.gridy = 1;
        card.add(customerLabel, gbc);

        customerCombo = new JComboBox<>();
        for (Customer c : authService.getAllCustomers()) {
            if (!c.isActive()) continue; // deactivated customers can't book
            customerCombo.addItem(new ComboItem(c.getUserId(), c.getUserId() + " - " + c.getFullName()));
        }
        styleCombo(customerCombo);
        gbc.gridx = 1;
        card.add(customerCombo, gbc);

        JLabel vehicleLabel = UIConstants.createDarkBodyLabel("Vehicle:");
        gbc.gridx = 0; gbc.gridy = 2;
        card.add(vehicleLabel, gbc);

        vehicleCombo = new JComboBox<>();
        for (Vehicle v : vehicleService.getInServiceVehicles()) {
            vehicleCombo.addItem(new ComboItem(v.getVehicleId(),
                    v.getVehicleId() + " - " + v.getBrand() + " " + v.getModel() + " (" + v.getLicensePlate() + ")"));
        }
        styleCombo(vehicleCombo);
        vehicleCombo.addActionListener(e -> { updateVehicleDetails(); updateTotal(); });
        gbc.gridx = 1;
        card.add(vehicleCombo, gbc);

        JLabel startLabel = UIConstants.createDarkBodyLabel("Start Date:");
        gbc.gridx = 0; gbc.gridy = 3;
        card.add(startLabel, gbc);

        startDateSpinner = dateSpinner(LocalDate.now());
        startDateSpinner.addChangeListener(e -> updateTotal());
        gbc.gridx = 1;
        card.add(startDateSpinner, gbc);

        JLabel endLabel = UIConstants.createDarkBodyLabel("End Date:");
        gbc.gridx = 0; gbc.gridy = 4;
        card.add(endLabel, gbc);

        endDateSpinner = dateSpinner(LocalDate.now().plusDays(2));
        endDateSpinner.addChangeListener(e -> updateTotal());
        gbc.gridx = 1;
        card.add(endDateSpinner, gbc);

        JLabel priceLabel = UIConstants.createDarkBodyLabel("Rental Price/Day:");
        gbc.gridx = 0; gbc.gridy = 5;
        card.add(priceLabel, gbc);

        pricePerDayLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(pricePerDayLabel, gbc);

        JLabel daysLabel = UIConstants.createDarkBodyLabel("Total Days:");
        gbc.gridx = 0; gbc.gridy = 6;
        card.add(daysLabel, gbc);

        totalDaysLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(totalDaysLabel, gbc);

        JLabel totalLabel = UIConstants.createDarkBodyLabel("Total Amount:");
        totalLabel.setFont(UIConstants.FONT_BODY_BOLD);
        gbc.gridx = 0; gbc.gridy = 7;
        gbc.insets = new Insets(UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(totalLabel, gbc);

        totalAmountLabel = new JLabel("\u20B10.00");
        totalAmountLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        totalAmountLabel.setForeground(UIConstants.SUCCESS);
        gbc.gridx = 1;
        card.add(totalAmountLabel, gbc);

        return card;
    }

    private JPanel createDetailsPanel() {
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

        vehicleImage = new SilhouettePanel(SilhouettePanel.Kind.CAR);
        vehicleImage.setDarkMode(true);
        vehicleImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        vehicleImage.setMaximumSize(new Dimension(260, 170));
        panel.add(vehicleImage);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        plateLabel = UIConstants.createDarkSecondaryLabel("Plate No.: -");
        plateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(plateLabel);

        typeLabel = UIConstants.createDarkSecondaryLabel("Type: -");
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(typeLabel);

        statusLabel = new JLabel("Status: -");
        statusLabel.setFont(UIConstants.FONT_BODY_BOLD);
        statusLabel.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(new EmptyBorder(UIConstants.SPACING_SM, 0, 0, 0));
        panel.add(statusLabel);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_BG);

        JButton confirmButton = UIConstants.createSuccessButton("Confirm Rental");
        confirmButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.CHECK, 16, Color.WHITE));
        confirmButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        confirmButton.setPreferredSize(new Dimension(200, UIConstants.BUTTON_HEIGHT_LG));
        confirmButton.addActionListener(e -> confirmRental());
        panel.add(confirmButton);

        JButton clearButton = UIConstants.createSecondaryButton("Clear");
        clearButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        clearButton.addActionListener(e -> {
            startDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now()));
            endDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
            updateTotal();
        });
        panel.add(clearButton);

        return panel;
    }

    private JSpinner dateSpinner(LocalDate initial) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
        spinner.setEditor(editor);
        UIConstants.styleDarkSpinnerEditor(spinner);
        spinner.setValue(java.sql.Date.valueOf(initial));
        spinner.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        return spinner;
    }

    private void styleCombo(JComboBox<ComboItem> combo) {
        UIConstants.styleDarkCombo(combo);
        combo.setPreferredSize(new Dimension(260, UIConstants.INPUT_HEIGHT));
    }

    private Vehicle selectedVehicle() {
        ComboItem item = (ComboItem) vehicleCombo.getSelectedItem();
        if (item == null) return null;
        return vehicleService.getVehicleById(item.id).orElse(null);
    }

    private void updateVehicleDetails() {
        Vehicle v = selectedVehicle();
        if (v == null) {
            pricePerDayLabel.setText("-");
            plateLabel.setText("Plate No.: -");
            typeLabel.setText("Type: -");
            statusLabel.setText("Status: -");
            return;
        }
        pricePerDayLabel.setText(String.format("\u20B1%,.2f", v.getPricePerDay()));
        plateLabel.setText("Plate No.: " + v.getLicensePlate());
        typeLabel.setText("Type: " + v.getType());
        boolean freeToday = bookingService.isVehicleAvailableToday(v.getVehicleId());
        statusLabel.setText("Status: " + (freeToday ? "Available today" : "Rented today"));
        statusLabel.setForeground(freeToday ? UIConstants.SUCCESS : UIConstants.DANGER);
        vehicleImage.setKind(SilhouettePanel.Kind.of(v));
        vehicleImage.setPhoto(VehicleImages.load(v));
    }

    private void updateTotal() {
        Vehicle v = selectedVehicle();
        LocalDate start = asLocalDate(startDateSpinner);
        LocalDate end = asLocalDate(endDateSpinner);

        if (v == null || start == null || end == null || !start.isBefore(end)) {
            totalDaysLabel.setText("-");
            totalAmountLabel.setText("\u20B10.00");
            return;
        }
        long days = model.Booking.calculateDays(start, end);
        java.math.BigDecimal total = v.calculateRentalCost((int) days);
        totalDaysLabel.setText(String.valueOf(days));
        totalAmountLabel.setText(String.format("\u20B1%,.2f", total));
    }

    private LocalDate asLocalDate(JSpinner spinner) {
        java.util.Date date = (java.util.Date) spinner.getValue();
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private void confirmRental() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        ComboItem customerItem = (ComboItem) customerCombo.getSelectedItem();
        Vehicle vehicle = selectedVehicle();

        if (customerItem == null) {
            showError("Please add a customer first (Manage Customers)");
            return;
        }
        if (vehicle == null) {
            showError("Please select a vehicle to rent");
            return;
        }

        LocalDate start = asLocalDate(startDateSpinner);
        LocalDate end = asLocalDate(endDateSpinner);

        try {
            bookingService.createBooking(customerItem.id, vehicle.getVehicleId(), start, end);
            JOptionPane.showMessageDialog(this, "Rental confirmed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (BookingException | VehicleNotFoundException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Rental Failed", JOptionPane.ERROR_MESSAGE);
    }

    private static class ComboItem {
        final String id;
        final String label;
        ComboItem(String id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}
