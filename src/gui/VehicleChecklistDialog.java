package gui;

import model.VehicleCondition;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * The damage checklist the admin fills in at the counter: scratches, dents, fuel level,
 * cleanliness and a note. At return it also shows what was ticked at pick-up beside each item,
 * and lists anything new with the suggested charge. {@link #showDialog} returns the checklist, or
 * null when the admin cancels.
 */
public class VehicleChecklistDialog extends JDialog {

    private static final String CHOOSE = "Choose...";
    private static final String CLEAN = "Clean";
    private static final String NEEDS_CLEANING = "Needs cleaning";

    private final VehicleCondition pickUp; // null at pick-up, or when it wasn't recorded
    private final boolean atReturn;

    private JCheckBox scratchesBox;
    private JCheckBox dentsBox;
    private JComboBox<Object> fuelCombo;
    private JComboBox<String> cleanCombo;
    private JTextField notesField;
    private JLabel newProblemsLabel;
    private JLabel errorLabel;
    private VehicleCondition result;

    /**
     * @param atReturn  false for the pick-up checklist, true for the return checklist
     * @param pickUp    at return: the pick-up checklist to compare with (may be null for older rentals)
     * @param vehicle   e.g. "BKG0004 · Toyota Camry (ABC 1234)", shown under the heading
     */
    private VehicleChecklistDialog(Window owner, boolean atReturn, VehicleCondition pickUp, String vehicle) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.atReturn = atReturn;
        this.pickUp = pickUp;
        UIConstants.styleDarkDialog(this, atReturn ? "Return Checklist" : "Pick-up Checklist");
        buildUI(vehicle);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    /** Opens the checklist and waits; returns what was filled in, or null if cancelled. */
    public static VehicleCondition showDialog(Window owner, boolean atReturn, VehicleCondition pickUp, String vehicle) {
        VehicleChecklistDialog d = new VehicleChecklistDialog(owner, atReturn, pickUp, vehicle);
        d.setVisible(true);
        return d.result;
    }

    private void buildUI(String vehicle) {
        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        JPanel header = new JPanel(new BorderLayout(0, UIConstants.SPACING_XS));
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel(atReturn ? "Return Checklist" : "Pick-up Checklist"), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel(vehicle), BorderLayout.CENTER);
        header.add(UIConstants.createDarkMutedLabel(atReturn
                ? "Check the vehicle with the customer before taking it back."
                : "Walk around the vehicle with the customer before handing over the keys."), BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_MD, UIConstants.SPACING_LG, UIConstants.SPACING_MD, UIConstants.SPACING_LG)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM, UIConstants.SPACING_SM);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        boolean compare = atReturn;
        if (compare) {
            gbc.gridy = 0;
            gbc.gridx = 1;
            card.add(columnHeading("Now"), gbc);
            gbc.gridx = 2;
            card.add(columnHeading("At pick-up"), gbc);
        }

        scratchesBox = checkBox("Scratches");
        dentsBox = checkBox("Dents");
        Object[] fuels = new Object[VehicleCondition.Fuel.values().length + 1];
        fuels[0] = CHOOSE;
        System.arraycopy(VehicleCondition.Fuel.values(), 0, fuels, 1, fuels.length - 1);
        fuelCombo = new JComboBox<>(fuels);
        UIConstants.styleDarkCombo(fuelCombo);
        fuelCombo.setPreferredSize(new Dimension(170, UIConstants.INPUT_HEIGHT));
        cleanCombo = UIConstants.createDarkComboBox(new String[]{CHOOSE, CLEAN, NEEDS_CLEANING});
        cleanCombo.setPreferredSize(new Dimension(170, UIConstants.INPUT_HEIGHT));
        notesField = UIConstants.createDarkTextField(24);
        notesField.setPreferredSize(new Dimension(260, UIConstants.INPUT_HEIGHT));
        notesField.putClientProperty("JTextField.placeholderText", "Optional, e.g. scratch on rear bumper");

        row(card, gbc, 1, "Body:", scratchesBox, pickUp == null ? null : (pickUp.hasScratches() ? "Scratches" : "No scratches"));
        row(card, gbc, 2, "", dentsBox, pickUp == null ? null : (pickUp.hasDents() ? "Dents" : "No dents"));
        row(card, gbc, 3, "Fuel level:", fuelCombo, pickUp == null ? null : pickUp.getFuel().getLabel());
        row(card, gbc, 4, "Cleanliness:", cleanCombo, pickUp == null ? null : (pickUp.isClean() ? CLEAN : NEEDS_CLEANING));
        row(card, gbc, 5, "Notes:", notesField, pickUp == null ? null : (pickUp.getNotes() == null ? "—" : pickUp.getNotes()));

        if (compare) {
            newProblemsLabel = new JLabel(" ");
            newProblemsLabel.setFont(UIConstants.FONT_BODY_BOLD);
            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 3;
            card.add(newProblemsLabel, gbc);
            gbc.gridwidth = 1;
            Runnable update = this::updateNewProblems;
            scratchesBox.addActionListener(e -> update.run());
            dentsBox.addActionListener(e -> update.run());
            fuelCombo.addActionListener(e -> update.run());
            cleanCombo.addActionListener(e -> update.run());
            updateNewProblems();
        }
        root.add(card, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, UIConstants.SPACING_SM));
        south.setOpaque(false);
        errorLabel = new JLabel(" ");
        errorLabel.setFont(UIConstants.FONT_SMALL);
        errorLabel.setForeground(UIConstants.DANGER);
        south.add(errorLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        buttons.setOpaque(false);
        JButton cancel = UIConstants.createSecondaryButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = UIConstants.createSuccessButton(atReturn ? "Save Checklist" : "Save and Mark Picked Up");
        save.addActionListener(e -> save());
        buttons.add(cancel);
        buttons.add(save);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        setContentPane(root);
    }

    private void row(JPanel card, GridBagConstraints gbc, int y, String label, JComponent field, String atPickUp) {
        gbc.gridy = y;
        gbc.gridx = 0;
        card.add(UIConstants.createDarkBodyLabel(label), gbc);
        gbc.gridx = 1;
        card.add(field, gbc);
        if (atReturn) {
            gbc.gridx = 2;
            JLabel before = UIConstants.createDarkSecondaryLabel(atPickUp == null ? "Not recorded" : atPickUp);
            before.setPreferredSize(new Dimension(170, before.getPreferredSize().height));
            before.setToolTipText(atPickUp); // long notes are cut off: hover to read them
            card.add(before, gbc);
        }
    }

    private JLabel columnHeading(String text) {
        JLabel l = UIConstants.createDarkMutedLabel(text);
        l.setFont(UIConstants.FONT_BODY_BOLD);
        return l;
    }

    private JCheckBox checkBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setFont(UIConstants.FONT_BODY);
        box.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        return box;
    }

    /** The checklist as filled in so far, or null while fuel or cleanliness hasn't been chosen. */
    private VehicleCondition current() {
        if (!(fuelCombo.getSelectedItem() instanceof VehicleCondition.Fuel) || CHOOSE.equals(cleanCombo.getSelectedItem())) {
            return null;
        }
        return new VehicleCondition(scratchesBox.isSelected(), dentsBox.isSelected(),
                (VehicleCondition.Fuel) fuelCombo.getSelectedItem(), CLEAN.equals(cleanCombo.getSelectedItem()),
                notesField.getText());
    }

    private void updateNewProblems() {
        if (pickUp == null) {
            newProblemsLabel.setText("No pick-up checklist to compare with: set any charge yourself.");
            newProblemsLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
            return;
        }
        VehicleCondition now;
        try {
            now = current();
        } catch (IllegalArgumentException ex) {
            now = null;
        }
        if (now == null) {
            newProblemsLabel.setText("Choose the fuel level and cleanliness.");
            newProblemsLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
            return;
        }
        List<String> problems = VehicleCondition.newProblems(pickUp, now);
        if (problems.isEmpty()) {
            newProblemsLabel.setText("No new damage since pick-up.");
            newProblemsLabel.setForeground(UIConstants.SUCCESS);
        } else {
            BigDecimal charge = VehicleCondition.suggestedCharge(pickUp, now);
            newProblemsLabel.setText(String.format("New: %s · suggested charge ₱%,.2f",
                    String.join(", ", problems), charge));
            newProblemsLabel.setForeground(UIConstants.WARNING);
        }
    }

    private void save() {
        if (!(fuelCombo.getSelectedItem() instanceof VehicleCondition.Fuel)) {
            errorLabel.setText("Choose the fuel level.");
            fuelCombo.requestFocusInWindow();
            return;
        }
        if (CHOOSE.equals(cleanCombo.getSelectedItem())) {
            errorLabel.setText("Choose whether the vehicle is clean.");
            cleanCombo.requestFocusInWindow();
            return;
        }
        try {
            result = current();
        } catch (IllegalArgumentException ex) {
            errorLabel.setText(ex.getMessage());
            notesField.requestFocusInWindow();
            return;
        }
        dispose();
    }
}
