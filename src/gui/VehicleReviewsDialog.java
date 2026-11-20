package gui;

import model.Review;
import model.Vehicle;
import service.ReviewService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

/** Read-only list of a vehicle's reviews (hidden ones left out), newest first. */
public class VehicleReviewsDialog extends JDialog {

    public VehicleReviewsDialog(Window parent, ReviewService reviewService, Vehicle vehicle) {
        super(parent, ModalityType.APPLICATION_MODAL);
        UIConstants.styleDarkDialog(this, "Reviews - " + vehicle.getBrand() + " " + vehicle.getModel());

        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_MD));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        List<Review> reviews = reviewService.getVisibleReviews(vehicle.getVehicleId());
        String summary = reviewService.getRatingSummary(vehicle.getVehicleId())
                .map(s -> String.format("%.1f out of 5 from %d review%s", s.getAverage(), s.getCount(), s.getCount() == 1 ? "" : "s"))
                .orElse("No reviews yet");
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel(vehicle.getBrand() + " " + vehicle.getModel()), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel(summary), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UIConstants.DARK_CARD);
        if (reviews.isEmpty()) {
            JLabel none = UIConstants.createDarkMutedLabel("Be the first to review this vehicle after your rental.");
            none.setBorder(new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
            list.add(none);
        }
        for (Review r : reviews) {
            list.add(reviewRow(r));
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(new LineBorder(UIConstants.DARK_BORDER, 1));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(460, 320));
        root.add(scroll, BorderLayout.CENTER);

        JButton close = UIConstants.createSecondaryButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        root.add(buttons, BorderLayout.SOUTH);

        add(root);
        pack();
        setLocationRelativeTo(parent);
    }

    private JPanel reviewRow(Review r) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel stars = new JLabel(Review.stars(r.getRating()));
        stars.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 15));
        stars.setForeground(UIConstants.CREST_GOLD);
        top.add(stars, BorderLayout.WEST);
        top.add(UIConstants.createDarkMutedLabel(r.getDate().toString()), BorderLayout.EAST);
        row.add(top, BorderLayout.NORTH);

        if (!r.getComment().isEmpty()) {
            JTextArea comment = new JTextArea(r.getComment());
            comment.setLineWrap(true);
            comment.setWrapStyleWord(true);
            comment.setEditable(false);
            comment.setOpaque(false);
            comment.setFont(UIConstants.FONT_BODY);
            comment.setForeground(UIConstants.DARK_TEXT_PRIMARY);
            comment.setColumns(36);
            row.add(comment, BorderLayout.CENTER);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }
}
