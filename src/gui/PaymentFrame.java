package gui;

import model.Booking;
import model.Money;
import model.Payment;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.VehicleService;
import model.exceptions.ValidationException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class PaymentFrame extends JFrame {
    private final AuthService authService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    private JComboBox<ComboItem> rentalCombo;
    private JLabel customerLabel;
    private JLabel totalAmountLabel;
    private JComboBox<String> methodCombo;
    private JTextField amountPaidField;
    private JLabel changeLabel;
    private JTextArea receiptArea;

    public PaymentFrame(JFrame parent, AuthService authService, VehicleService vehicleService,
                         BookingService bookingService, PaymentService paymentService) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        initializeUI(parent);
        onRentalChanged();
    }

    private void initializeUI(JFrame parent) {
        UIConstants.styleDarkFrame(this, "RentNGo - Payment", 980, 620);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        root.add(createFormPanel(), BorderLayout.CENTER);
        root.add(createReceiptPanel(), BorderLayout.EAST);
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

        JLabel heading = UIConstants.createDarkHeadingLabel("Payment Information");
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

        JLabel totalLbl = UIConstants.createDarkBodyLabel("Amount Due:");
        gbc.gridx = 0; gbc.gridy = 3;
        card.add(totalLbl, gbc);
        totalAmountLabel = new JLabel("\u20B10.00");
        totalAmountLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        totalAmountLabel.setForeground(UIConstants.SUCCESS);
        gbc.gridx = 1;
        card.add(totalAmountLabel, gbc);

        JLabel methodLbl = UIConstants.createDarkBodyLabel("Payment Method:");
        gbc.gridx = 0; gbc.gridy = 4;
        card.add(methodLbl, gbc);
        methodCombo = UIConstants.createDarkComboBox(PaymentService.METHODS);
        methodCombo.setPreferredSize(new Dimension(200, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 1;
        card.add(methodCombo, gbc);

        JLabel paidLbl = UIConstants.createDarkBodyLabel("Amount Paid:");
        gbc.gridx = 0; gbc.gridy = 5;
        card.add(paidLbl, gbc);
        amountPaidField = UIConstants.createDarkTextField(12);
        amountPaidField.setPreferredSize(new Dimension(180, UIConstants.INPUT_HEIGHT));
        amountPaidField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateChangeAndReceipt(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateChangeAndReceipt(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateChangeAndReceipt(); }
        });
        methodCombo.addActionListener(e -> { lockAmountToMethod(); updateChangeAndReceipt(); });
        gbc.gridx = 1;
        card.add(amountPaidField, gbc);

        JLabel changeLbl = UIConstants.createDarkBodyLabel("Change:");
        changeLbl.setFont(UIConstants.FONT_BODY_BOLD);
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.insets = new Insets(UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        card.add(changeLbl, gbc);

        changeLabel = new JLabel("\u20B10.00");
        changeLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        changeLabel.setForeground(UIConstants.PRIMARY);
        gbc.gridx = 1;
        card.add(changeLabel, gbc);

        return card;
    }

    private JPanel createReceiptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));
        panel.setPreferredSize(new Dimension(300, 0));

        JLabel heading = UIConstants.createDarkSubheadingLabel("Receipt Preview");
        panel.add(heading, BorderLayout.NORTH);

        receiptArea = new JTextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(UIConstants.FONT_MONO);
        receiptArea.setBackground(UIConstants.DARK_FIELD_BG);
        receiptArea.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        receiptArea.setBorder(new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        receiptArea.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(receiptArea);
        scroll.setBorder(new LineBorder(UIConstants.DARK_BORDER, 1));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_BG);

        JButton processButton = UIConstants.createSuccessButton("Process Payment");
        processButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.CARD, 16, Color.WHITE));
        processButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        processButton.setPreferredSize(new Dimension(200, UIConstants.BUTTON_HEIGHT_LG));
        processButton.addActionListener(e -> processPayment());
        panel.add(processButton);

        JButton clearButton = UIConstants.createSecondaryButton("Clear");
        clearButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        clearButton.addActionListener(e -> {
            amountPaidField.setText("");
            updateChangeAndReceipt();
        });
        panel.add(clearButton);

        return panel;
    }

    private void refreshRentals() {
        rentalCombo.removeAllItems();
        for (Booking b : bookingService.getUnpaidBookings()) {
            rentalCombo.addItem(new ComboItem(b.getBookingId(), b.getBookingId()));
        }
    }

    private Booking selectedBooking() {
        ComboItem item = (ComboItem) rentalCombo.getSelectedItem();
        if (item == null) return null;
        return bookingService.getBookingById(item.id).orElse(null);
    }

    private void onRentalChanged() {
        Booking booking = selectedBooking();
        if (booking == null) {
            customerLabel.setText("-");
            totalAmountLabel.setText("\u20B10.00");
            amountPaidField.setText("");
            updateChangeAndReceipt();
            return;
        }
        String customerName = authService.getAllUsers().stream()
                .filter(u -> u.getUserId().equals(booking.getCustomerId()))
                .map(model.User::getFullName)
                .findFirst()
                .orElse("Unknown");
        customerLabel.setText(customerName);
        totalAmountLabel.setText(String.format("\u20B1%,.2f", booking.getBalanceDue()));
        amountPaidField.setText(booking.getBalanceDue().toPlainString());
        lockAmountToMethod();
        updateChangeAndReceipt();
    }

    /** Card and GCash are charged the exact amount due; only Cash can be more (change). */
    private void lockAmountToMethod() {
        boolean cash = PaymentService.CASH.equals(methodCombo.getSelectedItem());
        Booking booking = selectedBooking();
        if (!cash && booking != null) {
            amountPaidField.setText(booking.getBalanceDue().toPlainString());
        }
        amountPaidField.setEditable(cash);
        amountPaidField.setToolTipText(cash ? null : "Card and GCash are charged the exact amount due");
    }

    private void updateChangeAndReceipt() {
        Booking booking = selectedBooking();
        BigDecimal total = booking != null ? booking.getBalanceDue() : Money.ZERO;
        BigDecimal paid = parsePaid();
        BigDecimal change = paid.subtract(total);
        changeLabel.setText(String.format("\u20B1%,.2f", change.max(Money.ZERO)));
        changeLabel.setForeground(change.signum() < 0 ? UIConstants.DANGER : UIConstants.PRIMARY);
        buildReceiptPreview(booking, total, paid, change);
    }

    private BigDecimal parsePaid() {
        try {
            return Money.parse(amountPaidField.getText());
        } catch (NumberFormatException ex) {
            return Money.ZERO;
        }
    }

    private void buildReceiptPreview(Booking booking, BigDecimal total, BigDecimal paid, BigDecimal change) {
        StringBuilder sb = new StringBuilder();
        sb.append("      RentNGo\n");
        sb.append("CAR RENTAL MANAGEMENT SYSTEM\n");
        sb.append("--------------------------------\n");
        if (booking != null) {
            sb.append("Rental ID: ").append(booking.getBookingId()).append("\n");
            sb.append("Customer:  ").append(customerLabel.getText()).append("\n");
            sb.append(String.format("Amount:    \u20B1%,.2f%n", total));
            sb.append("Method:    ").append(methodCombo.getSelectedItem()).append("\n");
            sb.append(String.format("Paid:      \u20B1%,.2f%n", paid));
            sb.append(String.format("Change:    \u20B1%,.2f%n", change.max(Money.ZERO)));
            sb.append("--------------------------------\n");
            sb.append("Status: ").append(paid.compareTo(total) >= 0 && total.signum() > 0 ? "Paid" : "Pending").append("\n");
        } else {
            sb.append("No rental selected.\n");
        }
        receiptArea.setText(sb.toString());
    }

    private void processPayment() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        Booking booking = selectedBooking();
        if (booking == null) {
            showError("Please select a rental to pay for");
            return;
        }
        BigDecimal total = booking.getBalanceDue();
        BigDecimal paid = parsePaid();
        String method = (String) methodCombo.getSelectedItem();

        try {
            Payment payment = paymentService.processPayment(booking.getBookingId(), booking.getCustomerId(), total, method, paid);
            model.Vehicle vehicle = vehicleService.getVehicleById(booking.getVehicleId()).orElse(null);
            dispose();
            // the booking shown here is a copy from before the payment: fetch it again for the receipt
            Booking updated = bookingService.getBookingById(booking.getBookingId()).orElse(booking);
            new ReceiptDialog(getOwner(), updated, paymentService.getPaymentsByBookingId(booking.getBookingId()), payment,
                    customerLabel.getText(), vehicle).setVisible(true);
        } catch (ValidationException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Payment Failed", JOptionPane.ERROR_MESSAGE);
    }

    private static class ComboItem {
        final String id;
        final String label;
        ComboItem(String id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}
