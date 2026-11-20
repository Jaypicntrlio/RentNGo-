package gui;

import model.Admin;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.ReportService;
import service.VehicleService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * Main menu / dashboard shown after an admin logs in. The sidebar switches between the
 * Dashboard and Reports views; the other entries open their own window for that task.
 */
public class AdminDashboardFrame extends JFrame {
    static final String VIEW_DASHBOARD = "Dashboard";
    static final String VIEW_REPORTS = "Reports";
    static final String VIEW_AUDIT = "Audit Log";

    private final AuthService authService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final Admin currentAdmin;
    private DashboardPanel dashboardPanel;
    private ReportsPanel reportsPanel;
    private AuditLogPanel auditPanel;

    private String activeView;
    private final CardLayout viewCards = new CardLayout();
    private JPanel viewPanel;
    private JPanel sidebar;
    private JLabel titleLabel;

    public AdminDashboardFrame(AuthService authService, VehicleService vehicleService,
                                BookingService bookingService, PaymentService paymentService) {
        this(authService, vehicleService, bookingService, paymentService, VIEW_DASHBOARD);
    }

    AdminDashboardFrame(AuthService authService, VehicleService vehicleService,
                        BookingService bookingService, PaymentService paymentService, String startView) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        // only ever opened for a logged-in admin; anything else is a programming mistake, not a crash later
        if (!(authService.getCurrentUser() instanceof Admin)) {
            throw new IllegalStateException(WindowSession.SESSION_ENDED);
        }
        this.currentAdmin = (Admin) authService.getCurrentUser();
        initializeUI();
        showView(startView);
    }

    private void initializeUI() {
        UIConstants.styleFrame(this, "RentNGo - Main Menu", 1100, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.DARK_BG);

        sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(UIConstants.DARK_SIDEBAR);
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, UIConstants.DARK_BORDER));
        sidebar.setPreferredSize(new Dimension(220, 0));
        root.add(sidebar, BorderLayout.WEST);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setBackground(UIConstants.DARK_BG);
        centerWrap.add(createTopBar(), BorderLayout.NORTH);

        dashboardPanel = new DashboardPanel(vehicleService, authService, bookingService, paymentService);
        reportsPanel = new ReportsPanel(new ReportService(bookingService, vehicleService, paymentService));
        viewPanel = new JPanel(viewCards);
        viewPanel.setBackground(UIConstants.DARK_BG);
        viewPanel.add(dashboardPanel, VIEW_DASHBOARD);
        viewPanel.add(reportsPanel, VIEW_REPORTS);
        auditPanel = new AuditLogPanel(bookingService.getAuditLog(), authService);
        viewPanel.add(auditPanel, VIEW_AUDIT);
        centerWrap.add(viewPanel, BorderLayout.CENTER);
        root.add(centerWrap, BorderLayout.CENTER);

        add(root);

        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {
                refreshActiveView();
            }
        });
    }

    /** Shows the Dashboard, Reports or Audit Log view and highlights it in the sidebar. */
    private void showView(String view) {
        activeView = VIEW_REPORTS.equals(view) || VIEW_AUDIT.equals(view) ? view : VIEW_DASHBOARD;
        viewCards.show(viewPanel, activeView);
        titleLabel.setText(VIEW_REPORTS.equals(activeView) ? "Reports"
                : VIEW_AUDIT.equals(activeView) ? "Audit Log" : "Sales Overview");
        rebuildSidebar();
        refreshActiveView();
    }

    private void refreshActiveView() {
        if (VIEW_REPORTS.equals(activeView)) {
            reportsPanel.refresh();
        } else if (VIEW_AUDIT.equals(activeView)) {
            auditPanel.refresh();
        } else {
            dashboardPanel.refresh();
        }
    }

    /** Sidebar entry for one of the dashboard's own views. */
    private JButton viewButton(String icon, String text, String view) {
        boolean active = view.equals(activeView);
        return navButton(icon, text, active, active ? null : e -> showView(view));
    }

    private void rebuildSidebar() {
        sidebar.removeAll();

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MD, UIConstants.SPACING_LG));
        brand.setBackground(UIConstants.DARK_SIDEBAR);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.add(UIConstants.createBrandPanel(38, 21, UIConstants.DARK_TEXT_PRIMARY));
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, brand.getPreferredSize().height));
        sidebar.add(brand);

        sidebar.add(viewButton("🏠", "Dashboard", VIEW_DASHBOARD));
        sidebar.add(viewButton("📊", "Reports", VIEW_REPORTS));
        sidebar.add(navButton("🚗", "Vehicles", false, e -> showManageVehicles()));
        sidebar.add(navButton("👥", "Customers", false, e -> showManageCustomers()));
        sidebar.add(navButton("🔑", "Rent Vehicle", false, e -> showRentVehicle()));
        sidebar.add(navButton("🔄", "Return Vehicle", false, e -> showReturnVehicle()));
        sidebar.add(navButton("💰", "Payments", false, e -> showPayments()));
        sidebar.add(navButton("🏷", "Promo Codes", false, e -> showPromoCodes()));
        sidebar.add(navButton("⭐", "Reviews", false, e -> showReviews()));
        sidebar.add(navButton("📋", "Rental Records", false, e -> showRentalRecords()));
        sidebar.add(viewButton("🧾", "Audit Log", VIEW_AUDIT));
        if (WindowSession.getBackupService() != null) {
            sidebar.add(navButton("💾", "Backup", false, e -> backupNow()));
            sidebar.add(navButton("♻", "Restore", false, e -> showRestore()));
        }

        sidebar.add(Box.createVerticalGlue());
        JButton passwordButton = UIConstants.createSidebarButton(new UIConstants.KeyIcon(16, UIConstants.DARK_TEXT_MUTED), "Change Password", false, true);
        passwordButton.addActionListener(e -> new ChangePasswordDialog(this, authService, currentAdmin, false).setVisible(true));
        sidebar.add(passwordButton);
        JButton logoutButton = UIConstants.createSidebarButton(new UIConstants.LogoutIcon(16, UIConstants.DARK_TEXT_MUTED), "Logout", false, true);
        logoutButton.addActionListener(e -> logout());
        sidebar.add(logoutButton);
        sidebar.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        sidebar.revalidate();
        sidebar.repaint();
    }

    private JButton navButton(String icon, String text, boolean active, java.awt.event.ActionListener listener) {
        JButton button = UIConstants.createSidebarButton(icon, text, active, true);
        if (listener != null) {
            button.addActionListener(listener);
        }
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return button;
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIConstants.DARK_BG);
        panel.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_LG, UIConstants.SPACING_XL));

        titleLabel = new JLabel("Sales Overview");
        titleLabel.setFont(UIConstants.FONT_HEADING);
        titleLabel.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        panel.add(titleLabel, BorderLayout.WEST);

        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MD, 0));
        userPanel.setBackground(UIConstants.DARK_BG);

        userPanel.add(Theme.createToggleButton(this::reopenWithCurrentTheme));
        userPanel.add(Box.createRigidArea(new Dimension(UIConstants.SPACING_SM, 0)));

        // admins are shown with the RentNGo crest, not a personal photo
        JLabel badge = new JLabel(new BrandAvatar(38));
        userPanel.add(badge);

        JPanel nameBox = new JPanel();
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        nameBox.setBackground(UIConstants.DARK_BG);
        String fullName = displayName(currentAdmin);
        // the top bar shows the business, not the person: "RentNGo / Admin" for every admin account
        JLabel name = new JLabel(BRAND_NAME);
        name.setFont(UIConstants.FONT_BODY_BOLD);
        name.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        // every admin account is shown simply as "Admin" (the saved admin level is kept, just not shown)
        JLabel role = new JLabel(ADMIN_LABEL);
        role.setFont(UIConstants.FONT_SMALL);
        role.setForeground(UIConstants.CREST_GOLD);
        nameBox.add(name);
        nameBox.add(role);
        userPanel.add(nameBox);
        String tip = "Logged in as " + fullName + " (" + currentAdmin.getUsername() + "), RentNGo admin";
        badge.setToolTipText(tip);
        name.setToolTipText(tip);

        panel.add(userPanel, BorderLayout.EAST);
        return panel;
    }

    /** The role shown under the name for every admin account. */
    static final String ADMIN_LABEL = "Admin";
    /** The name shown in the top bar for every admin account. */
    static final String BRAND_NAME = "RentNGo";

    /** The admin's full name, or their username when no name was saved (shown in the tooltip). */
    static String displayName(Admin admin) {
        String fullName = admin.getFullName() == null ? "" : admin.getFullName().trim();
        return fullName.isEmpty() ? admin.getUsername() : fullName;
    }

    /** The RentNGo crest in a round badge with a gold ring, used as the admin's profile picture. */
    static final class BrandAvatar implements Icon {
        private final int size;

        BrandAvatar(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            float ring = Math.max(1.5f, size * 0.04f);
            g2.setColor(UIConstants.DARK_CARD);
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.setColor(UIConstants.CREST_GOLD);
            g2.setStroke(new BasicStroke(ring));
            g2.drawOval(Math.round(ring / 2), Math.round(ring / 2), Math.round(size - 1 - ring), Math.round(size - 1 - ring));
            UIConstants.CrestIcon crest = new UIConstants.CrestIcon(Math.round(size * 0.62f));
            crest.paintIcon(c, g2, (size - crest.getIconWidth()) / 2, (size - crest.getIconHeight()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private void showManageVehicles() {
        new ManageVehiclesFrame(this, authService, vehicleService, bookingService).setVisible(true);
    }

    private void showManageCustomers() {
        new ManageCustomersFrame(this, authService, bookingService).setVisible(true);
    }

    private void showRentVehicle() {
        new RentVehicleFrame(this, authService, vehicleService, bookingService).setVisible(true);
    }

    private void showReturnVehicle() {
        new ReturnVehicleFrame(this, authService, vehicleService, bookingService, paymentService).setVisible(true);
    }

    private void showPayments() {
        new PaymentFrame(this, authService, vehicleService, bookingService, paymentService).setVisible(true);
    }

    private void showPromoCodes() {
        new PromoCodesFrame(this, authService, bookingService).setVisible(true);
    }

    private void showReviews() {
        new ReviewsFrame(this, authService, bookingService.getReviewService(), vehicleService).setVisible(true);
    }

    private void showRentalRecords() {
        RentalRecordsDialog dialog = new RentalRecordsDialog(this, currentAdmin, bookingService, vehicleService, authService);
        dialog.setVisible(true);
    }

    /** Saves the whole data folder to backups/rentngo-YYYY-MM-DD-HHmm.zip. */
    private void backupNow() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        try {
            java.io.File zip = WindowSession.getBackupService().backup();
            JOptionPane.showMessageDialog(this, "Backup saved:\n" + zip.getAbsolutePath()
                            + "\n\nUse Restore to bring this data back later.",
                    "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (model.exceptions.ValidationException | java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Backup Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRestore() {
        if (!WindowSession.requireAdmin(this, authService)) return;
        new RestoreDialog(this, authService, WindowSession.getBackupService()).setVisible(true);
    }

    /** Rebuilds this window after a Light/Dark switch, keeping its size, position and view. */
    private void reopenWithCurrentTheme() {
        AdminDashboardFrame next = new AdminDashboardFrame(authService, vehicleService, bookingService, paymentService, activeView);
        next.setBounds(getBounds());
        next.setExtendedState(getExtendedState());
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
}
