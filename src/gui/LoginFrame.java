package gui;

import model.User;
import service.AuthService;
import service.BookingService;
import service.PaymentService;
import service.VehicleService;
import model.exceptions.AuthenticationException;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

public class LoginFrame extends JFrame {
    private AuthService authService;
    private VehicleService vehicleService;
    private BookingService bookingService;
    private PaymentService paymentService;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;

    private static final int WINDOW_WIDTH = 1120;
    private static final int WINDOW_HEIGHT = 600;
    private static final int FORM_PANEL_WIDTH = 400;
    private static final int FORM_CONTENT_WIDTH = 300;
    private static final Color FORM_TOP = new Color(40, 40, 42);
    private static final Color FORM_BOTTOM = new Color(22, 22, 23);

    public LoginFrame(AuthService authService, VehicleService vehicleService, BookingService bookingService, PaymentService paymentService) {
        this.authService = authService;
        this.vehicleService = vehicleService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        initializeUI();
    }

    private void initializeUI() {
        setTitle("RentNGo - Login");
        UIConstants.applyAppIcon(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(820, 520));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIConstants.DARK_BG);
        setContentPane(root);

        root.add(createFormPanel(), BorderLayout.WEST);
        root.add(new ArtPanel(), BorderLayout.CENTER);

        getRootPane().setDefaultButton(loginButton);
    }

    private JPanel createFormPanel() {
        // Outer panel centers the form column vertically and horizontally.
        // Neutral charcoal gradient that matches the studio tones of the login photo.
        JPanel outer = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, FORM_TOP, 0, getHeight(), FORM_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        outer.setBackground(FORM_BOTTOM);
        outer.setPreferredSize(new Dimension(FORM_PANEL_WIDTH, 0));

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        addRow(form, UIConstants.createBrandPanel(64, 34, true));
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_XL)));

        JLabel welcome = UIConstants.createDarkSubheadingLabel("Sign in to your account");
        addRow(form, welcome);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        addRow(form, UIConstants.createDarkSecondaryLabel("Username"));
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_XS)));
        usernameField = UIConstants.createDarkTextField(20);
        addRow(form, usernameField, UIConstants.INPUT_HEIGHT + 4);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        addRow(form, UIConstants.createDarkSecondaryLabel("Password"));
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_XS)));
        passwordField = UIConstants.createDarkPasswordField(20);
        addRow(form, passwordField, UIConstants.INPUT_HEIGHT + 4);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_XL)));

        loginButton = UIConstants.createButton("Login", UIConstants.DARK_ACCENT_BLUE,
                UIConstants.DARK_ACCENT_BLUE.darker(), Color.WHITE, UIConstants.FONT_BUTTON_LARGE);
        loginButton.addActionListener(this::handleLogin);
        addRow(form, loginButton, UIConstants.BUTTON_HEIGHT_LG);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_SM)));

        JButton exitButton = UIConstants.createButton("Exit", UIConstants.DARK_BORDER,
                UIConstants.DARK_BUTTON_NEUTRAL, UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        exitButton.addActionListener(e -> System.exit(0));
        addRow(form, exitButton, UIConstants.BUTTON_HEIGHT_MD);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_MD)));

        JPanel signUpRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        signUpRow.setOpaque(false);
        JLabel noAccount = UIConstants.createDarkSecondaryLabel("Don't have an account?");
        JButton createLink = new JButton("Create one");
        createLink.setFont(UIConstants.FONT_BODY_BOLD);
        createLink.setForeground(UIConstants.DARK_ACCENT_BLUE);
        createLink.setContentAreaFilled(false);
        createLink.setBorderPainted(false);
        createLink.setFocusPainted(false);
        createLink.setBorder(new EmptyBorder(0, 0, 0, 0));
        createLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createLink.addActionListener(this::handleRegister);
        signUpRow.add(noAccount);
        signUpRow.add(createLink);
        addRow(form, signUpRow, 24);
        form.add(Box.createRigidArea(new Dimension(0, UIConstants.SPACING_LG)));

        JLabel demoLabel = new JLabel("<html><div style='text-align:center'>Demo accounts<br>"
                + "Admin: admin / admin123 &nbsp;&middot;&nbsp; Customer: john / john123</div></html>",
                SwingConstants.CENTER);
        demoLabel.setFont(UIConstants.FONT_SMALL);
        demoLabel.setForeground(UIConstants.DARK_TEXT_MUTED);
        addRow(form, demoLabel);

        outer.add(form);
        return outer;
    }

    /** Adds a component to the form column at the fixed form width, using its natural height. */
    private void addRow(JPanel form, JComponent comp) {
        addRow(form, comp, comp.getPreferredSize().height);
    }

    private void addRow(JPanel form, JComponent comp, int height) {
        Dimension size = new Dimension(FORM_CONTENT_WIDTH, height);
        comp.setPreferredSize(size);
        comp.setMinimumSize(size);
        comp.setMaximumSize(size);
        comp.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (comp instanceof JLabel) {
            JLabel label = (JLabel) comp;
            if (label.getHorizontalAlignment() == SwingConstants.LEADING) {
                label.setHorizontalAlignment(SwingConstants.LEFT);
            }
        }
        form.add(comp);
    }

    private void handleLogin(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password");
            return;
        }
        try {
            User user = authService.login(username, password);
            if (user.isMustChangePassword()) {
                // started with a temporary password: no dashboard until they choose their own
                ChangePasswordDialog change = new ChangePasswordDialog(this, authService, user, true);
                change.setVisible(true);
                if (!change.isChanged()) {
                    authService.logout();
                    passwordField.setText("");
                    return;
                }
            }
            dispose();
            Theme.useSaved(); // the login screen is always dark; dashboards use the user's Light/Dark choice
            if ("ADMIN".equals(user.getRole())) {
                new AdminDashboardFrame(authService, vehicleService, bookingService, paymentService).setVisible(true);
            } else {
                new CustomerDashboardFrame((model.Customer) user, authService, vehicleService, bookingService, paymentService).setVisible(true);
            }
        } catch (AuthenticationException ex) {
            showError(ex.getMessage());
            passwordField.setText("");
            passwordField.requestFocus();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Login Failed", JOptionPane.ERROR_MESSAGE);
    }

    private void handleRegister(ActionEvent e) {
        RegisterDialog dialog = new RegisterDialog(this, authService);
        dialog.setVisible(true);
        if (dialog.getCreatedUser() != null) {
            usernameField.setText(dialog.getCreatedUser().getUsername());
            passwordField.setText("");
            passwordField.requestFocus();
        }
    }

    /**
     * Right-hand art: the login photo bundled with the app (src/resources/login-bg.jpg, loaded from
     * the classpath so it works on any computer). If it is missing, a plain charcoal gradient in
     * the same tones is shown instead.
     */
    class ArtPanel extends JPanel {
        private static final String BACKGROUND_RESOURCE = "/resources/login-bg.jpg";

        private final BufferedImage heroPhoto;
        private BufferedImage scaled;

        ArtPanel() {
            setBackground(UIConstants.DARK_BG);
            heroPhoto = loadBackground();
        }

        private BufferedImage loadBackground() {
            java.net.URL url = getClass().getResource(BACKGROUND_RESOURCE);
            if (url == null) return null;
            try {
                return javax.imageio.ImageIO.read(url);
            } catch (java.io.IOException e) {
                return null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            if (heroPhoto != null) {
                paintHeroPhoto(g2, w, h);
            } else {
                paintGradient(g2, w, h);
            }

            // Fade into the form panel on the left, so the two halves read as one scene.
            Color edge = FORM_BOTTOM;
            g2.setPaint(new GradientPaint(0, 0, new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 230),
                    w * 0.28f, 0, new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 0)));
            g2.fillRect(0, 0, w, h);

            // Darken the bottom under the welcome text so it stays readable.
            g2.setPaint(new LinearGradientPaint(0, h * 0.55f, 0, h,
                    new float[]{0f, 0.55f, 1f},
                    new Color[]{new Color(18, 18, 19, 0), new Color(18, 18, 19, 170), new Color(18, 18, 19, 245)}));
            g2.fillRect(0, 0, w, h);
            paintWelcome(g2, 44, h - 64);
            g2.dispose();
        }

        /** Full-bleed photo, cropped to fill the panel. */
        private void paintHeroPhoto(Graphics2D g2, int w, int h) {
            double scale = Math.max(w / (double) heroPhoto.getWidth(), h / (double) heroPhoto.getHeight());
            int iw = (int) Math.ceil(heroPhoto.getWidth() * scale);
            int ih = (int) Math.ceil(heroPhoto.getHeight() * scale);
            if (scaled == null || scaled.getWidth() != iw || scaled.getHeight() != ih) {
                scaled = SilhouettePanel.downscale(heroPhoto, iw, ih);
            }
            g2.drawImage(scaled, (w - iw) / 2, (h - ih) / 2, null);
        }

        /** Fallback: neutral studio-style gradient with a soft light from the top right. */
        private void paintGradient(Graphics2D g2, int w, int h) {
            g2.setPaint(new GradientPaint(0, 0, new Color(58, 58, 61), 0, h, new Color(24, 24, 26)));
            g2.fillRect(0, 0, w, h);
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(w * 0.75f, h * 0.05f), Math.max(w, h) * 0.7f,
                    new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 45), new Color(255, 255, 255, 0)}));
            g2.fillRect(0, 0, w, h);
        }

        private void paintWelcome(Graphics2D g2, int x, int baseline) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
            g2.setColor(UIConstants.DARK_TEXT_PRIMARY);
            g2.drawString("Welcome back", x, baseline);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.setColor(UIConstants.DARK_TEXT_MUTED);
            g2.drawString("PICK UP THE KEYS. GET MOVING.", x, baseline + 26);
        }
    }
}
