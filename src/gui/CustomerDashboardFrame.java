package gui;

import model.Booking;
import model.BookingStatus;
import model.Customer;
import model.Vehicle;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.VehicleService;
import model.exceptions.BookingException;
import model.exceptions.VehicleNotFoundException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerDashboardFrame extends JFrame {
    private AuthService authService;
    private VehicleService vehicleService;
    private BookingService bookingService;
    private PaymentService paymentService;
    private Customer currentCustomer;

    private JTable vehicleTable;
    private DefaultTableModel vehicleTableModel;
    private TableRowSorter<DefaultTableModel> vehicleSorter;

    // Available Vehicles search and filters
    private static final String ALL_TYPES = "All";
    private static final int PRICE_STEP = 500;
    private JTextField searchField;
    private JComboBox<String> typeFilter;
    private JSlider maxPriceSlider;
    private JLabel maxPriceLabel;
    private JLabel resultCountLabel;
    private JTable bookingTable;
    private DefaultTableModel bookingTableModel;
    private JTabbedPane tabbedPane;

    private SilhouettePanel detailPhoto;
    private JLabel detailName;
    private JLabel detailType;
    private JLabel detailPlate;
    private JLabel detailPrice;
    private JLabel detailSpecs;

    private JLabel totalStatLabel;
    private JLabel activeStatLabel;
    private JLabel completedStatLabel;

    public CustomerDashboardFrame(Customer customer, AuthService authService, VehicleService vehicleService, BookingService bookingService, PaymentService paymentService) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.currentCustomer = customer;
        initializeUI();
        loadAvailableVehicles();
        loadBookingHistory();
    }

    private void initializeUI() {
        UIConstants.styleDarkFrame(this, "RentNGo - Customer Dashboard", 1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 700));

        // Main panel with background
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIConstants.DARK_BG);

        // Header Panel
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        tabbedPane = new JTabbedPane();
        UIConstants.styleDarkTabs(tabbedPane);
        tabbedPane.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        tabbedPane.addTab("  Available Vehicles  ", createVehiclePanel());
        tabbedPane.addTab("  My Bookings  ", createBookingPanel());
        tabbedPane.addTab("  Profile  ", createProfilePanel());
        tabbedPane.addTab("  Rental Records  ", createRentalRecordsPanel());
        // bookings can also change from the admin side (returns, payments), so recount on every tab switch
        tabbedPane.addChangeListener(e -> loadBookingHistory());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        add(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new javax.swing.border.MatteBorder(0, 0, 1, 0, UIConstants.DARK_BORDER),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_LG, UIConstants.SPACING_XL)
        ));

        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_LG, 0));
        titleBox.setOpaque(false);
        titleBox.add(UIConstants.createBrandPanel(44, 22, true));
        JLabel titleLabel = new JLabel("Customer Dashboard");
        titleLabel.setFont(UIConstants.FONT_SUBHEADING);
        titleLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        titleBox.add(titleLabel);
        // CENTER, not WEST: on a narrow window the title is cut off instead of drawing under the buttons
        panel.add(titleBox, BorderLayout.CENTER);

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MD, 0));
        userPanel.setOpaque(false);

        // "CUSTOMER" in small gold capitals, the name in white, and a thin gold underline
        JPanel nameBox = new JPanel();
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        nameBox.setOpaque(false);
        nameBox.setBorder(new EmptyBorder(0, 0, 0, UIConstants.SPACING_LG));

        java.util.Map<java.awt.font.TextAttribute, Object> tracking = new java.util.HashMap<>();
        tracking.put(java.awt.font.TextAttribute.TRACKING, 0.25f);
        JLabel roleLabel = new JLabel("CUSTOMER");
        roleLabel.setFont(new Font("Segoe UI", Font.BOLD, 10).deriveFont(tracking));
        roleLabel.setForeground(UIConstants.CREST_GOLD);

        JLabel userLabel = new JLabel(currentCustomer.getFullName());
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        userLabel.setForeground(UIConstants.BRAND_WORD);
        userLabel.setBorder(new CompoundBorder(
                new javax.swing.border.MatteBorder(0, 0, 1, 0, UIConstants.CREST_GOLD),
                new EmptyBorder(0, 0, 3, 0)));

        nameBox.add(roleLabel);
        nameBox.add(Box.createRigidArea(new Dimension(0, 1)));
        nameBox.add(userLabel);
        userPanel.add(new JLabel(new ProfilePhotos.Avatar(currentCustomer.getUserId(), 40)));
        userPanel.add(nameBox);

        JButton logoutButton = UIConstants.createDangerButton("Logout");
        logoutButton.setPreferredSize(new Dimension(100, UIConstants.BUTTON_HEIGHT_MD));
        logoutButton.addActionListener(e -> logout());
        userPanel.add(logoutButton);

        JButton passwordButton = UIConstants.createSecondaryButton("Change Password");
        passwordButton.setPreferredSize(new Dimension(150, UIConstants.BUTTON_HEIGHT_MD));
        passwordButton.addActionListener(e -> new ChangePasswordDialog(this, authService, currentCustomer, false).setVisible(true));
        userPanel.add(passwordButton);

        JButton recordsButton = UIConstants.createPurpleButton("Rental Records");
        recordsButton.setPreferredSize(new Dimension(140, UIConstants.BUTTON_HEIGHT_MD));
        recordsButton.addActionListener(e -> showRentalRecords());
        userPanel.add(recordsButton);

        // Light/dark toggle sits at the end of the button row, same height as the buttons
        userPanel.add(Theme.createToggleButton(this::reopenWithCurrentTheme));

        panel.add(userPanel, BorderLayout.EAST);
        return panel;
    }

    private JPanel createVehiclePanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_BG);
        panel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        panel.add(createVehicleFilterBar(), BorderLayout.NORTH);

        String[] columns = {"ID", "Brand", "Model", "Year", "Type", "Price/Day", "License Plate", "Details"};
        vehicleTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        vehicleTable = UIConstants.createDarkStyledTable(vehicleTableModel);
        vehicleTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);

        // Sorting (click a header) and the search / type / price filters
        vehicleSorter = new TableRowSorter<>(vehicleTableModel);
        vehicleSorter.setComparator(3, java.util.Comparator.comparingInt(y -> Integer.parseInt(y.toString())));
        vehicleSorter.setComparator(5, java.util.Comparator.comparing(p -> parsePrice(p.toString())));
        vehicleTable.setRowSorter(vehicleSorter);

        // Column widths
        vehicleTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        vehicleTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        vehicleTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        vehicleTable.getColumnModel().getColumn(3).setPreferredWidth(70);
        vehicleTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        vehicleTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        vehicleTable.getColumnModel().getColumn(6).setPreferredWidth(120);
        vehicleTable.getColumnModel().getColumn(7).setPreferredWidth(200);

        JScrollPane scrollPane = UIConstants.createDarkScrollPane(vehicleTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        panel.add(createVehicleDetailsPanel(), BorderLayout.EAST);
        vehicleTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateVehicleDetails();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        buttonPanel.setBackground(UIConstants.DARK_BG);

        JButton rentButton = UIConstants.createSuccessButton("Rent Vehicle");
        rentButton.addActionListener(e -> showRentDialog());
        buttonPanel.add(rentButton);

        JButton refreshButton = UIConstants.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadAvailableVehicles());
        buttonPanel.add(refreshButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    /** Search box, type dropdown and max price slider above the vehicle table. */
    private JPanel createVehicleFilterBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, UIConstants.SPACING_MD, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, UIConstants.SPACING_MD);

        searchField = UIConstants.createDarkTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Search brand or model");
        searchField.setPreferredSize(new Dimension(230, UIConstants.INPUT_HEIGHT));
        searchField.setToolTipText("Search by brand or model");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyVehicleFilters(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyVehicleFilters(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyVehicleFilters(); }
        });
        gbc.gridx = 0;
        bar.add(searchField, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(0, UIConstants.SPACING_SM, 0, UIConstants.SPACING_SM);
        bar.add(UIConstants.createDarkSecondaryLabel("Type"), gbc);
        typeFilter = UIConstants.createDarkComboBox(new String[]{ALL_TYPES, "Car", "SUV", "Truck"});
        typeFilter.setPreferredSize(new Dimension(110, UIConstants.INPUT_HEIGHT));
        typeFilter.addActionListener(e -> applyVehicleFilters());
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 0, UIConstants.SPACING_LG);
        bar.add(typeFilter, gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(0, UIConstants.SPACING_SM, 0, UIConstants.SPACING_SM);
        bar.add(UIConstants.createDarkSecondaryLabel("Max price"), gbc);
        maxPriceSlider = new JSlider(0, PRICE_STEP, PRICE_STEP);
        maxPriceSlider.setOpaque(false);
        maxPriceSlider.setPreferredSize(new Dimension(170, UIConstants.INPUT_HEIGHT));
        maxPriceSlider.setToolTipText("Highest price per day to show");
        maxPriceSlider.addChangeListener(e -> applyVehicleFilters());
        gbc.gridx = 4;
        gbc.insets = new Insets(0, 0, 0, UIConstants.SPACING_SM);
        bar.add(maxPriceSlider, gbc);

        maxPriceLabel = new JLabel("Any price");
        maxPriceLabel.setFont(UIConstants.FONT_BODY_BOLD);
        maxPriceLabel.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        maxPriceLabel.setPreferredSize(new Dimension(118, UIConstants.INPUT_HEIGHT));
        gbc.gridx = 5;
        bar.add(maxPriceLabel, gbc);

        JButton clearButton = UIConstants.createButton("Clear", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        clearButton.setPreferredSize(new Dimension(76, UIConstants.INPUT_HEIGHT));
        clearButton.addActionListener(e -> clearVehicleFilters());
        gbc.gridx = 6;
        gbc.insets = new Insets(0, 0, 0, 0);
        bar.add(clearButton, gbc);

        resultCountLabel = UIConstants.createDarkSecondaryLabel(" ");
        gbc.gridx = 7;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        bar.add(resultCountLabel, gbc);
        return bar;
    }

    private void clearVehicleFilters() {
        searchField.setText("");
        typeFilter.setSelectedItem(ALL_TYPES);
        maxPriceSlider.setValue(maxPriceSlider.getMaximum());
    }

    /** Shows only the rows matching the search text, type and max price, then updates the count. */
    private void applyVehicleFilters() {
        if (vehicleSorter == null || maxPriceLabel == null) return;
        String query = searchField.getText().trim().toLowerCase();
        String type = (String) typeFilter.getSelectedItem();
        boolean anyPrice = maxPriceSlider.getValue() >= maxPriceSlider.getMaximum();
        java.math.BigDecimal maxPrice = java.math.BigDecimal.valueOf(maxPriceSlider.getValue());
        maxPriceLabel.setText(anyPrice ? "Any price" : String.format("\u20B1%,d / day", maxPriceSlider.getValue()));

        vehicleSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> row) {
                String name = (row.getStringValue(1) + " " + row.getStringValue(2)).toLowerCase();
                if (!query.isEmpty() && !name.contains(query)) return false;
                if (type != null && !ALL_TYPES.equals(type) && !type.equalsIgnoreCase(row.getStringValue(4))) return false;
                return anyPrice || parsePrice(row.getStringValue(5)).compareTo(maxPrice) <= 0;
            }
        });

        int shown = vehicleTable.getRowCount();
        int total = vehicleTableModel.getRowCount();
        resultCountLabel.setText(shown == total
                ? total + (total == 1 ? " vehicle" : " vehicles")
                : "Showing " + shown + " of " + total);
        if (vehicleTable.getSelectedRow() == -1 && shown > 0) {
            vehicleTable.setRowSelectionInterval(0, 0);
        } else {
            updateVehicleDetails();
        }
    }

    /** Sets the slider's range from the cheapest to the dearest vehicle, rounded to PRICE_STEP. */
    private void updatePriceSliderRange(List<Vehicle> vehicles) {
        if (maxPriceSlider == null) return;
        boolean wasAtMax = maxPriceSlider.getValue() >= maxPriceSlider.getMaximum();
        int low = Integer.MAX_VALUE, high = 0;
        for (Vehicle v : vehicles) {
            int price = v.getPricePerDay().intValue();
            low = Math.min(low, price);
            high = Math.max(high, v.getPricePerDay().setScale(0, java.math.RoundingMode.CEILING).intValue());
        }
        if (vehicles.isEmpty()) { low = 0; high = PRICE_STEP; }
        int min = (low / PRICE_STEP) * PRICE_STEP;
        int max = Math.max(min + PRICE_STEP, ((high + PRICE_STEP - 1) / PRICE_STEP) * PRICE_STEP);
        int value = wasAtMax ? max : Math.max(min, Math.min(max, maxPriceSlider.getValue()));
        maxPriceSlider.setMinimum(min);
        maxPriceSlider.setMaximum(max);
        maxPriceSlider.setMinorTickSpacing(PRICE_STEP);
        maxPriceSlider.setSnapToTicks(true);
        maxPriceSlider.setValue(value);
    }

    /** "₱1,500.00" -> 1500.00 */
    private static java.math.BigDecimal parsePrice(String text) {
        String digits = text.replace("\u20B1", "").replace(",", "").trim();
        try {
            return new java.math.BigDecimal(digits);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private JPanel createVehicleDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UIConstants.DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG)
        ));
        panel.setPreferredSize(new Dimension(290, 0));

        JLabel heading = UIConstants.createDarkSubheadingLabel("Vehicle Details");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        detailPhoto = new SilhouettePanel(SilhouettePanel.Kind.CAR);
        detailPhoto.setDarkMode(true);
        detailPhoto.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPhoto.setPreferredSize(new Dimension(256, 170));
        detailPhoto.setMaximumSize(new Dimension(256, 170));
        panel.add(detailPhoto);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        detailName = UIConstants.createDarkSubheadingLabel("Select a vehicle");
        detailName.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailName);
        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        detailType = UIConstants.createDarkSecondaryLabel("Type: -");
        detailPlate = UIConstants.createDarkSecondaryLabel("Plate No.: -");
        detailSpecs = UIConstants.createDarkSecondaryLabel("Details: -");
        for (JLabel l : new JLabel[]{detailType, detailPlate, detailSpecs}) {
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(l);
        }

        panel.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));
        detailPrice = new JLabel(" ");
        detailPrice.setFont(UIConstants.FONT_SUBHEADING);
        detailPrice.setForeground(UIConstants.DARK_ACCENT_BLUE);
        detailPrice.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailPrice);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void updateVehicleDetails() {
        int row = vehicleTable.getSelectedRow();
        Vehicle v = row == -1 ? null
                : vehicleService.getVehicleById((String) vehicleTableModel.getValueAt(vehicleTable.convertRowIndexToModel(row), 0)).orElse(null);
        if (v == null) {
            detailName.setText("Select a vehicle");
            detailType.setText("Type: -");
            detailPlate.setText("Plate No.: -");
            detailSpecs.setText("Details: -");
            detailPrice.setText(" ");
            detailPhoto.setPhoto(null);
            return;
        }
        detailName.setText(v.getBrand() + " " + v.getModel() + " (" + v.getYear() + ")");
        detailType.setText("Type: " + v.getType());
        detailPlate.setText("Plate No.: " + v.getLicensePlate());
        detailSpecs.setText("Details: " + getVehicleDetails(v));
        detailPrice.setText(String.format("\u20B1%,.2f / day", v.getPricePerDay()));
        detailPhoto.setKind(SilhouettePanel.Kind.of(v));
        detailPhoto.setPhoto(VehicleImages.load(v));
    }

    private JPanel createBookingPanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_BG);
        panel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        String[] columns = {"Booking ID", "Vehicle", "Start Date", "End Date", "Days", "Total Cost", "Status"};
        bookingTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        bookingTable = UIConstants.createDarkStyledTable(bookingTableModel);
        bookingTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);

        // Status column renderer
        bookingTable.getColumnModel().getColumn(6).setCellRenderer(new BookingStatusCellRenderer());

        // Column widths
        bookingTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        bookingTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        bookingTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        bookingTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        bookingTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        bookingTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        bookingTable.getColumnModel().getColumn(6).setPreferredWidth(130);

        JScrollPane scrollPane = UIConstants.createDarkScrollPane(bookingTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        buttonPanel.setBackground(UIConstants.DARK_BG);

        JButton cancelButton = UIConstants.createDangerButton("Cancel Booking");
        cancelButton.addActionListener(e -> cancelSelectedBooking());
        buttonPanel.add(cancelButton);

        JButton refreshButton = UIConstants.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadBookingHistory());
        buttonPanel.add(refreshButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout(UIConstants.SPACING_MD, UIConstants.SPACING_MD));
        panel.setBackground(UIConstants.DARK_BG);
        panel.setBorder(new EmptyBorder(UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        // Info Card
        JPanel infoCard = UIConstants.createDarkCardPanel();
        infoCard.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel sectionTitle = UIConstants.createDarkSubheadingLabel("Profile Information");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, UIConstants.SPACING_LG, 0);
        infoCard.add(sectionTitle, gbc);
        gbc.insets = new Insets(UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD, UIConstants.SPACING_MD);
        gbc.gridwidth = 1;

        String[] labels = {"Customer ID:", "Username:", "Full Name:", "Email:", "Phone:", "License Number:"};
        String[] values = {
                currentCustomer.getUserId(),
                currentCustomer.getUsername(),
                currentCustomer.getFullName(),
                currentCustomer.getEmail(),
                currentCustomer.getPhone(),
                currentCustomer.getLicenseNumber()
        };

        for (int i = 0; i < labels.length; i++) {
            JLabel label = UIConstants.createDarkBodyLabel(labels[i]);
            label.setFont(UIConstants.FONT_BODY_BOLD);
            gbc.gridx = 0;
            gbc.gridy = i + 1;
            infoCard.add(label, gbc);

            JLabel valueLabel = UIConstants.createDarkBodyLabel(values[i]);
            gbc.gridx = 1;
            infoCard.add(valueLabel, gbc);
        }

        panel.add(infoCard, BorderLayout.NORTH);

        // Stats Panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, UIConstants.SPACING_LG, UIConstants.SPACING_LG));
        statsPanel.setBackground(UIConstants.DARK_BG);
        statsPanel.setBorder(new EmptyBorder(UIConstants.SPACING_XL, 0, 0, 0));

        totalStatLabel = new JLabel("0", SwingConstants.CENTER);
        activeStatLabel = new JLabel("0", SwingConstants.CENTER);
        completedStatLabel = new JLabel("0", SwingConstants.CENTER);
        statsPanel.add(createStatCard("Total Bookings", totalStatLabel, UIConstants.PRIMARY));
        statsPanel.add(createStatCard("Active", activeStatLabel, UIConstants.SUCCESS));
        statsPanel.add(createStatCard("Completed", completedStatLabel, UIConstants.PURPLE));
        refreshProfileStats();

        panel.add(statsPanel, BorderLayout.CENTER);

        return panel;
    }

    /** Recounts the Profile tab's booking numbers; called whenever the bookings change. */
    private void refreshProfileStats() {
        if (totalStatLabel == null) return;
        List<Booking> bookings = bookingService.getBookingsVisibleTo(currentCustomer);
        long active = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.ACTIVE).count();
        long completed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        totalStatLabel.setText(String.valueOf(bookings.size()));
        activeStatLabel.setText(String.valueOf(active));
        completedStatLabel.setText(String.valueOf(completed));
    }

    private JPanel createRentalRecordsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_BG);

        JLabel label = new JLabel("<html><center>Rental Records are available via the <b>\"Rental Records\"</b> button in the header.<br>It provides search, filtering, and export capabilities.</center></html>", SwingConstants.CENTER);
        label.setFont(UIConstants.FONT_BODY);
        label.setForeground(UIConstants.DARK_TEXT_MUTED);
        label.setBorder(new EmptyBorder(UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL, UIConstants.SPACING_XXL));
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UIConstants.DARK_CARD);
        card.setBorder(new CompoundBorder(
                new LineBorder(UIConstants.DARK_BORDER, 1),
                new EmptyBorder(UIConstants.SPACING_XXL, UIConstants.SPACING_XL, UIConstants.SPACING_XXL, UIConstants.SPACING_XL)
        ));

        // Accent bar at top
        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(0, 4));
        card.add(accentBar, BorderLayout.NORTH);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        valueLabel.setForeground(accentColor);
        card.add(valueLabel, BorderLayout.CENTER);

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UIConstants.FONT_BODY);
        titleLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        titleLabel.setBorder(new EmptyBorder(UIConstants.SPACING_MD, 0, 0, 0));
        card.add(titleLabel, BorderLayout.SOUTH);

        return card;
    }

    private class BookingStatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof BookingStatus) {
                BookingStatus status = (BookingStatus) value;
                setText(status.name());
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UIConstants.FONT_BODY_BOLD);

                Color fgColor;
                switch (status) {
                    case CONFIRMED:
                    case ACTIVE:
                        fgColor = UIConstants.DARK_ACCENT_BLUE;
                        break;
                    case COMPLETED:
                        fgColor = UIConstants.DARK_TREND_UP;
                        break;
                    case PENDING:
                        fgColor = UIConstants.WARNING;
                        break;
                    default:
                        fgColor = UIConstants.DARK_TEXT_MUTED;
                }

                c.setBackground(isSelected ? UIConstants.DARK_ROW_SELECTED
                        : (row % 2 == 0 ? UIConstants.DARK_CARD : UIConstants.DARK_BG));
                c.setForeground(fgColor);
                setBorder(new EmptyBorder(6, 12, 6, 12));
            }
            return c;
        }
    }

    private void loadAvailableVehicles() {
        vehicleTableModel.setRowCount(0);
        List<Vehicle> vehicles = vehicleService.getInServiceVehicles().stream()
                .filter(v -> bookingService.isVehicleAvailableToday(v.getVehicleId()))
                .collect(java.util.stream.Collectors.toList());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Vehicle v : vehicles) {
            vehicleTableModel.addRow(new Object[]{
                    v.getVehicleId(),
                    v.getBrand(),
                    v.getModel(),
                    v.getYear(),
                    v.getType(),
                    String.format("\u20B1%,.2f", v.getPricePerDay()),
                    v.getLicensePlate(),
                    v.getVehicleCategory() + " - " + getVehicleDetails(v)
            });
        }
        updatePriceSliderRange(vehicles);
        applyVehicleFilters();
    }

    /** "4 doors, Gasoline": each vehicle type lists its own features (polymorphism). */
    private String getVehicleDetails(Vehicle v) {
        return String.join(", ", v.getFeatures());
    }

    private void loadBookingHistory() {
        refreshProfileStats();
        bookingTableModel.setRowCount(0);
        List<Booking> bookings = bookingService.getBookingsVisibleTo(currentCustomer);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Booking b : bookings) {
            String vehicleInfo = vehicleService.getVehicleById(b.getVehicleId())
                    .map(v -> v.getBrand() + " " + v.getModel())
                    .orElse("Unknown");

            bookingTableModel.addRow(new Object[]{
                    b.getBookingId(),
                    vehicleInfo,
                    b.getStartDate().format(formatter),
                    b.getEndDate().format(formatter),
                    b.getNumberOfDays(),
                    String.format("\u20B1%,.2f", b.getTotalCost()),
                    b.getStatus()
            });
        }
    }

    private void showRentDialog() {
        int row = vehicleTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a vehicle to rent", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String vehicleId = (String) vehicleTableModel.getValueAt(vehicleTable.convertRowIndexToModel(row), 0);
        vehicleService.getVehicleById(vehicleId).ifPresent(vehicle -> {
            RentDialog dialog = new RentDialog(this, vehicle, bookingService, currentCustomer.getUserId());
            dialog.setVisible(true);
            if (dialog.isBooked()) {
                loadAvailableVehicles();
                loadBookingHistory();
            }
        });
    }

    /** What cancelling now costs, shown before the customer confirms (fee within 24 hours of the start). */
    private String cancellationSummary(Booking booking) {
        java.math.BigDecimal paid = booking.getNetPaid();
        java.math.BigDecimal fee = bookingService.calculateCancellationFee(booking);
        java.math.BigDecimal refund = bookingService.previewCancellationRefund(booking);
        int ratePct = new java.math.BigDecimal(BookingService.CANCELLATION_FEE_RATE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder("<html>Cancel booking " + booking.getBookingId() + " (starts "
                + booking.getStartDate() + ")?<br><br>");
        if (paid.signum() <= 0) {
            sb.append("Nothing has been paid yet, so there is nothing to charge or refund.");
        } else if (fee.signum() > 0) {
            // a small table so the amounts line up
            sb.append("The rental starts in less than 24 hours, so a ").append(ratePct).append("% cancellation fee applies:")
              .append("<table cellpadding='2' style='margin-top:6px'>")
              .append(String.format("<tr><td>Paid</td><td align='right'>₱%,.2f</td></tr>", paid))
              .append(String.format("<tr><td>Cancellation fee (%d%%)</td><td align='right'>-₱%,.2f</td></tr>", ratePct, fee))
              .append(String.format("<tr><td><b>Refund</b></td><td align='right'><b>₱%,.2f</b></td></tr>", refund))
              .append("</table>");
        } else {
            sb.append(String.format("You're cancelling more than 24 hours ahead, so you'll get a full refund of ₱%,.2f.", refund));
        }
        return sb.append("</html>").toString();
    }

    private void cancelSelectedBooking() {
        int row = bookingTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a booking to cancel", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String bookingId = (String) bookingTableModel.getValueAt(row, 0);
        Booking booking = bookingService.getBookingById(bookingId).orElse(null);

        if (booking == null) {
            JOptionPane.showMessageDialog(this, "Booking not found", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            JOptionPane.showMessageDialog(this, "Booking is already cancelled", "Cannot Cancel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            JOptionPane.showMessageDialog(this, "Cannot cancel a completed booking", "Cannot Cancel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                cancellationSummary(booking),
                "Confirm Cancel",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                java.math.BigDecimal refund = paymentService.cancelBooking(bookingId);
                loadAvailableVehicles();
                loadBookingHistory();
                // the booking above is a copy from before cancelling; what is kept now is the fee
                java.math.BigDecimal fee = bookingService.getBookingById(bookingId).map(Booking::getNetPaid).orElse(java.math.BigDecimal.ZERO);
                String message = refund.signum() > 0
                        ? String.format("Booking cancelled. \u20B1%,.2f will be refunded to you.", refund)
                                + (fee.signum() > 0 ? String.format("%nA cancellation fee of \u20B1%,.2f was kept.", fee) : "")
                        : fee.signum() > 0 ? String.format("Booking cancelled. A cancellation fee of \u20B1%,.2f was kept.", fee)
                        : "Booking cancelled successfully";
                JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (BookingException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** Rebuilds this window after a Light/Dark switch, keeping its size, tab and vehicle filters. */
    private void reopenWithCurrentTheme() {
        CustomerDashboardFrame next = new CustomerDashboardFrame(currentCustomer, authService, vehicleService, bookingService, paymentService);
        next.setBounds(getBounds());
        next.setExtendedState(getExtendedState());
        next.searchField.setText(searchField.getText());
        next.typeFilter.setSelectedItem(typeFilter.getSelectedItem());
        next.maxPriceSlider.setValue(maxPriceSlider.getValue());
        next.tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex());
        // closes this window and any tool windows built with the old theme
        WindowSession.closeAllExcept(next);
        next.setVisible(true);
    }

    private void logout() {
        authService.logout();
        // close every window of this session (admin tools, records, ...) so none stays usable
        WindowSession.closeAllExcept(null);
        Theme.use(Theme.Mode.DARK);
        new LoginFrame(authService, vehicleService, bookingService, paymentService).setVisible(true);
    }

    private void showRentalRecords() {
        RentalRecordsDialog dialog = new RentalRecordsDialog(this, currentCustomer, bookingService, vehicleService, authService);
        dialog.setVisible(true);
    }
}