package gui;

import model.MaintenanceRecord;
import model.Money;
import model.Vehicle;
import model.exceptions.ValidationException;
import model.exceptions.VehicleNotFoundException;
import service.AuthService;
import service.VehicleService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Maintenance history of one vehicle (admin only): the list of past services with their costs,
 * and a form to record a new one. Opened from Manage Vehicles.
 */
public class MaintenanceDialog extends JDialog {
    /** Default gap until the next service, offered when "Plan next service" is ticked. */
    private static final int DEFAULT_NEXT_SERVICE_DAYS = 90;

    private final VehicleService vehicleService;
    private final AuthService authService;
    private final String vehicleId;

    private DefaultTableModel tableModel;
    private JLabel totalLabel;
    private JLabel nextServiceLabel;

    private JSpinner serviceDateSpinner;
    private JTextField workField;
    private JTextField costField;
    private JCheckBox planNextCheckBox;
    private JSpinner nextDateSpinner;
    private JCheckBox outOfServiceCheckBox;

    private boolean changed;

    public MaintenanceDialog(JFrame parent, AuthService authService, VehicleService vehicleService, Vehicle vehicle) {
        super(parent, true);
        this.vehicleService = vehicleService;
        this.authService = authService;
        this.vehicleId = vehicle.getVehicleId();
        UIConstants.styleDarkDialog(this, "Maintenance - " + vehicle.getBrand() + " " + vehicle.getModel() + " (" + vehicleId + ")");
        buildUI(vehicle);
        loadHistory();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI(Vehicle vehicle) {
        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("Maintenance History"), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel(vehicle.getBrand() + " " + vehicle.getModel() + " · "
                + vehicle.getLicensePlate() + " · " + (vehicle.isAvailable() ? "In service" : "Out of service")), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        root.add(createHistoryPanel(), BorderLayout.CENTER);
        root.add(createFormPanel(vehicle), BorderLayout.SOUTH);
        add(root);
    }

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UIConstants.SPACING_SM));
        panel.setOpaque(false);

        String[] columns = {"Record", "Service Date", "Work Done", "Cost", "Next Service"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = UIConstants.createDarkStyledTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        JScrollPane scroll = UIConstants.createDarkScrollPane(table);
        scroll.setPreferredSize(new Dimension(680, 200));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel summary = new JPanel(new BorderLayout());
        summary.setOpaque(false);
        totalLabel = UIConstants.createDarkBodyLabel("Total spent: -");
        totalLabel.setFont(UIConstants.FONT_BODY_BOLD);
        nextServiceLabel = UIConstants.createDarkSecondaryLabel("Next service: -");
        summary.add(totalLabel, BorderLayout.WEST);
        summary.add(nextServiceLabel, BorderLayout.EAST);
        panel.add(summary, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createFormPanel(Vehicle vehicle) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_LG)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4;
        card.add(UIConstants.createDarkSubheadingLabel("Record a Service"), gbc);
        gbc.gridwidth = 1;

        serviceDateSpinner = dateSpinner(LocalDate.now());
        addField(card, gbc, 1, 0, "Service Date:", serviceDateSpinner);
        costField = UIConstants.createDarkTextField(10);
        costField.setPreferredSize(new Dimension(140, UIConstants.INPUT_HEIGHT));
        addField(card, gbc, 1, 2, "Cost (₱):", costField);

        workField = UIConstants.createDarkTextField(30);
        workField.setPreferredSize(new Dimension(0, UIConstants.INPUT_HEIGHT));
        workField.putClientProperty("JTextField.placeholderText", "e.g. Oil change and tire rotation");
        gbc.gridx = 0;
        gbc.gridy = 2;
        card.add(UIConstants.createDarkBodyLabel("Work Done:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        card.add(workField, gbc);
        gbc.gridwidth = 1;

        planNextCheckBox = checkBox("Plan next service on:");
        nextDateSpinner = dateSpinner(LocalDate.now().plusDays(DEFAULT_NEXT_SERVICE_DAYS));
        nextDateSpinner.setEnabled(false);
        planNextCheckBox.addActionListener(e -> nextDateSpinner.setEnabled(planNextCheckBox.isSelected()));
        gbc.gridx = 0;
        gbc.gridy = 3;
        card.add(planNextCheckBox, gbc);
        gbc.gridx = 1;
        card.add(nextDateSpinner, gbc);

        outOfServiceCheckBox = checkBox("Take the vehicle out of service now (it is in the shop)");
        outOfServiceCheckBox.setVisible(vehicle.isAvailable()); // already out of service: nothing to switch
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 4;
        card.add(outOfServiceCheckBox, gbc);
        gbc.gridwidth = 1;

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        buttons.setOpaque(false);
        JButton closeButton = UIConstants.createSecondaryButton("Close");
        closeButton.addActionListener(e -> dispose());
        JButton saveButton = UIConstants.createSuccessButton("Save Record");
        saveButton.addActionListener(e -> saveRecord());
        buttons.add(closeButton);
        buttons.add(saveButton);
        getRootPane().setDefaultButton(saveButton);
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        card.add(buttons, gbc);
        return card;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, int column, String label, JComponent field) {
        gbc.gridx = column;
        gbc.gridy = row;
        panel.add(UIConstants.createDarkBodyLabel(label), gbc);
        gbc.gridx = column + 1;
        panel.add(field, gbc);
    }

    private JCheckBox checkBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setFont(UIConstants.FONT_BODY);
        box.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        return box;
    }

    private JSpinner dateSpinner(LocalDate initial) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd");
        editor.getFormat().setLenient(false); // impossible dates are refused, not rolled over
        spinner.setEditor(editor);
        UIConstants.styleDarkSpinnerEditor(spinner);
        spinner.setValue(java.sql.Date.valueOf(initial));
        spinner.setPreferredSize(new Dimension(150, UIConstants.INPUT_HEIGHT));
        return spinner;
    }

    private static LocalDate dateOf(JSpinner spinner) {
        return ((Date) spinner.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void loadHistory() {
        tableModel.setRowCount(0);
        List<MaintenanceRecord> records = vehicleService.getMaintenanceRecords(vehicleId);
        BigDecimal total = Money.ZERO;
        for (MaintenanceRecord r : records) {
            tableModel.addRow(new Object[]{
                    r.getRecordId(), r.getServiceDate().toString(), r.getDescription(),
                    String.format("₱%,.2f", r.getCost()),
                    r.getNextServiceDate() == null ? "-" : r.getNextServiceDate().toString()});
            total = total.add(r.getCost());
        }
        totalLabel.setText(String.format("Total spent: ₱%,.2f (%d record%s)", total, records.size(), records.size() == 1 ? "" : "s"));

        LocalDate next = vehicleService.getNextServiceDate(vehicleId).orElse(null);
        if (next == null) {
            nextServiceLabel.setText("Next service: not planned");
            nextServiceLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        } else {
            boolean due = !next.isAfter(LocalDate.now());
            nextServiceLabel.setText("Next service: " + next + (due ? " (due now)" : ""));
            nextServiceLabel.setForeground(due ? UIConstants.DANGER : UIConstants.DARK_TEXT_MUTED);
        }
    }

    private void saveRecord() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        BigDecimal cost;
        try {
            cost = costField.getText().isBlank() ? null : Money.parse(costField.getText());
        } catch (NumberFormatException ex) {
            showError("Cost must be a number, e.g. 1500");
            return;
        }
        if (cost == null) {
            showError("Enter the cost (0 if it was free)");
            return;
        }
        try {
            commitSpinner(serviceDateSpinner);
            commitSpinner(nextDateSpinner);
            LocalDate next = planNextCheckBox.isSelected() ? dateOf(nextDateSpinner) : null;
            boolean takeOut = outOfServiceCheckBox.isVisible() && outOfServiceCheckBox.isSelected();
            vehicleService.addMaintenanceRecord(vehicleId, dateOf(serviceDateSpinner), workField.getText(), cost, next, takeOut);
        } catch (java.text.ParseException ex) {
            showError("Dates must look like 2026-11-19");
            return;
        } catch (ValidationException | VehicleNotFoundException ex) {
            showError(ex.getMessage());
            // a record can be saved even when only the out-of-service switch failed
            changed = true;
            loadHistory();
            return;
        }
        changed = true;
        if (outOfServiceCheckBox.isSelected()) outOfServiceCheckBox.setVisible(false);
        workField.setText("");
        costField.setText("");
        planNextCheckBox.setSelected(false);
        nextDateSpinner.setEnabled(false);
        loadHistory();
        JOptionPane.showMessageDialog(this, "Maintenance record saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Takes a date typed into the spinner's text box, so it isn't lost when Save is clicked right away. */
    private static void commitSpinner(JSpinner spinner) throws java.text.ParseException {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) spinner.getEditor()).commitEdit();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Maintenance", JOptionPane.ERROR_MESSAGE);
    }

    /** True if a record was saved (the caller then refreshes its vehicle list). */
    public boolean isChanged() {
        return changed;
    }
}
