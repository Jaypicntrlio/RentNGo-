package gui;

import model.Customer;
import service.AuthService;
import service.BookingService;
import model.exceptions.ValidationException;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ManageCustomersFrame extends JFrame {
    private final AuthService authService;
    private final BookingService bookingService;

    private JTable customerTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JCheckBox showInactiveBox;
    private JButton deactivateButton;
    private JButton reactivateButton;

    private ProfilePhotos.Avatar previewAvatar;
    private JLabel previewImage;
    private JLabel previewName;
    private JLabel previewPhone;
    private JLabel previewAddress;
    private JLabel previewLicense;
    private JLabel previewStatus;

    public ManageCustomersFrame(JFrame parent, AuthService authService, BookingService bookingService) {
        this.authService = authService;
        this.bookingService = bookingService;
        initializeUI(parent);
        loadCustomers();
    }

    private void initializeUI(JFrame parent) {
        UIConstants.styleDarkFrame(this, "RentNGo - Manage Customers", 1150, 680);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.DARK_BG);
        root.add(createToolbar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(UIConstants.SPACING_LG, 0));
        center.setBackground(UIConstants.DARK_BG);
        center.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        center.add(createTablePanel(), BorderLayout.CENTER);
        center.add(createPreviewPanel(), BorderLayout.EAST);
        root.add(center, BorderLayout.CENTER);

        add(root);
    }

    private JPanel createToolbar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_LG)
        ));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 0));
        buttons.setBackground(UIConstants.DARK_CARD);

        JButton addButton = UIConstants.createSuccessButton("+ Add");
        addButton.addActionListener(e -> showCustomerDialog(null));
        buttons.add(addButton);

        JButton editButton = UIConstants.createPrimaryButton("Edit");
        editButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.EDIT));
        editButton.addActionListener(e -> editSelected());
        buttons.add(editButton);

        // customers are deactivated rather than deleted, so their bookings and payments keep their name
        deactivateButton = UIConstants.createDangerButton("Deactivate");
        deactivateButton.setToolTipText("Stop this customer logging in or booking; their history is kept");
        deactivateButton.addActionListener(e -> deactivateSelected());
        buttons.add(deactivateButton);

        reactivateButton = UIConstants.createSecondaryButton("Reactivate");
        reactivateButton.setToolTipText("Let a deactivated customer log in and book again");
        reactivateButton.addActionListener(e -> reactivateSelected());
        buttons.add(reactivateButton);

        showInactiveBox = new JCheckBox("Show inactive");
        showInactiveBox.setOpaque(false);
        showInactiveBox.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        showInactiveBox.setFont(UIConstants.FONT_BODY);
        showInactiveBox.setFocusPainted(false);
        showInactiveBox.addActionListener(e -> loadCustomers());
        buttons.add(Box.createRigidArea(new Dimension(UIConstants.SPACING_MD, 0)));
        buttons.add(showInactiveBox);

        panel.add(buttons, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        searchPanel.setBackground(UIConstants.DARK_CARD);
        searchField = UIConstants.createDarkTextField(20);
        searchField.setPreferredSize(new Dimension(220, UIConstants.INPUT_HEIGHT));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { loadCustomers(); }
            @Override public void removeUpdate(DocumentEvent e) { loadCustomers(); }
            @Override public void changedUpdate(DocumentEvent e) { loadCustomers(); }
        });
        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setForeground(UIConstants.DARK_TEXT_MUTED);
        searchPanel.add(searchIcon);
        searchPanel.add(searchField);
        panel.add(searchPanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_BG);

        String[] columns = {"ID", "Name", "Contact No.", "Email", "License No.", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        customerTable = UIConstants.createDarkStyledTable(tableModel);
        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreview();
        });

        panel.add(UIConstants.createDarkScrollPane(customerTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));
        panel.setPreferredSize(new Dimension(280, 0));

        JLabel heading = UIConstants.createDarkSubheadingLabel("Customer Details");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        previewAvatar = new ProfilePhotos.Avatar((java.awt.image.BufferedImage) null, 140);
        previewImage = new JLabel(previewAvatar, SwingConstants.CENTER);
        previewImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewImage.setPreferredSize(new Dimension(246, 160));
        previewImage.setMaximumSize(new Dimension(246, 160));
        panel.add(previewImage);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        previewName = UIConstants.createDarkSubheadingLabel("Select a customer");
        previewName.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewName);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        previewPhone = UIConstants.createDarkSecondaryLabel("Contact: -");
        previewPhone.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewPhone);

        previewAddress = UIConstants.createDarkSecondaryLabel("Email: -");
        previewAddress.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewAddress);

        previewLicense = UIConstants.createDarkSecondaryLabel("License: -");
        previewLicense.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewLicense);

        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));
        previewStatus = UIConstants.createDarkSecondaryLabel(" ");
        previewStatus.setFont(UIConstants.FONT_BODY_BOLD);
        previewStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(previewStatus);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void updatePreview() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            previewName.setText("Select a customer");
            previewPhone.setText("Contact: -");
            previewAddress.setText("Email: -");
            previewLicense.setText("License: -");
            previewStatus.setText(" ");
            previewAvatar.setPhoto(null);
            previewImage.repaint();
            updateButtons(null);
            return;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        findCustomer(id).ifPresent(c -> {
            previewName.setText(c.getFullName());
            previewPhone.setText("Contact: " + c.getPhone());
            previewAddress.setText("Email: " + c.getEmail());
            previewLicense.setText("License: " + c.getLicenseNumber());
            previewStatus.setText(c.isActive() ? "Active" : "Deactivated \u2014 can't log in or book");
            previewStatus.setForeground(c.isActive() ? UIConstants.DARK_TREND_UP : UIConstants.WARNING);
            previewAvatar.setPhoto(ProfilePhotos.load(c.getUserId()));
            previewImage.repaint();
            updateButtons(c);
        });
    }

    private java.util.Optional<Customer> findCustomer(String id) {
        return authService.getAllCustomers().stream().filter(c -> c.getUserId().equals(id)).findFirst();
    }

    private void loadCustomers() {
        tableModel.setRowCount(0);
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        boolean showInactive = showInactiveBox != null && showInactiveBox.isSelected();
        List<Customer> customers = authService.getAllCustomers();
        for (Customer c : customers) {
            if (!c.isActive() && !showInactive) continue;
            String haystack = (c.getUserId() + c.getFullName() + c.getLicenseNumber()).toLowerCase();
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            tableModel.addRow(new Object[]{
                    c.getUserId(), c.getFullName(), c.getPhone(), c.getEmail(), c.getLicenseNumber(),
                    c.isActive() ? "Active" : "Inactive"
            });
        }
        updatePreview();
    }

    /** Deactivate for active customers, Reactivate for deactivated ones. */
    private void updateButtons(Customer selected) {
        if (deactivateButton == null) return;
        deactivateButton.setEnabled(selected != null && selected.isActive());
        reactivateButton.setEnabled(selected != null && !selected.isActive());
    }

    /** Selects the row for this customer ID, if it is shown. */
    private void select(String id) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (id.equals(tableModel.getValueAt(row, 0))) {
                customerTable.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private void showCustomerDialog(Customer customer) {
        if (!WindowSession.requireAdmin(this, authService)) return;
        CustomerDialog dialog = new CustomerDialog(this, authService, customer);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadCustomers();
        }
    }

    private void editSelected() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a customer to edit", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        findCustomer(id).ifPresent(this::showCustomerDialog);
    }

    private void deactivateSelected() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a customer to deactivate", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Deactivate " + name + "?\n\nThey won't be able to log in or make bookings.\n"
                        + "Their bookings and payments are kept, and you can reactivate them later.",
                "Deactivate Customer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            // the service refuses while they have an open rental or owe money, and says which bookings
            authService.deactivateCustomer(id, bookingService);
            loadCustomers();
            select(id);
        } catch (ValidationException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Can't Deactivate", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void reactivateSelected() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a customer to reactivate", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        try {
            authService.reactivateCustomer(id);
            loadCustomers();
            select(id);
        } catch (ValidationException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Can't Reactivate", JOptionPane.WARNING_MESSAGE);
        }
    }
}
