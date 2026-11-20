package gui;

import model.PromoCode;
import model.exceptions.BookingException;
import service.AuthService;
import service.BookingService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Admin "Promo Codes" window: every code with its discount, expiry date and uses left, a form to
 * create a new one, and Delete. Customers and admins type a code when renting to get the discount.
 */
public class PromoCodesFrame extends JFrame {
    private static final String ACTIVE = "Active";

    private final AuthService authService;
    private final BookingService bookingService;

    private DefaultTableModel tableModel;
    private JTable table;

    private JTextField codeField;
    private JSpinner percentSpinner;
    private JSpinner expirySpinner;
    private JSpinner usesSpinner;

    public PromoCodesFrame(JFrame parent, AuthService authService, BookingService bookingService) {
        this.authService = authService;
        this.bookingService = bookingService;
        UIConstants.styleDarkFrame(this, "RentNGo - Promo Codes", 980, 560);
        setMinimumSize(new Dimension(900, 500));
        setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(UIConstants.SPACING_LG, 0));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        root.add(createListPanel(), BorderLayout.CENTER);
        root.add(createFormPanel(), BorderLayout.EAST);
        add(root);
        loadCodes();
    }

    private JPanel createListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UIConstants.SPACING_MD));
        panel.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("Promo Codes"), BorderLayout.WEST);
        JButton deleteButton = UIConstants.createDangerButton("Delete");
        deleteButton.setIcon(new UIConstants.ButtonIcon(UIConstants.ButtonIcon.Kind.DELETE));
        deleteButton.addActionListener(e -> deleteSelected());
        header.add(deleteButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        String[] columns = {"Code", "Discount", "Valid Until", "Uses Left", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = UIConstants.createDarkStyledTable(tableModel);
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        panel.add(UIConstants.createDarkScrollPane(table), BorderLayout.CENTER);

        JLabel note = UIConstants.createDarkMutedLabel("A code takes its discount off the rental cost (not late fees). "
                + "Cancelling a booking gives its use back.");
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createFormPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)));
        card.setPreferredSize(new Dimension(320, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, 0, UIConstants.SPACING_SM, UIConstants.SPACING_SM);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        card.add(UIConstants.createDarkSubheadingLabel("New Promo Code"), gbc);
        gbc.gridwidth = 1;

        codeField = UIConstants.createDarkTextField(12);
        codeField.putClientProperty("JTextField.placeholderText", "e.g. WEEKEND10");
        addRow(card, gbc, 1, "Code:", codeField);

        percentSpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(10, 1, BookingService.MAX_PROMO_PERCENT, 1));
        addRow(card, gbc, 2, "Discount (%):", percentSpinner);

        expirySpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(expirySpinner, "yyyy-MM-dd");
        editor.getFormat().setLenient(false);
        expirySpinner.setEditor(editor);
        UIConstants.styleDarkSpinnerEditor(expirySpinner);
        expirySpinner.setValue(java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        addRow(card, gbc, 3, "Valid Until:", expirySpinner);

        usesSpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(100, 1, BookingService.MAX_PROMO_USES, 1));
        addRow(card, gbc, 4, "Number of Uses:", usesSpinner);

        JButton createButton = UIConstants.createSuccessButton("+ Create Code");
        createButton.addActionListener(e -> createCode());
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(UIConstants.SPACING_LG, 0, 0, 0);
        card.add(createButton, gbc);

        gbc.gridy = 6;
        gbc.weighty = 1;
        card.add(Box.createVerticalGlue(), gbc);
        getRootPane().setDefaultButton(createButton);
        return card;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(UIConstants.createDarkBodyLabel(label), gbc);
        field.setPreferredSize(new Dimension(150, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void loadCodes() {
        tableModel.setRowCount(0);
        LocalDate today = LocalDate.now();
        for (PromoCode c : bookingService.getPromoCodes()) {
            String status = c.isExpired(today) ? "Expired" : c.getUsesLeft() <= 0 ? "Used up" : ACTIVE;
            tableModel.addRow(new Object[]{c.getCode(), c.getPercentOff() + "%", c.getExpiryDate().toString(),
                    String.format("%,d", c.getUsesLeft()), status});
        }
    }

    private void createCode() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        try {
            ((JSpinner.DefaultEditor) expirySpinner.getEditor()).commitEdit();
            percentSpinner.commitEdit();
            usesSpinner.commitEdit();
        } catch (java.text.ParseException ex) {
            showError("Check the numbers and the date (like 2026-12-31)");
            return;
        }
        LocalDate expiry = ((Date) expirySpinner.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        try {
            PromoCode created = bookingService.addPromoCode(codeField.getText(), (Integer) percentSpinner.getValue(),
                    expiry, (Integer) usesSpinner.getValue());
            codeField.setText("");
            loadCodes();
            JOptionPane.showMessageDialog(this, "Promo code " + created.getCode() + " created.", "Created", JOptionPane.INFORMATION_MESSAGE);
        } catch (BookingException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteSelected() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a promo code to delete", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String code = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete promo code " + code + "? Bookings that already used it keep their discount.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            bookingService.deletePromoCode(code);
            loadCodes();
        } catch (BookingException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Promo Codes", JOptionPane.ERROR_MESSAGE);
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(UIConstants.FONT_BODY_BOLD);
            c.setForeground(ACTIVE.equals(value) ? UIConstants.SUCCESS : UIConstants.DANGER);
            c.setBackground(isSelected ? UIConstants.DARK_ROW_SELECTED : (row % 2 == 0 ? UIConstants.DARK_CARD : UIConstants.DARK_BG));
            return c;
        }
    }
}
