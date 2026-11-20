package gui;

import service.BookingService;

import javax.swing.*;
import java.math.BigDecimal;

/**
 * "Use 300 of my 450 loyalty points (-₱300)" checkbox, used by both rent screens. It always offers
 * the most points that can be spent on the current total (a multiple of 100, never more than the
 * rental costs); the booking checks the points again when it is made.
 */
public class LoyaltyPointsOption extends JCheckBox {
    private int balance;
    private int usable;

    /** @param onChange called when ticked or unticked, so the screen can update its total */
    public LoyaltyPointsOption(Runnable onChange) {
        setOpaque(false);
        setFont(UIConstants.FONT_BODY);
        setForeground(UIConstants.DARK_TEXT_PRIMARY);
        addActionListener(e -> {
            if (onChange != null) onChange.run();
        });
        update(0, BigDecimal.ZERO);
    }

    /** Recalculates the offer for this points balance and rental cost (after any promo discount). */
    public void update(int balance, BigDecimal cost) {
        this.balance = balance;
        this.usable = BookingService.maxPointsUsable(balance, cost);
        if (usable == 0) {
            setSelected(false);
            setEnabled(false);
            setText(balance + " loyalty points" + (balance < BookingService.POINTS_STEP
                    ? " (" + BookingService.POINTS_STEP + " needed to use them)" : ""));
        } else {
            setEnabled(true);
            setText(String.format("Use %,d of %,d loyalty points (-₱%,d)", usable, balance, usable));
        }
    }

    /** The points to pass to createBooking: the offer when ticked, otherwise 0. */
    public int getPointsToUse() {
        return isSelected() ? usable : 0;
    }

    public int getBalance() {
        return balance;
    }
}
