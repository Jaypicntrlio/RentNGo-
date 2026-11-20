package gui;

import model.Review;
import model.exceptions.BookingException;
import service.ReviewService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Customer: rate a completed rental with 1 to 5 stars and an optional comment (up to 300 characters).
 * Opened from My Bookings → Rate Vehicle.
 */
public class ReviewDialog extends JDialog {
    private final ReviewService reviewService;
    private final String bookingId;

    private final JToggleButton[] starButtons = new JToggleButton[Review.MAX_RATING];
    private int rating;
    private JLabel ratingLabel;
    private JTextArea commentArea;
    private JLabel counterLabel;
    private boolean saved;

    private static final String[] RATING_WORDS = {"", "Poor", "Fair", "Good", "Very good", "Excellent"};

    public ReviewDialog(JFrame parent, ReviewService reviewService, String bookingId, String vehicleName) {
        super(parent, true);
        this.reviewService = reviewService;
        this.bookingId = bookingId;
        UIConstants.styleDarkDialog(this, "Rate Your Rental");
        buildUI(vehicleName);
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI(String vehicleName) {
        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_MD));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_LG, UIConstants.SPACING_XL));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("How was the " + vehicleName + "?"), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel("Rental " + bookingId + " · your review helps other customers choose"), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel starsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        starsRow.setOpaque(false);
        starsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < starButtons.length; i++) {
            int value = i + 1;
            JToggleButton star = new JToggleButton("☆");
            star.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 30));
            star.setForeground(UIConstants.DARK_TEXT_MUTED);
            star.setContentAreaFilled(false);
            star.setBorderPainted(false);
            star.setFocusPainted(false);
            star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            star.setToolTipText(value + (value == 1 ? " star" : " stars") + " - " + RATING_WORDS[value]);
            star.addActionListener(e -> setRating(value));
            starButtons[i] = star;
            starsRow.add(star);
        }
        ratingLabel = UIConstants.createDarkBodyLabel("Choose 1 to 5 stars");
        ratingLabel.setFont(UIConstants.FONT_BODY_BOLD);
        starsRow.add(Box.createRigidArea(new Dimension(UIConstants.SPACING_MD, 0)));
        starsRow.add(ratingLabel);
        body.add(starsRow);
        body.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        JLabel commentLabel = UIConstants.createDarkBodyLabel("Comment (optional)");
        commentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(commentLabel);
        body.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        commentArea = new JTextArea(5, 36);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        commentArea.setFont(UIConstants.FONT_BODY);
        commentArea.setBackground(UIConstants.DARK_FIELD_BG);
        commentArea.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        commentArea.setCaretColor(UIConstants.DARK_TEXT_PRIMARY);
        commentArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        commentArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateCounter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateCounter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateCounter(); }
        });
        JScrollPane scroll = new JScrollPane(commentArea);
        scroll.setBorder(new LineBorder(UIConstants.DARK_BORDER, 1));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(scroll);

        counterLabel = UIConstants.createDarkMutedLabel("0 / " + Review.MAX_COMMENT_LENGTH);
        counterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(counterLabel);
        root.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        buttons.setOpaque(false);
        JButton cancel = UIConstants.createSecondaryButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton submit = UIConstants.createSuccessButton("Submit Review");
        submit.addActionListener(e -> submit());
        buttons.add(cancel);
        buttons.add(submit);
        root.add(buttons, BorderLayout.SOUTH);

        add(root);
    }

    private void setRating(int value) {
        rating = value;
        for (int i = 0; i < starButtons.length; i++) {
            boolean on = i < value;
            starButtons[i].setText(on ? "★" : "☆");
            starButtons[i].setForeground(on ? UIConstants.CREST_GOLD : UIConstants.DARK_TEXT_MUTED);
            starButtons[i].setSelected(on);
        }
        ratingLabel.setText(value + "/5 · " + RATING_WORDS[value]);
    }

    private void updateCounter() {
        int length = commentArea.getText().trim().length();
        counterLabel.setText(length + " / " + Review.MAX_COMMENT_LENGTH);
        counterLabel.setForeground(length > Review.MAX_COMMENT_LENGTH ? UIConstants.DANGER : UIConstants.DARK_TEXT_MUTED);
    }

    private void submit() {
        if (rating == 0) {
            JOptionPane.showMessageDialog(this, "Please choose 1 to 5 stars.", "Review", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            reviewService.addReview(bookingId, rating, commentArea.getText());
            saved = true;
            JOptionPane.showMessageDialog(this, "Thank you for your review!", "Review Saved", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (BookingException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Review", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
