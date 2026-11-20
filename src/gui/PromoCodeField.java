package gui;

import model.PromoCode;
import model.exceptions.BookingException;
import service.BookingService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.BigDecimal;

/**
 * "Promo code [ WEEKEND10 ] [Apply]" with a line underneath saying whether the code works.
 * Used by both rent screens. Applying only previews the discount: the booking itself checks the
 * code again (it may have been used up in the meantime) and spends one use.
 */
public class PromoCodeField extends JPanel {
    private final BookingService bookingService;
    private final Runnable onChange;
    /** Who the booking is for (null = the logged-in customer), so a code they already used is refused on Apply. */
    private final java.util.function.Supplier<String> customerId;

    private final JTextField codeField;
    private final JLabel statusLabel;
    private PromoCode applied;

    /** @param onChange called whenever the applied code changes, so the screen can update its total */
    public PromoCodeField(BookingService bookingService, Runnable onChange) {
        this(bookingService, onChange, () -> null);
    }

    /** @param customerId who the booking is for; the admin's Rent Vehicle screen passes the selected customer */
    public PromoCodeField(BookingService bookingService, Runnable onChange, java.util.function.Supplier<String> customerId) {
        super(new BorderLayout(0, 2));
        this.bookingService = bookingService;
        this.onChange = onChange;
        this.customerId = customerId;
        setOpaque(false);

        JPanel row = new JPanel(new BorderLayout(UIConstants.SPACING_SM, 0));
        row.setOpaque(false);
        codeField = UIConstants.createDarkTextField(12);
        codeField.setPreferredSize(new Dimension(140, UIConstants.INPUT_HEIGHT));
        codeField.putClientProperty("JTextField.placeholderText", "Optional");
        codeField.addActionListener(e -> apply()); // Enter applies the code
        codeField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { textChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { textChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { textChanged(); }
        });
        JButton applyButton = UIConstants.createSecondaryButton("Apply");
        applyButton.addActionListener(e -> apply());
        row.add(codeField, BorderLayout.CENTER);
        row.add(applyButton, BorderLayout.EAST);
        add(row, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(UIConstants.FONT_SMALL);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void apply() {
        String text = codeField.getText().trim();
        if (text.isEmpty()) {
            setApplied(null, " ", UIConstants.DARK_TEXT_MUTED);
            return;
        }
        try {
            String forCustomer = customerId.get();
            PromoCode promo = forCustomer == null ? bookingService.checkPromoCode(text)
                    : bookingService.checkPromoCode(text, forCustomer);
            setApplied(promo, promo.getCode() + ": " + promo.getPercentOff() + "% off the rental (valid until "
                    + promo.getExpiryDate() + ")", UIConstants.SUCCESS);
        } catch (BookingException ex) {
            setApplied(null, ex.getMessage(), UIConstants.DANGER);
        }
    }

    /** Drops an applied code (e.g. when another customer is chosen), so it must be applied again for them. */
    public void reset() {
        if (applied != null) setApplied(null, "Press Apply to use this code", UIConstants.DARK_TEXT_MUTED);
    }

    /** Typing after applying drops the discount until Apply is pressed again. */
    private void textChanged() {
        if (applied != null && !applied.getCode().equals(PromoCode.normalize(codeField.getText()))) {
            setApplied(null, "Press Apply to use this code", UIConstants.DARK_TEXT_MUTED);
        }
    }

    private void setApplied(PromoCode promo, String message, Color color) {
        applied = promo;
        statusLabel.setText(message);
        statusLabel.setForeground(color);
        if (onChange != null) onChange.run();
    }

    /** The code typed (null when the box is empty), passed to createBooking. */
    public String getCode() {
        String text = codeField.getText().trim();
        return text.isEmpty() ? null : text;
    }

    /** The applied code's discount on this rental cost, or 0 when no code is applied. */
    public BigDecimal discountOn(BigDecimal rentalCost) {
        return applied == null ? model.Money.ZERO : applied.discountOn(rentalCost);
    }

    /** True when a code was typed but not applied (or didn't work). */
    public boolean hasUncheckedCode() {
        return getCode() != null && applied == null;
    }
}
