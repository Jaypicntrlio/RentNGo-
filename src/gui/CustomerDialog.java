package gui;

import model.Customer;
import service.AuthService;
import model.exceptions.ValidationException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

public class CustomerDialog extends JDialog {
    private final AuthService authService;
    private final Customer customer;
    private boolean saved = false;

    private JTextField nameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField licenseField;

    public CustomerDialog(JFrame parent, AuthService authService, Customer customer) {
        super(parent, customer == null ? "Add Customer" : "Edit Customer", true);
        this.authService = authService;
        this.customer = customer;
        initializeUI();
        if (customer != null) {
            populateFields();
        }
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, customer == null ? "Add Customer" : "Edit Customer");
        setSize(UIConstants.DIALOG_WIDTH, 440);
        setLocationRelativeTo(getParent());
        setLayout(new BorderLayout());

        JPanel background = new JPanel(new BorderLayout());
        background.setBackground(UIConstants.DARK_BG);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 2;

        JLabel title = UIConstants.createDarkHeadingLabel(customer == null ? "Add New Customer" : "Edit Customer");
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        card.add(title, gbc);

        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        nameField = UIConstants.createDarkTextField(20);
        emailField = UIConstants.createDarkTextField(20);
        phoneField = UIConstants.createDarkTextField(20);
        licenseField = UIConstants.createDarkTextField(20);

        String[] labels = {"Name:", "Contact No.:", "Email:", "License No.:"};
        JComponent[] fields = {nameField, phoneField, emailField, licenseField};

        for (int i = 0; i < labels.length; i++) {
            JLabel label = UIConstants.createDarkBodyLabel(labels[i]);
            gbc.gridx = 0;
            gbc.gridy = i + 1;
            card.add(label, gbc);

            fields[i].setPreferredSize(new Dimension(0, UIConstants.INPUT_HEIGHT));
            gbc.gridx = 1;
            card.add(fields[i], gbc);
        }

        background.add(card, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        buttonPanel.setBackground(UIConstants.DARK_BG);
        buttonPanel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, 0, 0, 0));

        JButton saveButton = UIConstants.createPrimaryButton("Save");
        saveButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        saveButton.addActionListener(this::handleSave);
        buttonPanel.add(saveButton);

        JButton cancelButton = UIConstants.createSecondaryButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        background.add(buttonPanel, BorderLayout.SOUTH);
        add(background);
        getRootPane().setDefaultButton(saveButton);
    }

    private void populateFields() {
        nameField.setText(customer.getFullName());
        emailField.setText(customer.getEmail());
        phoneField.setText(customer.getPhone());
        licenseField.setText(customer.getLicenseNumber());
    }

    private void handleSave(ActionEvent e) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String license = licenseField.getText().trim();

        try {
            if (customer == null) {
                Customer created = authService.addCustomer(name, email, phone, license);
                String password = authService.takeTemporaryPassword(created.getUserId());
                saved = true;
                dispose();
                showCredentials(created, password);
            } else {
                authService.updateCustomer(customer.getUserId(), name, email, phone, license);
                saved = true;
                dispose();
            }
        } catch (ValidationException ex) {
            showError(ex.getMessage());
        }
    }

    private void showCredentials(Customer created, String password) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel heading = UIConstants.createDarkBodyLabel("Customer account created. Share these login details:");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.insets = new Insets(0, UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD);
        panel.add(heading, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);

        JLabel userLabel = UIConstants.createDarkBodyLabel("Username:");
        userLabel.setFont(UIConstants.FONT_BODY_BOLD);
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(userLabel, gbc);
        gbc.gridx = 1;
        panel.add(selectableValue(created.getUsername()), gbc);

        JLabel passLabel = UIConstants.createDarkBodyLabel("Password:");
        passLabel.setFont(UIConstants.FONT_BODY_BOLD);
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(passLabel, gbc);
        gbc.gridx = 1;
        panel.add(selectableValue(password), gbc);

        JLabel note = UIConstants.createDarkMutedLabel("This password is shown only once. The customer must change it at first login.");
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(UIConstants.SPACING_LG, UIConstants.SPACING_MD, 0, UIConstants.SPACING_MD);
        panel.add(note, gbc);

        JOptionPane.showMessageDialog(getParent(), panel, "New Customer Account", JOptionPane.INFORMATION_MESSAGE);
    }

    private JTextField selectableValue(String text) {
        JTextField field = new JTextField(text);
        field.setFont(UIConstants.FONT_MONO);
        field.setEditable(false);
        field.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        field.setBackground(UIConstants.DARK_FIELD_BG);
        field.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        field.setColumns(16);
        return field;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}
