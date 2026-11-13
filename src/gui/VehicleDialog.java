package gui;

import model.*;
import service.VehicleService;
import model.exceptions.ValidationException;
import model.exceptions.VehicleNotFoundException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;

public class VehicleDialog extends JDialog {
    private VehicleService vehicleService;
    private Vehicle vehicle;
    private boolean saved = false;

    private JComboBox<String> typeComboBox;
    private JTextField vehicleIdField;
    private JTextField brandField;
    private JTextField modelField;
    private JSpinner yearSpinner;
    private JTextField priceField;
    private JTextField licensePlateField;
    private JCheckBox inServiceCheckBox;

    private JPanel specificFieldsPanel;
    private JSpinner doorsSpinner;
    private JComboBox<String> fuelTypeComboBox;
    private JCheckBox fourWheelDriveCheckBox;
    private JSpinner seatingSpinner;
    private JSpinner loadCapacitySpinner;
    private JCheckBox liftGateCheckBox;

    private SilhouettePanel photoPreview;
    private JLabel photoNameLabel;
    private File chosenImage;

    public VehicleDialog(JFrame parent, VehicleService vehicleService, Vehicle vehicle) {
        super(parent, vehicle == null ? "Add Vehicle" : "Edit Vehicle", true);
        this.vehicleService = vehicleService;
        this.vehicle = vehicle;
        initializeUI();
        if (vehicle != null) {
            populateFields();
            vehicleIdField.setEnabled(false);
            vehicleIdField.setBackground(UIConstants.DARK_BORDER);
        }
    }

    private void initializeUI() {
        UIConstants.styleDarkDialog(this, vehicle == null ? "Add Vehicle" : "Edit Vehicle");
        setSize(UIConstants.DIALOG_WIDTH_LARGE, 800);
        setLocationRelativeTo(getParent());
        setResizable(false);
        setLayout(new BorderLayout());

        // Background
        JPanel backgroundPanel = new JPanel(new BorderLayout());
        backgroundPanel.setBackground(UIConstants.DARK_BG);

        // Form panel in a card
        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setBackground(UIConstants.DARK_CARD);
        formCard.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Title
        JLabel titleLabel = UIConstants.createDarkHeadingLabel(vehicle == null ? "Add New Vehicle" : "Edit Vehicle");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_XS, 0);
        formCard.add(titleLabel, gbc);

        JLabel subtitleLabel = UIConstants.createDarkSecondaryLabel("Fill in the vehicle details below");
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        formCard.add(subtitleLabel, gbc);

        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        // Vehicle Type
        JLabel typeLabel = UIConstants.createDarkBodyLabel("Vehicle Type:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        formCard.add(typeLabel, gbc);

        typeComboBox = UIConstants.createDarkComboBox(new String[]{"Car", "SUV", "Truck"});
        typeComboBox.setPreferredSize(new Dimension(200, UIConstants.INPUT_HEIGHT));
        typeComboBox.addItemListener(this::onTypeChanged);
        gbc.gridx = 1;
        formCard.add(typeComboBox, gbc);

        // Common fields
        addLabeledField(formCard, "Vehicle ID:", vehicleIdField = UIConstants.createDarkTextField(20), 3);
        addLabeledField(formCard, "Brand:", brandField = UIConstants.createDarkTextField(20), 4);
        addLabeledField(formCard, "Model:", modelField = UIConstants.createDarkTextField(20), 5);

        // Year
        JLabel yearLabel = UIConstants.createDarkBodyLabel("Year:");
        gbc.gridx = 0;
        gbc.gridy = 6;
        formCard.add(yearLabel, gbc);

        yearSpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(java.time.Year.now().getValue(), VehicleService.MIN_YEAR, VehicleService.maxYear(), 1));
        yearSpinner.setEditor(new JSpinner.NumberEditor(yearSpinner, "#"));
        UIConstants.styleDarkSpinnerEditor(yearSpinner);
        gbc.gridx = 1;
        formCard.add(yearSpinner, gbc);

        addLabeledField(formCard, "Price/Day (\u20B1):", priceField = UIConstants.createDarkTextField(20), 7);
        addLabeledField(formCard, "License Plate:", licensePlateField = UIConstants.createDarkTextField(20), 8);

        // In service (unchecked = under maintenance, can't be booked)
        inServiceCheckBox = new JCheckBox("In service (uncheck while under maintenance)", true);
        inServiceCheckBox.setOpaque(false);
        inServiceCheckBox.setFont(UIConstants.FONT_BODY);
        inServiceCheckBox.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        gbc.gridx = 1;
        gbc.gridy = 9;
        formCard.add(inServiceCheckBox, gbc);

