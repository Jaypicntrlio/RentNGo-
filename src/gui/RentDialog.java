package gui;

import model.Vehicle;
import service.BookingService;
import model.exceptions.BookingException;
import model.exceptions.VehicleNotFoundException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class RentDialog extends JDialog {
    private Vehicle vehicle;
    private BookingService bookingService;
    private String customerId;
    private boolean booked = false;

    private JSpinner startDateSpinner;
    private JSpinner endDateSpinner;
    private JLabel totalCostLabel;

    public RentDialog(JFrame parent, Vehicle vehicle, BookingService bookingService, String customerId) {
        super(parent, "Rent Vehicle - " + vehicle.getBrand() + " " + vehicle.getModel(), true);
        this.vehicle = vehicle;
        this.bookingService = bookingService;
        this.customerId = customerId;
        initializeUI();
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, "Rent Vehicle");
        setSize(UIConstants.DIALOG_WIDTH, 500);
        setLocationRelativeTo(getParent());
        setResizable(false);
        setLayout(new BorderLayout());

        // Background
        JPanel backgroundPanel = new JPanel(new BorderLayout());
        backgroundPanel.setBackground(UIConstants.DARK_BG);

        // Main card
        JPanel cardPanel = new JPanel(new GridBagLayout());
        cardPanel.setBackground(UIConstants.DARK_CARD);
        cardPanel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Vehicle info header
        JPanel vehicleInfoPanel = new JPanel(new BorderLayout());
        vehicleInfoPanel.setBackground(UIConstants.DARK_FIELD_BG);
        vehicleInfoPanel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));

        JLabel vehicleName = UIConstants.createDarkSubheadingLabel(vehicle.getBrand() + " " + vehicle.getModel());
        vehicleInfoPanel.add(vehicleName, BorderLayout.WEST);

        JLabel vehiclePrice = new JLabel(String.format("\u20B1%,.2f/day", vehicle.getPricePerDay()));
        vehiclePrice.setFont(new Font("Segoe UI", Font.BOLD, 18));
        vehiclePrice.setForeground(UIConstants.SUCCESS);
        vehicleInfoPanel.add(vehiclePrice, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        cardPanel.add(vehicleInfoPanel, gbc);

        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        // Type badge
        JLabel typeBadge = new JLabel("  " + vehicle.getType() + "  ");
        typeBadge.setFont(UIConstants.FONT_BODY_BOLD);
        typeBadge.setForeground(UIConstants.DARK_ACCENT_BLUE);
        typeBadge.setBackground(UIConstants.DARK_ROW_SELECTED);
        typeBadge.setOpaque(true);
        typeBadge.setBorder(new EmptyBorder(4, 10, 4, 10));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        cardPanel.add(typeBadge, gbc);

        // Details
        String details = String.join(" · ", vehicle.getFeatures());
        JLabel detailsLabel = UIConstants.createDarkSecondaryLabel(details);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        cardPanel.add(detailsLabel, gbc);

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(UIConstants.DARK_BORDER);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(UIConstants.SPACING_MD, UIConstants.SPACING_XL, UIConstants.SPACING_MD, UIConstants.SPACING_XL);
        cardPanel.add(sep, gbc);
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        // Start Date
        JLabel startLabel = UIConstants.createDarkBodyLabel("Start Date:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        cardPanel.add(startLabel, gbc);

        startDateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor startEditor = new JSpinner.DateEditor(startDateSpinner, "yyyy-MM-dd");
        startEditor.getFormat().setLenient(false); // impossible dates are refused, not rolled over
        startDateSpinner.setEditor(startEditor);
        UIConstants.styleDarkSpinnerEditor(startDateSpinner);
        startDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        startDateSpinner.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        cardPanel.add(startDateSpinner, gbc);

        // End Date
        JLabel endLabel = UIConstants.createDarkBodyLabel("End Date:");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        cardPanel.add(endLabel, gbc);

        endDateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor endEditor = new JSpinner.DateEditor(endDateSpinner, "yyyy-MM-dd");
        endEditor.getFormat().setLenient(false); // impossible dates are refused, not rolled over
        endDateSpinner.setEditor(endEditor);
        UIConstants.styleDarkSpinnerEditor(endDateSpinner);
        endDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
        endDateSpinner.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        cardPanel.add(endDateSpinner, gbc);

        // Date change listeners
        ChangeListener dateListener = this::updateTotalCost;
        startDateSpinner.addChangeListener(dateListener);
        endDateSpinner.addChangeListener(dateListener);

        // Total Cost
        JLabel costTitleLabel = UIConstants.createDarkBodyLabel("Total Cost:");
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.anchor = GridBagConstraints.WEST;
        cardPanel.add(costTitleLabel, gbc);

        totalCostLabel = new JLabel("\u20B10.00");
        totalCostLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        totalCostLabel.setForeground(UIConstants.SUCCESS);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        cardPanel.add(totalCostLabel, gbc);

        updateTotalCost();

        // Note
        JLabel noteLabel = UIConstants.createDarkMutedLabel("Priced per day between the dates (minimum 1 day).");
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(UIConstants.SPACING_MD, UIConstants.SPACING_MD, 0, UIConstants.SPACING_MD);
        cardPanel.add(noteLabel, gbc);
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        backgroundPanel.add(cardPanel, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        buttonPanel.setBackground(UIConstants.DARK_BG);
        buttonPanel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, 0, 0, 0));

        JButton bookButton = UIConstants.createPrimaryButton("Confirm Booking");
        bookButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        bookButton.setPreferredSize(new Dimension(180, UIConstants.BUTTON_HEIGHT_LG));
        bookButton.addActionListener(this::handleBook);
        buttonPanel.add(bookButton);

        JButton cancelButton = UIConstants.createSecondaryButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        backgroundPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(backgroundPanel);
        getRootPane().setDefaultButton(bookButton);
    }

    private void updateTotalCost(ChangeEvent e) {
        updateTotalCost();
    }

    private void updateTotalCost() {
        java.util.Date startDate = (java.util.Date) startDateSpinner.getValue();
        java.util.Date endDate = (java.util.Date) endDateSpinner.getValue();

        LocalDate start = startDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        LocalDate end = endDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        if (!end.isAfter(start)) {
            totalCostLabel.setText("Invalid dates");
            totalCostLabel.setForeground(UIConstants.DANGER);
            return;
        }

        long days = model.Booking.calculateDays(start, end);
        if (days > service.BookingService.MAX_RENTAL_DAYS) {
            totalCostLabel.setText("Max " + service.BookingService.MAX_RENTAL_DAYS + " days");
            totalCostLabel.setForeground(UIConstants.DANGER);
            return;
        }
        java.math.BigDecimal total = vehicle.calculateRentalCost((int) days);
        totalCostLabel.setText(String.format("\u20B1%,.2f", total));
        totalCostLabel.setForeground(UIConstants.SUCCESS);
    }

    private void handleBook(ActionEvent e) {
        java.util.Date startDate = (java.util.Date) startDateSpinner.getValue();
        java.util.Date endDate = (java.util.Date) endDateSpinner.getValue();

        LocalDate start = startDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        LocalDate end = endDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();

        if (start.isBefore(LocalDate.now())) {
            showError("Start date cannot be in the past");
            return;
        }

        if (!end.isAfter(start)) {
            showError("End date must be after the start date (minimum rental is 1 day)");
            return;
        }

        try {
            bookingService.createBooking(customerId, vehicle.getVehicleId(), start, end);
            booked = true;
            JOptionPane.showMessageDialog(this, "Booking confirmed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (BookingException | VehicleNotFoundException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Booking Failed", JOptionPane.ERROR_MESSAGE);
    }

    public boolean isBooked() {
        return booked;
    }
}