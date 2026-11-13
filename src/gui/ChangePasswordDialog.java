package gui;

import model.User;
import model.exceptions.ValidationException;
import service.AuthService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/**
 * Change-password dialog. In forced mode (account created with a temporary password) the user can
 * only choose a new password or log out; {@link #isChanged()} says whether it succeeded.
 */
public class ChangePasswordDialog extends JDialog {
    private final AuthService authService;
    private final User user;
    private final boolean forced;

    private JPasswordField currentField;
    private JPasswordField newField;
    private JPasswordField confirmField;
    private JLabel ruleLabel;
    private JLabel errorLabel;
    private boolean changed;

    public ChangePasswordDialog(Window owner, AuthService authService, User user, boolean forced) {
        super(owner, forced ? "Choose a New Password" : "Change Password", ModalityType.APPLICATION_MODAL);
        this.authService = authService;
        this.user = user;
        this.forced = forced;
        initializeUI();
    }

    /** True once the password was changed and saved. */
    public boolean isChanged() {
        return changed;
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, getTitle());
        // in forced mode, closing the window counts as "Log Out" (the caller checks isChanged())
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));
        setContentPane(root);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JLabel heading = UIConstants.createDarkHeadingLabel(getTitle());
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(heading);
        header.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));
        JLabel intro = new JLabel("<html><div style='width:330px'>" + (forced
                ? "Hi " + escape(user.getFullName()) + ", your account was set up with a temporary password. "
                  + "Choose your own password to continue."
                : "Signed in as " + escape(user.getUsername()) + ".") + "</div></html>");
        intro.setFont(UIConstants.FONT_BODY);
        intro.setForeground(UIConstants.DARK_TEXT_MUTED);
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(intro);
        root.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        currentField = UIConstants.createDarkPasswordField(20);
        newField = UIConstants.createDarkPasswordField(20);
        confirmField = UIConstants.createDarkPasswordField(20);
        addField(form, forced ? "Temporary password" : "Current password", currentField);
        addField(form, "New password", newField);
        addField(form, "Confirm new password", confirmField);

        ruleLabel = UIConstants.createDarkMutedLabel(" ");
        ruleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(ruleLabel);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));
        errorLabel = UIConstants.createDarkMutedLabel(" ");
        errorLabel.setForeground(UIConstants.DANGER);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(errorLabel);
        root.add(form, BorderLayout.CENTER);

        javax.swing.event.DocumentListener live = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateRule(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateRule(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateRule(); }
        };
        newField.getDocument().addDocumentListener(live);
        confirmField.getDocument().addDocumentListener(live);
        updateRule();

        JPanel buttons = new JPanel(new GridLayout(1, 2, UIConstants.SPACING_MD, 0));
        buttons.setOpaque(false);
        JButton cancel = UIConstants.createButton(forced ? "Log Out" : "Cancel", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        cancel.setPreferredSize(new Dimension(0, UIConstants.BUTTON_HEIGHT_LG));
        cancel.addActionListener(e -> dispose());
        JButton save = UIConstants.createButton("Save Password", UIConstants.DARK_ACCENT_BLUE,
                UIConstants.DARK_ACCENT_BLUE.darker(), Color.WHITE, UIConstants.FONT_BUTTON_LARGE);
        save.addActionListener(e -> save());
        buttons.add(cancel);
        buttons.add(save);
        root.add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);

        pack();
        setSize(Math.max(getWidth(), 440), getHeight());
        setLocationRelativeTo(getOwner());
    }

    private static void addField(JPanel form, String label, JPasswordField field) {
        JLabel l = UIConstants.createDarkSecondaryLabel(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_XS)));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UIConstants.INPUT_HEIGHT + 4));
        field.setPreferredSize(new Dimension(380, UIConstants.INPUT_HEIGHT + 4));
        form.add(field);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));
    }

    /** Live check of the new password against the rule, and whether the two copies match. */
    private void updateRule() {
        char[] pw = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        boolean longEnough = pw.length >= 8;
        boolean letter = false, digit = false;
        for (char c : pw) {
            letter |= Character.isLetter(c);
            digit |= Character.isDigit(c);
        }
        boolean ok = longEnough && letter && digit;
        if (pw.length == 0) {
            ruleLabel.setText("New password: " + AuthService.PASSWORD_RULE);
            ruleLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        } else if (!ok) {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (!longEnough) missing.add("8+ characters");
            if (!letter) missing.add("a letter");
            if (!digit) missing.add("a number");
            ruleLabel.setText("Still needs " + String.join(" and ", missing));
            ruleLabel.setForeground(UIConstants.WARNING);
        } else if (confirm.length > 0 && !Arrays.equals(pw, confirm)) {
            ruleLabel.setText("The two new passwords don't match");
            ruleLabel.setForeground(UIConstants.WARNING);
        } else {
            ruleLabel.setText("Strong enough");
            ruleLabel.setForeground(UIConstants.DARK_TREND_UP);
        }
        Arrays.fill(pw, '\0');
        Arrays.fill(confirm, '\0');
    }

    private void save() {
        String current = new String(currentField.getPassword());
        String next = new String(newField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (current.isEmpty()) {
            showError("Please enter your " + (forced ? "temporary" : "current") + " password");
            return;
        }
        if (!next.equals(confirm)) {
            showError("The two new passwords don't match");
            return;
        }
        try {
            authService.changePassword(user.getUserId(), current, next);
            changed = true;
            if (!forced) {
                JOptionPane.showMessageDialog(this, "Your password has been changed.", "Password Changed",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            dispose();
        } catch (ValidationException ex) {
            showError(ex.getMessage());
            currentField.setText("");
            currentField.requestFocusInWindow();
        }
    }

    private void showError(String message) {
        errorLabel.setText("<html><div style='width:360px'>" + escape(message) + "</div></html>");
        pack();
        setSize(Math.max(getWidth(), 440), getHeight());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
