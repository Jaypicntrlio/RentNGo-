package gui;

import model.Vehicle;
import service.BookingService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;

/**
 * The availability calendar of one vehicle in its own window (admin Rent Vehicle). "Use These
 * Dates" hands the picked start and return day back to the screen that opened it.
 */
public class AvailabilityCalendarDialog extends JDialog {

    private final AvailabilityCalendar calendar;
    private LocalDate[] chosen;

    private AvailabilityCalendarDialog(Window owner, BookingService bookingService, Vehicle vehicle,
                                       LocalDate start, LocalDate end) {
        super(owner, ModalityType.APPLICATION_MODAL);
        UIConstants.styleDarkDialog(this, "Availability - " + vehicle.getBrand() + " " + vehicle.getModel());

        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_MD));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        JPanel header = new JPanel(new BorderLayout(0, UIConstants.SPACING_XS));
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("Availability"), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel(vehicle.getVehicleId() + " · " + vehicle.getBrand() + " "
                + vehicle.getModel() + " (" + vehicle.getLicensePlate() + ")"
                + (vehicle.isAvailable() ? "" : " · out of service")), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        calendar = new AvailabilityCalendar(bookingService, vehicle.getVehicleId());
        calendar.setSelection(start, end);
        root.add(calendar, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        buttons.setOpaque(false);
        JButton close = UIConstants.createSecondaryButton("Close");
        close.addActionListener(e -> dispose());
        JButton use = UIConstants.createSuccessButton("Use These Dates");
        use.setEnabled(false);
        calendar.setOnSelect((s, en) -> use.setEnabled(true));
        use.addActionListener(e -> {
            chosen = new LocalDate[]{calendar.getSelectionStart(), calendar.getSelectionEnd()};
            dispose();
        });
        buttons.add(close);
        buttons.add(use);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setSize(Math.max(getWidth(), 400), getHeight());
        setLocationRelativeTo(owner);
    }

    /**
     * Opens the calendar showing the current dates and waits. Returns {start, return day} when the
     * user picked new free dates and pressed "Use These Dates", otherwise null.
     */
    public static LocalDate[] pickDates(Window owner, BookingService bookingService, Vehicle vehicle,
                                        LocalDate start, LocalDate end) {
        AvailabilityCalendarDialog d = new AvailabilityCalendarDialog(owner, bookingService, vehicle, start, end);
        d.setVisible(true);
        return d.chosen;
    }
}
