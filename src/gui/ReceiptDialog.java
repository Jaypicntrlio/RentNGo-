package gui;

import model.Booking;
import model.BookingStatus;
import model.Money;
import model.Payment;
import model.Vehicle;
import model.VehicleCondition;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Shows a payment receipt for a booking, with a "Save as TXT" button. */
public class ReceiptDialog extends JDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int WIDTH = 42;

    private final String receiptText;
    private final String fileName;

    /**
     * @param payments every payment for the booking, refunds included (PaymentService.getPaymentsByBookingId)
     * @param current  the payment just taken, or null when nothing was paid now (e.g. paid in advance)
     */
    public ReceiptDialog(Window owner, Booking booking, List<Payment> payments, Payment current,
                         String customerName, Vehicle vehicle) {
        super(owner, "Receipt", ModalityType.APPLICATION_MODAL);
        this.receiptText = buildReceipt(booking, payments, current, customerName, vehicle);
        this.fileName = "receipt-" + (current != null ? current.getPaymentId() : booking.getBookingId()) + ".txt";
        initializeUI();
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, "Receipt");
        setSize(480, 640);
        setLocationRelativeTo(getOwner());

        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        setContentPane(root);

        JLabel heading = UIConstants.createDarkHeadingLabel("Payment Receipt");
        root.add(heading, BorderLayout.NORTH);

        JTextArea area = new JTextArea(receiptText);
        area.setEditable(false);
        area.setFont(UIConstants.FONT_MONO);
        area.setBackground(UIConstants.DARK_FIELD_BG);
        area.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        area.setCaretPosition(0);
        area.setBorder(new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_LG));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(new CompoundBorder(new LineBorder(UIConstants.DARK_BORDER, 1), new EmptyBorder(0, 0, 0, 0)));
        scroll.getViewport().setBackground(UIConstants.DARK_FIELD_BG);
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, UIConstants.SPACING_MD, 0));
        buttons.setBackground(UIConstants.DARK_BG);
        JButton saveButton = UIConstants.createPrimaryButton("Save as TXT");
        saveButton.setPreferredSize(new Dimension(0, UIConstants.BUTTON_HEIGHT_LG));
        saveButton.addActionListener(e -> saveAsText());
        JButton closeButton = UIConstants.createButton("Close", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        closeButton.addActionListener(e -> dispose());
        buttons.add(saveButton);
        buttons.add(closeButton);
        root.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(closeButton);
    }

    private void saveAsText() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Receipt");
        chooser.setSelectedFile(new File(fileName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".txt")) {
            file = new File(file.getParentFile(), file.getName() + ".txt");
        }
        if (!FileSaving.okToWrite(this, file)) return;
        try {
            Files.writeString(file.toPath(), receiptText, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Receipt saved to " + file.getName(), "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the receipt: " + ex.getMessage(), "Save Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Plain-text receipt, also used for the saved .txt file. */
    public static String buildReceipt(Booking booking, List<Payment> payments, Payment current, String customerName, Vehicle vehicle) {
        Payment payment = current;
        StringBuilder sb = new StringBuilder();
        String rule = "-".repeat(WIDTH) + "\n";
        sb.append(center("RentNGo")).append(center("DRIVE IN DISTINCTION")).append(center("Official Receipt")).append(rule);

        if (payment != null) {
            line(sb, "Receipt No.", payment.getPaymentId());
            line(sb, "Date", payment.getPaymentDate().format(DATE_TIME));
        } else {
            line(sb, "Date", LocalDateTime.now().format(DATE_TIME));
        }
        line(sb, "Rental ID", booking.getBookingId());
        line(sb, "Customer", customerName);
        if (vehicle != null) {
            line(sb, "Vehicle", vehicle.getBrand() + " " + vehicle.getModel());
            line(sb, "Plate No.", vehicle.getLicensePlate());
        }
        line(sb, "Rental", booking.getStartDate().format(DATE) + " to " + booking.getEndDate().format(DATE));
        if (booking.getActualReturnDate() != null) {
            line(sb, "Returned", booking.getActualReturnDate().format(DATE));
        }
        sb.append(rule);
        if (booking.getPickUpCondition() != null || booking.getReturnCondition() != null) {
            appendConditions(sb, booking.getPickUpCondition(), booking.getReturnCondition());
            sb.append(rule);
        }

        long days = booking.getNumberOfDays();
        money(sb, "Rental (" + days + " day" + (days == 1 ? "" : "s") + ")", booking.getRentalCostBeforeDiscount());
        if (booking.getPromoCode() != null) {
            money(sb, "Promo " + booking.getPromoCode(), booking.getPromoDiscount().negate());
        }
        if (booking.getLoyaltyPointsUsed() > 0) {
            money(sb, "Loyalty points (" + booking.getLoyaltyPointsUsed() + ")", booking.getLoyaltyDiscount().negate());
        }
        if (Money.isPositive(booking.getLateFee())) {
            money(sb, "Late fee", booking.getLateFee());
        }
        if (Money.isPositive(booking.getAdditionalCharge())) {
            money(sb, "Additional charge", booking.getAdditionalCharge());
        }
        money(sb, "TOTAL", booking.getFinalAmount());
        sb.append(rule);

        // every payment and refund on the booking, oldest first; "*" marks the one this receipt is for
        sb.append("Payments\n");
        BigDecimal paid = Money.ZERO, refunded = Money.ZERO;
        for (Payment p : payments) {
            boolean isCurrent = current != null && p.getPaymentId().equals(current.getPaymentId());
            String label = (isCurrent ? "*" : " ") + p.getPaymentId() + " " + p.getPaymentDate().format(DATE) + " " + p.getMethod();
            money(sb, label, p.getAmount());
            if (Money.isPositive(p.getChange())) {
                money(sb, "   received " + peso(p.getAmountPaid()) + ", change", p.getChange());
            }
            if (p.isRefund()) refunded = refunded.add(p.getAmount().negate());
            else paid = paid.add(p.getAmount());
        }
        if (payments.isEmpty()) {
            sb.append(" No payments yet\n");
        }
        sb.append(rule);

        money(sb, "Total paid", paid);
        if (Money.isPositive(refunded)) {
            money(sb, "Refunded", refunded.negate());
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            money(sb, "Cancellation fee kept", booking.getNetPaid());
        } else {
            money(sb, "Balance", booking.getBalanceDue());
        }
        if (current == null && !payments.isEmpty() && booking.getStatus() != BookingStatus.CANCELLED
                && booking.getBalanceDue().signum() == 0) {
            sb.append(center("Paid in full in advance"));
        }
        if (current != null) {
            sb.append("* this payment\n");
        }
        if (booking.getLoyaltyPointsEarned() > 0) {
            sb.append(String.format("Loyalty points earned: %,d%n", booking.getLoyaltyPointsEarned()));
        } else if (booking.getLoyaltyPointsPending() > 0) {
            sb.append(String.format("Loyalty points earned: %,d (being added)%n", booking.getLoyaltyPointsPending()));
        }
        sb.append(rule).append(center("Thank you for renting with us!"));
        return sb.toString();
    }

    /**
     * The pick-up and return checklists side by side; "*" marks what is worse at return:
     * <pre>
     * Condition         Pick-up     Return
     * Scratches         No          Yes *
     * Fuel              Full        1/2 *
     * </pre>
     */
    static void appendConditions(StringBuilder sb, VehicleCondition pickUp, VehicleCondition atReturn) {
        String format = "%-16s%-12s%s%n";
        sb.append(String.format(format, "Condition", "Pick-up", "Return"));
        boolean anyNew = false;
        String[][] rows = {
                {"Scratches", pickUp == null ? null : yesNo(pickUp.hasScratches()), atReturn == null ? null : yesNo(atReturn.hasScratches())},
                {"Dents", pickUp == null ? null : yesNo(pickUp.hasDents()), atReturn == null ? null : yesNo(atReturn.hasDents())},
                {"Fuel", pickUp == null ? null : pickUp.getFuel().getLabel(), atReturn == null ? null : atReturn.getFuel().getLabel()},
                {"Cleanliness", pickUp == null ? null : cleanText(pickUp), atReturn == null ? null : cleanText(atReturn)},
        };
        boolean[] worse = new boolean[rows.length];
        if (pickUp != null && atReturn != null) {
            worse[0] = atReturn.hasScratches() && !pickUp.hasScratches();
            worse[1] = atReturn.hasDents() && !pickUp.hasDents();
            worse[2] = atReturn.getFuel().ordinal() < pickUp.getFuel().ordinal();
            worse[3] = !atReturn.isClean() && pickUp.isClean();
        }
        for (int i = 0; i < rows.length; i++) {
            anyNew |= worse[i];
            sb.append(String.format(format, rows[i][0], rows[i][1] == null ? "-" : rows[i][1],
                    (rows[i][2] == null ? "-" : rows[i][2]) + (worse[i] ? " *" : "")));
        }
        if (anyNew) sb.append("* new since pick-up\n");
        if (pickUp != null && pickUp.getNotes() != null) wrapped(sb, "Pick-up note: " + pickUp.getNotes());
        if (atReturn != null && atReturn.getNotes() != null) wrapped(sb, "Return note: " + atReturn.getNotes());
    }

    private static String yesNo(boolean b) {
        return b ? "Yes" : "No";
    }

    private static String cleanText(VehicleCondition c) {
        return c.isClean() ? "Clean" : "Not clean";
    }

    /** Writes text in lines of at most WIDTH characters, breaking between words. */
    private static void wrapped(StringBuilder sb, String text) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WIDTH) {
                sb.append(line).append('\n');
                line.setLength(0);
                line.append("  "); // continuation lines are indented
            }
            if (line.length() > 0 && !line.toString().isBlank()) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) sb.append(line).append('\n');
    }

    private static String peso(BigDecimal amount) {
        return (amount.signum() < 0 ? "-" : "") + String.format("₱%,.2f", amount.abs());
    }

    private static String center(String text) {
        int pad = Math.max(0, (WIDTH - text.length()) / 2);
        return " ".repeat(pad) + text + "\n";
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-14s%s%n", label + ":", value));
    }

    private static void money(StringBuilder sb, String label, BigDecimal amount) {
        String value = peso(amount);
        sb.append(String.format("%-" + (WIDTH - value.length()) + "s%s%n", label, value));
    }
}