        // Photo
        JLabel photoLabel = UIConstants.createDarkBodyLabel("Photo:");
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formCard.add(photoLabel, gbc);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 1;
        formCard.add(createPhotoPicker(), gbc);

        // Type-specific fields panel
        specificFieldsPanel = new JPanel(new GridBagLayout());
        specificFieldsPanel.setBackground(UIConstants.DARK_CARD);
        specificFieldsPanel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));
        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        formCard.add(specificFieldsPanel, gbc);

        // Scroll pane for the form
        JScrollPane scrollPane = new JScrollPane(formCard);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(UIConstants.DARK_BG);
        scrollPane.getViewport().setBackground(UIConstants.DARK_BG);
        backgroundPanel.add(scrollPane, BorderLayout.CENTER);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        buttonPanel.setBackground(UIConstants.DARK_BG);
        buttonPanel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, 0, 0, 0));

        JButton saveButton = UIConstants.createPrimaryButton("Save");
        saveButton.setFont(UIConstants.FONT_BUTTON_LARGE);
        saveButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        saveButton.addActionListener(this::handleSave);
        buttonPanel.add(saveButton);

        JButton cancelButton = UIConstants.createSecondaryButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_LG));
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        backgroundPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(backgroundPanel);

        onTypeChanged(null);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel createPhotoPicker() {
        JPanel picker = new JPanel(new BorderLayout(UIConstants.SPACING_MD, 0));
        picker.setBackground(UIConstants.DARK_CARD);

        String type = vehicle == null ? "CAR" : vehicle.getType().toUpperCase();
        photoPreview = new SilhouettePanel(SilhouettePanel.Kind.valueOf(type));
        photoPreview.setDarkMode(true);
        photoPreview.setPreferredSize(new Dimension(170, 110));
        if (vehicle != null) {
            photoPreview.setPhoto(VehicleImages.load(vehicle));
        }
        picker.add(photoPreview, BorderLayout.WEST);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(UIConstants.DARK_CARD);

        JButton chooseButton = UIConstants.createPrimaryButton("Choose Image...");
        chooseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        chooseButton.addActionListener(e -> chooseImage());
        side.add(chooseButton);
        side.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        photoNameLabel = UIConstants.createDarkMutedLabel("JPG or PNG");
        photoNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(photoNameLabel);

        picker.add(side, BorderLayout.CENTER);
        return picker;
    }

    private void chooseImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Vehicle Photo");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (JPG, PNG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        java.awt.image.BufferedImage img = null;
        try {
            img = VehicleImages.isSupported(file) ? javax.imageio.ImageIO.read(file) : null;
        } catch (IOException ignored) {
            // reported below
        }
        if (img == null) {
            showError("That file is not a JPG or PNG image.");
            return;
        }
        chosenImage = file;
        photoPreview.setPhoto(img);
        photoNameLabel.setText(file.getName());
    }

    private void addLabeledField(JPanel panel, String labelText, JComponent field, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel label = UIConstants.createDarkBodyLabel(labelText);
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(label, gbc);

        field.setPreferredSize(new Dimension(0, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private void onTypeChanged(ItemEvent e) {
        specificFieldsPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        String type = (String) typeComboBox.getSelectedItem();
        int row = 0;
        if (photoPreview != null) {
            photoPreview.setKind(SilhouettePanel.Kind.valueOf(type.toUpperCase()));
        }

        if ("Car".equals(type)) {
            addSpecificField(specificFieldsPanel, "Number of Doors:", doorsSpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(4, 2, 5, 1)), row++);
            addSpecificField(specificFieldsPanel, "Fuel Type:", fuelTypeComboBox = UIConstants.createDarkComboBox(new String[]{"Gasoline", "Diesel", "Electric", "Hybrid"}), row++);
        } else if ("SUV".equals(type)) {
            fourWheelDriveCheckBox = new JCheckBox();
            fourWheelDriveCheckBox.setBackground(UIConstants.DARK_CARD);
            fourWheelDriveCheckBox.setFont(UIConstants.FONT_BODY);
            addSpecificField(specificFieldsPanel, "4-Wheel Drive:", fourWheelDriveCheckBox, row++);
            addSpecificField(specificFieldsPanel, "Seating Capacity:", seatingSpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(5, 4, 8, 1)), row++);
        } else if ("Truck".equals(type)) {
            addSpecificField(specificFieldsPanel, "Load Capacity (tons):", loadCapacitySpinner = UIConstants.createDarkSpinner(new SpinnerNumberModel(2.0, 0.5, 10.0, 0.5)), row++);
            liftGateCheckBox = new JCheckBox();
            liftGateCheckBox.setBackground(UIConstants.DARK_CARD);
            liftGateCheckBox.setFont(UIConstants.FONT_BODY);
            addSpecificField(specificFieldsPanel, "Lift Gate:", liftGateCheckBox, row++);
        }

        specificFieldsPanel.revalidate();
        specificFieldsPanel.repaint();
    }

    private void addSpecificField(JPanel panel, String labelText, JComponent field, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel label = UIConstants.createDarkBodyLabel(labelText);
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(label, gbc);

        if (field instanceof JSpinner) {
            field.setPreferredSize(new Dimension(120, UIConstants.INPUT_HEIGHT));
        } else if (field instanceof JComboBox) {
            field.setPreferredSize(new Dimension(200, UIConstants.INPUT_HEIGHT));
        }
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private void populateFields() {
        vehicleIdField.setText(vehicle.getVehicleId());
        brandField.setText(vehicle.getBrand());
        modelField.setText(vehicle.getModel());
        yearSpinner.setValue(vehicle.getYear());
        priceField.setText(vehicle.getPricePerDay().toPlainString());
        licensePlateField.setText(vehicle.getLicensePlate());
        typeComboBox.setSelectedItem(vehicle.getType());
        inServiceCheckBox.setSelected(vehicle.isAvailable());

        if (vehicle instanceof Car) {
            Car car = (Car) vehicle;
            doorsSpinner.setValue(car.getNumberOfDoors());
            fuelTypeComboBox.setSelectedItem(car.getFuelType());
        } else if (vehicle instanceof SUV) {
            SUV suv = (SUV) vehicle;
            fourWheelDriveCheckBox.setSelected(suv.isFourWheelDrive());
            seatingSpinner.setValue(suv.getSeatingCapacity());
        } else if (vehicle instanceof Truck) {
            Truck truck = (Truck) vehicle;
            loadCapacitySpinner.setValue(truck.getLoadCapacity());
            liftGateCheckBox.setSelected(truck.isHasLiftGate());
        }
    }

    private void handleSave(ActionEvent e) {
        try {
            String vehicleId = vehicleIdField.getText().trim();
            String brand = brandField.getText().trim();
            String model = modelField.getText().trim();
            int year = (Integer) yearSpinner.getValue();
            // Turning the text into a number is the screen's job; every rule is checked by VehicleService.
            java.math.BigDecimal price = Money.parse(priceField.getText());
            String licensePlate = licensePlateField.getText().trim();
            String type = (String) typeComboBox.getSelectedItem();

            Vehicle newVehicle = createVehicle(vehicleId, brand, model, year, price, licensePlate, type);
            if (vehicle != null) {
                newVehicle.setImagePath(vehicle.getImagePath());
            }
            newVehicle.setAvailable(inServiceCheckBox.isSelected());

            if (vehicle == null) {
                vehicleService.addVehicle(newVehicle);
            } else {
                vehicleService.updateVehicle(vehicleId, newVehicle);
            }

            if (chosenImage != null) {
                try {
                    String stored = VehicleImages.importImage(chosenImage, vehicleId);
                    vehicleService.setVehicleImage(vehicleId, stored);
                } catch (IOException ex) {
                    showError("Vehicle saved, but the photo could not be copied: " + ex.getMessage());
                }
            }

            saved = true;
            dispose();

        } catch (NumberFormatException ex) {
            showError("Price/Day must be a number, e.g. 2500");
        } catch (ValidationException | VehicleNotFoundException ex) {
            showError(ex.getMessage());
        }
    }

    private Vehicle createVehicle(String vehicleId, String brand, String model, int year, java.math.BigDecimal price, String licensePlate, String type) {
        switch (type) {
            case "Car":
                return new Car(vehicleId, brand, model, year, price, licensePlate,
                        (Integer) doorsSpinner.getValue(), (String) fuelTypeComboBox.getSelectedItem());
            case "SUV":
                return new SUV(vehicleId, brand, model, year, price, licensePlate,
                        fourWheelDriveCheckBox.isSelected(), (Integer) seatingSpinner.getValue());
            case "Truck":
                return new Truck(vehicleId, brand, model, year, price, licensePlate,
                        (Double) loadCapacitySpinner.getValue(), liftGateCheckBox.isSelected());
            default:
                throw new IllegalArgumentException("Unknown vehicle type: " + type);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}