package gui;

import model.User;
import model.exceptions.ValidationException;
import service.AuthService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Sign-up form opened from the login screen. Creates a customer or admin account and saves it. */
public class RegisterDialog extends JDialog {
    private AuthService authService;
    private JToggleButton customerToggle;
    private JToggleButton adminToggle;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField licenseField;
    private JLabel licenseLabel;
    private JPanel roleRow;
    private JTextField approverUserField;
    private JPasswordField approverPassField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private User createdUser;
    private ProfilePhotos.Avatar avatar;
    private JLabel avatarLabel;
    private JButton removePhotoButton;
    private BufferedImage chosenPhoto;

    public RegisterDialog(JFrame parent, AuthService authService) {
        super(parent, "Create Account", true);
        this.authService = authService;
        initializeUI();
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, "Create Account");
        setSize(520, 770);
        setLocationRelativeTo(getParent());
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        setContentPane(root);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        int row = 0;

        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 2, 0);
        card.add(UIConstants.createDarkHeadingLabel("Create your account"), gbc);
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(UIConstants.createDarkSecondaryLabel("Your account is saved, so you can sign in any time."), gbc);

        // Account type switch
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(createRoleSwitch(), gbc);

        // Profile photo
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(createPhotoPicker(), gbc);

        fullNameField = UIConstants.createDarkTextField(20);
        row = addField(card, row, 0, 2, "Full name", fullNameField);

        emailField = UIConstants.createDarkTextField(20);
        phoneField = UIConstants.createDarkTextField(20);
        addField(card, row, 0, 1, "Email", emailField);
        row = addField(card, row, 1, 1, "Phone", phoneField);

        // Customers enter a driver's license; admins need an existing admin to approve them.
        licenseField = UIConstants.createDarkTextField(20);
        licenseLabel = UIConstants.createDarkSecondaryLabel("Driver's license no.");
        approverUserField = UIConstants.createDarkTextField(20);
        approverPassField = UIConstants.createDarkPasswordField(20);
        roleRow = new JPanel(new CardLayout());
        roleRow.setOpaque(false);
        roleRow.add(labeledColumn(licenseLabel, licenseField), "customer");
        JPanel approval = new JPanel(new GridLayout(1, 2, UIConstants.SPACING_LG, 0));
        approval.setOpaque(false);
        approval.add(labeledColumn(UIConstants.createDarkSecondaryLabel("Approving admin username"), approverUserField));
        approval.add(labeledColumn(UIConstants.createDarkSecondaryLabel("Approving admin password"), approverPassField));
        roleRow.add(approval, "admin");
        GridBagConstraints roleGbc = new GridBagConstraints();
        roleGbc.fill = GridBagConstraints.HORIZONTAL;
        roleGbc.weightx = 1.0;
        roleGbc.gridx = 0;
        roleGbc.gridwidth = 2;
        roleGbc.gridy = row++;
        roleGbc.insets = new Insets(0, 0, UIConstants.SPACING_MD, 0);
        card.add(roleRow, roleGbc);
        refreshRoleSwitch();

        usernameField = UIConstants.createDarkTextField(20);
        row = addField(card, row, 0, 2, "Username", usernameField);

        passwordField = UIConstants.createDarkPasswordField(20);
        passwordField.putClientProperty("JTextField.placeholderText", "8+ characters, letter and number");
        passwordField.setToolTipText("Password must be " + service.AuthService.PASSWORD_RULE);
        confirmPasswordField = UIConstants.createDarkPasswordField(20);
        addField(card, row, 0, 1, "Password", passwordField);
        row = addField(card, row, 1, 1, "Confirm password", confirmPasswordField);

        gbc.gridy = row;
        gbc.weighty = 1.0;
        card.add(Box.createGlue(), gbc);

        root.add(card, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, UIConstants.SPACING_MD, 0));
        buttons.setBackground(UIConstants.DARK_BG);
        buttons.setBorder(new EmptyBorder(UIConstants.SPACING_LG, 0, 0, 0));

        JButton cancelButton = UIConstants.createButton("Cancel", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        cancelButton.setPreferredSize(new Dimension(0, UIConstants.BUTTON_HEIGHT_LG));
        cancelButton.addActionListener(e -> dispose());

        JButton createButton = UIConstants.createButton("Create Account", UIConstants.DARK_ACCENT_BLUE,
                UIConstants.DARK_ACCENT_BLUE.darker(), Color.WHITE, UIConstants.FONT_BUTTON_LARGE);
        createButton.addActionListener(this::handleRegister);

        buttons.add(cancelButton);
        buttons.add(createButton);
        root.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(createButton);
    }

    private JPanel createRoleSwitch() {
        JPanel bar = new JPanel(new GridLayout(1, 2, 4, 0));
        bar.setBackground(UIConstants.DARK_FIELD_BG);
        bar.setBorder(new CompoundBorder(new LineBorder(UIConstants.DARK_BORDER, 1), new EmptyBorder(4, 4, 4, 4)));

        customerToggle = createToggle("Customer");
        adminToggle = createToggle("Admin");
        ButtonGroup group = new ButtonGroup();
        group.add(customerToggle);
        group.add(adminToggle);
        customerToggle.setSelected(true);

        bar.add(customerToggle);
        bar.add(adminToggle);
        refreshRoleSwitch();
        return bar;
    }

    private JPanel createPhotoPicker() {
        JPanel picker = new JPanel(new BorderLayout(UIConstants.SPACING_LG, 0));
        picker.setOpaque(false);

        avatar = new ProfilePhotos.Avatar((BufferedImage) null, 76);
        avatarLabel = new JLabel(avatar);
        avatarLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        avatarLabel.setToolTipText("Choose a profile photo");
        avatarLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                choosePhoto();
            }
        });
        picker.add(avatarLabel, BorderLayout.WEST);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setOpaque(false);

        JLabel title = UIConstants.createDarkBodyLabel("Profile photo");
        title.setFont(UIConstants.FONT_BODY_BOLD);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(title);
        side.add(Box.createRigidArea(new Dimension(0, 2)));

        JLabel hint = UIConstants.createDarkMutedLabel("Optional \u00B7 JPG or PNG \u00B7 shows who is signed in");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(hint);
        side.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton uploadButton = UIConstants.createButton("Upload Photo", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        uploadButton.setPreferredSize(new Dimension(130, 32));
        uploadButton.addActionListener(e -> choosePhoto());
        buttons.add(uploadButton);
        buttons.add(Box.createRigidArea(new Dimension(UIConstants.SPACING_SM, 0)));

        removePhotoButton = new JButton("Remove");
        removePhotoButton.setFont(UIConstants.FONT_BODY);
        removePhotoButton.setForeground(UIConstants.DARK_TEXT_MUTED);
        removePhotoButton.setContentAreaFilled(false);
        removePhotoButton.setBorderPainted(false);
        removePhotoButton.setFocusPainted(false);
        removePhotoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removePhotoButton.setVisible(false);
        removePhotoButton.addActionListener(e -> setPhoto(null));
        buttons.add(removePhotoButton);
        side.add(buttons);

        picker.add(side, BorderLayout.CENTER);
        return picker;
    }

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Profile Photo");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (JPG, PNG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        BufferedImage img = ProfilePhotos.read(chooser.getSelectedFile());
        if (img == null) {
            showError("That file is not a JPG or PNG image.");
            return;
        }
        setPhoto(img);
    }

    private void setPhoto(BufferedImage img) {
        chosenPhoto = img;
        avatar.setPhoto(img);
        avatarLabel.repaint();
        removePhotoButton.setVisible(img != null);
    }

    private JToggleButton createToggle(String text) {
        JToggleButton toggle = new JToggleButton(text);
        toggle.setUI(new BasicToggleButtonUI());
        toggle.setFont(UIConstants.FONT_BUTTON);
        toggle.setFocusPainted(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setOpaque(true);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setPreferredSize(new Dimension(0, 34));
        toggle.addItemListener(e -> refreshRoleSwitch());
        return toggle;
    }

    private void refreshRoleSwitch() {
        if (customerToggle == null || adminToggle == null) return;
        for (JToggleButton t : new JToggleButton[]{customerToggle, adminToggle}) {
            t.setBackground(t.isSelected() ? UIConstants.DARK_ACCENT_BLUE : UIConstants.DARK_FIELD_BG);
            t.setForeground(t.isSelected() ? Color.WHITE : UIConstants.DARK_TEXT_MUTED);
        }
        if (roleRow != null) {
            ((CardLayout) roleRow.getLayout()).show(roleRow, customerToggle.isSelected() ? "customer" : "admin");
        }
    }

    /** A label with its input underneath, as one block. */
    private JPanel labeledColumn(JLabel label, JComponent field) {
        JPanel col = new JPanel(new BorderLayout(0, UIConstants.SPACING_XS));
        col.setOpaque(false);
        field.setPreferredSize(new Dimension(10, UIConstants.INPUT_HEIGHT + 4));
        col.add(label, BorderLayout.NORTH);
        col.add(field, BorderLayout.CENTER);
        return col;
    }

    private int addField(JPanel card, int row, int col, int span, String label, JComponent field) {
        return addField(card, row, col, span, UIConstants.createDarkSecondaryLabel(label), field);
    }

    /** Adds a label with its input underneath; returns the next free row. */
    private int addField(JPanel card, int row, int col, int span, JLabel label, JComponent field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;
        gbc.gridx = col;
        gbc.gridwidth = span;
        int left = col == 1 ? UIConstants.SPACING_SM : 0;
        int right = span == 1 && col == 0 ? UIConstants.SPACING_SM : 0;

        gbc.gridy = row;
        gbc.insets = new Insets(0, left, UIConstants.SPACING_XS, right);
        card.add(label, gbc);

        field.setPreferredSize(new Dimension(10, UIConstants.INPUT_HEIGHT + 4));
        gbc.gridy = row + 1;
        gbc.insets = new Insets(0, left, UIConstants.SPACING_MD, right);
        card.add(field, gbc);
        return row + 2;
    }

    private void handleRegister(ActionEvent e) {
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String license = licenseField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmPasswordField.getPassword());
        boolean customer = customerToggle.isSelected();

        // All account rules live in AuthService; only the confirm-password match is a screen concern.
        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            confirmPasswordField.setText("");
            confirmPasswordField.requestFocus();
            return;
        }

        try {
            createdUser = customer
                    ? authService.registerCustomer(username, password, fullName, email, phone, license)
                    : authService.registerAdmin(username, password, fullName, email, phone,
                            approverUserField.getText().trim(), new String(approverPassField.getPassword()));
            if (chosenPhoto != null) {
                try {
                    ProfilePhotos.save(createdUser.getUserId(), chosenPhoto);
                } catch (IOException ex) {
                    showError("Account created, but the photo could not be saved: " + ex.getMessage());
                }
            }
            JOptionPane.showMessageDialog(this,
                    (customer ? "Customer" : "Admin") + " account created for " + fullName
                            + ".\nYou can now sign in as \"" + createdUser.getUsername() + "\".",
                    "Account Created", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (ValidationException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Create Account", JOptionPane.ERROR_MESSAGE);
    }

    /** The account that was created, or null if the dialog was cancelled. */
    public User getCreatedUser() {
        return createdUser;
    }
}
