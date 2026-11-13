package gui;

import model.Booking;
import model.BookingStatus;
import model.Payment;
import model.Money;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.VehicleService;
import model.exceptions.BookingException;
import model.exceptions.ValidationException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReturnVehicleFrame extends JFrame {
    private final AuthService authService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    private JComboBox<ComboItem> rentalCombo;
    private JLabel customerLabel;
    private JLabel vehicleLabel;
    private JLabel startLabel;
    private JLabel expectedEndLabel;
    private JSpinner returnDateSpinner;
    private JTextField additionalChargeField;
    private JLabel totalPaymentLabel;
    private JLabel lateFeeNote;
    private JTextField reasonField;
    private JLabel reasonHint;
    private JLabel plateLabel;
    private JLabel typeLabel;
    private JLabel statusLabel;
    private JButton pickUpButton;
    private JButton confirmButton;
    private JComboBox<String> methodCombo;
    private JTextField amountReceivedField;
    private JLabel changeNote;
    private SilhouettePanel vehicleImage;

    public ReturnVehicleFrame(JFrame parent, AuthService authService, VehicleService vehicleService,
                              BookingService bookingService, PaymentService paymentService) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        initializeUI(parent);
        onRentalChanged();
    }

    private void initializeUI(JFrame parent) {
        UIConstants.styleDarkFrame(this, "RentNGo - Return Vehicle", 1000, 840);
        setMinimumSize(new Dimension(920, 800));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        root.add(createFormPanel(), BorderLayout.CENTER);
        root.add(createDetailsPanel(), BorderLayout.EAST);
        root.add(createButtonPanel(), BorderLayout.SOUTH);

        add(root);
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

        JLabel heading = UIConstants.createDarkHeadingLabel("Return Information");
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(heading, gbc);

        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        JLabel rentalIdLabel = UIConstants.createDarkBodyLabel("Rental ID:");
        gbc.gridx = 0; gbc.gridy = 1;
        card.add(rentalIdLabel, gbc);

        rentalCombo = new JComboBox<>();
        refreshRentals();
        UIConstants.styleDarkCombo(rentalCombo);
        rentalCombo.setPreferredSize(new Dimension(260, UIConstants.INPUT_HEIGHT));
        rentalCombo.addActionListener(e -> onRentalChanged());
        gbc.gridx = 1;
        card.add(rentalCombo, gbc);

        JLabel custLbl = UIConstants.createDarkBodyLabel("Customer:");
        gbc.gridx = 0; gbc.gridy = 2;
        card.add(custLbl, gbc);
        customerLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(customerLabel, gbc);

        JLabel vehLbl = UIConstants.createDarkBodyLabel("Vehicle:");
        gbc.gridx = 0; gbc.gridy = 3;
        card.add(vehLbl, gbc);
        vehicleLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(vehicleLabel, gbc);

        JLabel startLbl = UIConstants.createDarkBodyLabel("Start Date:");
        gbc.gridx = 0; gbc.gridy = 4;
        card.add(startLbl, gbc);
        startLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(startLabel, gbc);

        JLabel expectedLbl = UIConstants.createDarkBodyLabel("Expected End Date:");
        gbc.gridx = 0; gbc.gridy = 5;
        card.add(expectedLbl, gbc);
        expectedEndLabel = UIConstants.createDarkBodyLabel("-");
        gbc.gridx = 1;
        card.add(expectedEndLabel, gbc);

        JLabel returnLbl = UIConstants.createDarkBodyLabel("Return Date:");
        gbc.gridx = 0; gbc.gridy = 6;
        card.add(returnLbl, gbc);
        returnDateSpinner = dateSpinner(LocalDate.now());
        returnDateSpinner.addChangeListener(e -> updateTotalPayment());
        gbc.gridx = 1;
        card.add(returnDateSpinner, gbc);

        // A return dated other than today changes the late fee, so it needs a reason (kept in the audit log)
        JLabel reasonLbl = UIConstants.createDarkBodyLabel("Reason:");
        gbc.gridx = 0; gbc.gridy = 7;
        card.add(reasonLbl, gbc);
        reasonField = UIConstants.createDarkTextField(20);
        reasonField.setPreferredSize(new Dimension(260, UIConstants.INPUT_HEIGHT));
        reasonField.putClientProperty("JTextField.placeholderText", "Why the return date isn't today");
        reasonField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateReasonState(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateReasonState(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateReasonState(); }
        });
        gbc.gridx = 1;
        card.add(reasonField, gbc);

        reasonHint = UIConstants.createDarkMutedLabel(" ");
        gbc.gridy = 8;
        gbc.insets = new Insets(0, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(reasonHint, gbc);
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);

        JLabel chargeLbl = UIConstants.createDarkBodyLabel("Additional Charge:");
        gbc.gridx = 0; gbc.gridy = 9;
        card.add(chargeLbl, gbc);
        additionalChargeField = UIConstants.createDarkTextField(10);
        additionalChargeField.setText("0");
        additionalChargeField.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        additionalChargeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateTotalPayment(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateTotalPayment(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateTotalPayment(); }
        });
        gbc.gridx = 1;
        card.add(additionalChargeField, gbc);

        JLabel totalLbl = UIConstants.createDarkBodyLabel("Amount Due:");
        totalLbl.setFont(UIConstants.FONT_BODY_BOLD);
        gbc.gridx = 0; gbc.gridy = 10;
        gbc.insets = new Insets(UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(totalLbl, gbc);

        totalPaymentLabel = new JLabel("\u20B10.00");
        totalPaymentLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        totalPaymentLabel.setForeground(UIConstants.SUCCESS);
        gbc.gridx = 1;
        card.add(totalPaymentLabel, gbc);

        lateFeeNote = UIConstants.createDarkMutedLabel(" ");
        gbc.gridy = 11;
        gbc.insets = new Insets(0, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(lateFeeNote, gbc);

        // Payment taken when the vehicle comes back
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        JLabel methodLbl = UIConstants.createDarkBodyLabel("Payment Method:");
        gbc.gridx = 0; gbc.gridy = 12;
        card.add(methodLbl, gbc);
        methodCombo = UIConstants.createDarkComboBox(PaymentService.METHODS);
        methodCombo.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        methodCombo.addActionListener(e -> updateTotalPayment());
        gbc.gridx = 1;
        card.add(methodCombo, gbc);

        JLabel receivedLbl = UIConstants.createDarkBodyLabel("Amount Received:");
        gbc.gridx = 0; gbc.gridy = 13;
        card.add(receivedLbl, gbc);
        amountReceivedField = UIConstants.createDarkTextField(10);
        amountReceivedField.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        amountReceivedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateChange(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateChange(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateChange(); }
        });
        gbc.gridx = 1;
        card.add(amountReceivedField, gbc);

        changeNote = UIConstants.createDarkMutedLabel(" ");
        gbc.gridy = 14;
        gbc.insets = new Insets(0, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(changeNote, gbc);

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

        confirmButton = UIConstants.createSuccessButton("Confirm Return");
        confirmButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.CHECK, 16, Color.WHITE));
        confirmButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        confirmButton.setPreferredSize(new Dimension(200, UIConstants.BUTTON_HEIGHT_LG));
        confirmButton.addActionListener(e -> confirmReturn());
        panel.add(confirmButton);

        // CONFIRMED -> ACTIVE: the customer has collected the vehicle
        pickUpButton = UIConstants.createPrimaryButton("Mark Picked Up");
        pickUpButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        pickUpButton.setPreferredSize(new Dimension(180, UIConstants.BUTTON_HEIGHT_LG));
        pickUpButton.setEnabled(false);
        pickUpButton.addActionListener(e -> markPickedUp());
        panel.add(pickUpButton);

        JButton clearButton = UIConstants.createSecondaryButton("Clear");
        clearButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        clearButton.addActionListener(e -> {
            returnDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now()));
            additionalChargeField.setText("0");
            reasonField.setText("");
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

    private void refreshRentals() {
        rentalCombo.removeAllItems();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Booking b : bookingService.getReturnableBookings()) {
            String vehicleName = vehicleService.getVehicleById(b.getVehicleId())
                    .map(v -> v.getBrand() + " " + v.getModel())
                    .orElse("Unknown");
            String state = b.getStatus() == BookingStatus.ACTIVE ? "picked up" : "not picked up";
            rentalCombo.addItem(new ComboItem(b.getBookingId(),
                    b.getBookingId() + " - " + vehicleName + " (" + b.getEndDate().format(fmt) + ", " + state + ")"));
        }
    }

    private Booking selectedBooking() {
        ComboItem item = (ComboItem) rentalCombo.getSelectedItem();
        if (item == null) return null;
        return bookingService.getBookingById(item.id).orElse(null);
    }

    private void onRentalChanged() {
        Booking booking = selectedBooking();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (booking == null) {
            customerLabel.setText("-");
            vehicleLabel.setText("-");
            startLabel.setText("-");
            expectedEndLabel.setText("-");
            plateLabel.setText("Plate No.: -");
            typeLabel.setText("Type: -");
            statusLabel.setText("Status: -");
            if (pickUpButton != null) pickUpButton.setEnabled(false);
            if (confirmButton != null) confirmButton.setEnabled(false);
            totalPaymentLabel.setText("\u20B10.00");
            return;
        }

        String customerName = authService.getAllUsers().stream()
                .filter(u -> u.getUserId().equals(booking.getCustomerId()))
                .map(model.User::getFullName)
                .findFirst()
                .orElse("Unknown");
        customerLabel.setText(customerName);

        Vehicle vehicle = vehicleService.getVehicleById(booking.getVehicleId()).orElse(null);
        if (vehicle != null) {
            vehicleLabel.setText(vehicle.getBrand() + " " + vehicle.getModel());
            plateLabel.setText("Plate No.: " + vehicle.getLicensePlate());
            typeLabel.setText("Type: " + vehicle.getType());
            boolean pickedUp = booking.getStatus() == BookingStatus.ACTIVE;
            statusLabel.setText(pickedUp ? "Status: Picked up" : "Status: Booked, not picked up");
            statusLabel.setForeground(pickedUp ? UIConstants.DANGER : UIConstants.WARNING);
            vehicleImage.setKind(SilhouettePanel.Kind.of(vehicle));
            vehicleImage.setPhoto(VehicleImages.load(vehicle));
        } else {
            vehicleLabel.setText("Unknown");
        }

        if (pickUpButton != null) {
            pickUpButton.setEnabled(booking.getStatus() == BookingStatus.CONFIRMED);
        }
        if (confirmButton != null) {
            // only picked-up (ACTIVE) rentals can be returned; use Mark Picked Up first
            boolean active = booking.getStatus() == BookingStatus.ACTIVE;
            confirmButton.setEnabled(active);
            confirmButton.setToolTipText(active ? null : "Mark the vehicle as picked up first");
        }
        startLabel.setText(booking.getStartDate().format(fmt));
        expectedEndLabel.setText(booking.getEndDate().format(fmt));
        returnDateSpinner.setValue(java.sql.Date.valueOf(LocalDate.now()));
        additionalChargeField.setText("0");
        reasonField.setText("");
        updateTotalPayment();
    }

    /** The reason is only needed (and only editable) when the return date isn't today. */
    private void updateReasonState() {
        if (reasonField == null || reasonHint == null) return;
        boolean needed = !selectedReturnDate().equals(LocalDate.now());
        reasonField.setEnabled(needed);
        if (!needed) {
            reasonHint.setText("Only needed when the return date isn't today");
            reasonHint.setForeground(UIConstants.DARK_TEXT_MUTED);
            return;
        }
        int missing = BookingService.MIN_RETURN_NOTE_LENGTH - reasonField.getText().trim().length();
        reasonHint.setText(missing > 0
                ? "Required: the return date isn't today (" + missing + " more character" + (missing == 1 ? "" : "s") + ")"
                : "Reason will be saved with the return and in the audit log");
        reasonHint.setForeground(missing > 0 ? UIConstants.WARNING : UIConstants.DARK_TEXT_MUTED);
    }

    private void updateTotalPayment() {
        updateReasonState();
        Booking booking = selectedBooking();
        if (booking == null) {
            totalPaymentLabel.setText("\u20B10.00");
            return;
        }
        BigDecimal additional = parseCharge();
        LocalDate returnDate = selectedReturnDate();
        BigDecimal lateFee = bookingService.calculateLateFee(booking, returnDate);
        BigDecimal due = bookingService.previewAmountDue(booking, returnDate, additional);
        totalPaymentLabel.setText(String.format("\u20B1%,.2f", due));

        long lateDays = java.time.temporal.ChronoUnit.DAYS.between(booking.getEndDate(), returnDate);
        StringBuilder note = new StringBuilder(lateFee.signum() > 0
                ? String.format("Includes \u20B1%,.2f late fee (%d day%s late, 1.5x daily rate)", lateFee, lateDays, lateDays == 1 ? "" : "s")
                : "No late fee");
        if (Money.isPositive(booking.getAmountPaid())) {
            note.append(String.format(" \u00B7 \u20B1%,.2f paid earlier", booking.getAmountPaid()));
        }
        lateFeeNote.setText(note.toString());
        lateFeeNote.setForeground(lateFee.signum() > 0 ? UIConstants.WARNING : UIConstants.DARK_TEXT_MUTED);

        amountReceivedField.setText(due.toPlainString());
        boolean owes = Money.isPositive(due);
        methodCombo.setEnabled(owes);
        amountReceivedField.setEnabled(owes);
        // Card and GCash are charged the exact amount due; only Cash can be more (change)
        boolean cash = PaymentService.CASH.equals(methodCombo.getSelectedItem());
        amountReceivedField.setEditable(cash);
        amountReceivedField.setToolTipText(cash ? null : "Card and GCash are charged the exact amount due");
        updateChange();
    }

    private void updateChange() {
        Booking booking = selectedBooking();
        if (booking == null || changeNote == null) return;
        BigDecimal due = bookingService.previewAmountDue(booking, selectedReturnDate(), parseCharge());
        if (!Money.isPositive(due)) {
            changeNote.setText("Nothing to pay \u2014 already paid in full");
            changeNote.setForeground(UIConstants.DARK_TEXT_MUTED);
            return;
        }
        BigDecimal change;
        try {
            change = Money.parse(amountReceivedField.getText()).subtract(due);
        } catch (NumberFormatException ex) {
            changeNote.setText("Enter the amount received");
            changeNote.setForeground(UIConstants.DANGER);
            return;
        }
        changeNote.setText(change.signum() < 0
                ? String.format("\u20B1%,.2f short", change.negate())
                : String.format("Change: \u20B1%,.2f", change));
        changeNote.setForeground(change.signum() < 0 ? UIConstants.DANGER : UIConstants.DARK_TEXT_MUTED);
    }

    private LocalDate selectedReturnDate() {
        return ((java.util.Date) returnDateSpinner.getValue())
                .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    private BigDecimal parseCharge() {
        try {
            return Money.parse(additionalChargeField.getText());
        } catch (NumberFormatException ex) {
            return Money.ZERO;
        }
    }

    private void confirmReturn() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        Booking booking = selectedBooking();
        if (booking == null) {
            showError("Please select a rental to return");
            return;
        }
        BigDecimal additional = parseCharge();
        if (additional.signum() < 0) {
            showError("Additional charge cannot be negative");
            return;
        }
        LocalDate returnDate = selectedReturnDate();
        String reason = reasonField.getText().trim();
        if (!returnDate.equals(LocalDate.now()) && reason.length() < BookingService.MIN_RETURN_NOTE_LENGTH) {
            showError("The return date isn't today, so please enter a reason (at least "
                    + BookingService.MIN_RETURN_NOTE_LENGTH + " characters).");
            reasonField.requestFocusInWindow();
            return;
        }
        BigDecimal received;
        try {
            received = Money.parse(amountReceivedField.getText());
        } catch (NumberFormatException ex) {
            received = null; // PaymentService reports it if a payment is actually needed
        }

        try {
            Payment payment = paymentService.completeReturn(booking.getBookingId(), returnDate, additional,
                    returnDate.equals(LocalDate.now()) ? null : reason, (String) methodCombo.getSelectedItem(), received);
            Vehicle vehicle = vehicleService.getVehicleById(booking.getVehicleId()).orElse(null);
            dispose();
            // the booking shown here is a copy from before the payment: fetch it again for the receipt
            Booking updated = bookingService.getBookingById(booking.getBookingId()).orElse(booking);
            new ReceiptDialog(getOwner(), updated, paymentService.getPaymentsByBookingId(booking.getBookingId()), payment,
                    customerLabel.getText(), vehicle).setVisible(true);
        } catch (BookingException | ValidationException ex) {
            showError(ex.getMessage());
        }
    }

    private void markPickedUp() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        Booking booking = selectedBooking();
        if (booking == null) {
            showError("Please select a rental");
            return;
        }
        try {
            bookingService.markPickedUp(booking.getBookingId());
            String id = booking.getBookingId();
            refreshRentals();
            for (int i = 0; i < rentalCombo.getItemCount(); i++) {
                if (rentalCombo.getItemAt(i).id.equals(id)) {
                    rentalCombo.setSelectedIndex(i);
                    break;
                }
            }
            onRentalChanged();
            JOptionPane.showMessageDialog(this, "Rental " + id + " marked as picked up.", "Picked Up", JOptionPane.INFORMATION_MESSAGE);
        } catch (BookingException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Can't Mark Picked Up", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Return Failed", JOptionPane.ERROR_MESSAGE);
    }

    private static class ComboItem {
        final String id;
        final String label;
        ComboItem(String id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}
