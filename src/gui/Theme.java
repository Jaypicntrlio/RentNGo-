package gui;

import service.ProjectPaths;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Light / Dark theme switching. Installs the FlatLaf look-and-feel (lib/flatlaf-*.jar) and swaps the
 * UIConstants palette that RentNGo's own components are painted with. The choice is saved in
 * data/settings.properties.
 *
 * FlatLaf is loaded by class name, so the app still runs (with the system look-and-feel) if the jar
 * is missing from the classpath.
 */
public final class Theme {

    public enum Mode { DARK, LIGHT }

    private static final String DARK_LAF = "com.formdev.flatlaf.FlatDarkLaf";
    private static final String LIGHT_LAF = "com.formdev.flatlaf.FlatLightLaf";
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String THEME_KEY = "theme";

    private static Mode current;
    private static boolean flatLafMissing;

    private Theme() {}

    public static Mode current() {
        return current == null ? Mode.DARK : current;
    }

    public static boolean isDark() {
        return current() == Mode.DARK;
    }

    /** The theme the user last picked for the dashboards (dark if never set). */
    public static Mode saved() {
        Properties props = new Properties();
        File file = settingsFile();
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // unreadable settings just mean the default theme
            }
        }
        return "light".equalsIgnoreCase(props.getProperty(THEME_KEY)) ? Mode.LIGHT : Mode.DARK;
    }

    /** Switches look-and-feel and palette. Windows built afterwards use the new theme. */
    public static void use(Mode mode) {
        if (mode == current) return;
        current = mode;
        UIConstants.applyPalette(mode == Mode.LIGHT);
        installLookAndFeel(mode);
    }

    /** Applies the saved dashboard theme. */
    public static void useSaved() {
        use(saved());
    }

    /** Flips between light and dark and remembers the choice. */
    public static Mode toggle() {
        Mode next = isDark() ? Mode.LIGHT : Mode.DARK;
        save(next);
        use(next);
        return next;
    }

    private static void save(Mode mode) {
        Properties props = new Properties();
        File file = settingsFile();
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException ignored) {
                // rewritten below
            }
        }
        props.setProperty(THEME_KEY, mode == Mode.LIGHT ? "light" : "dark");
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "RentNGo settings");
        } catch (IOException e) {
            System.err.println("Could not save the theme setting: " + e.getMessage());
        }
    }

    private static File settingsFile() {
        return new File(ProjectPaths.dir("data"), SETTINGS_FILE);
    }

    private static void installLookAndFeel(Mode mode) {
        if (!flatLafMissing) {
            try {
                UIManager.setLookAndFeel(mode == Mode.LIGHT ? LIGHT_LAF : DARK_LAF);
                applyAccents();
                return;
            } catch (ClassNotFoundException e) {
                flatLafMissing = true;
                System.err.println("FlatLaf not found on the classpath (lib/flatlaf-*.jar); using the system look.");
            } catch (Exception e) {
                System.err.println("Could not install FlatLaf: " + e.getMessage());
            }
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep whatever look-and-feel is active
        }
    }

    /** Blue accents and rounded corners to match the RentNGo palette. */
    private static void applyAccents() {
        Color accent = UIConstants.DARK_ACCENT_BLUE;
        UIManager.put("Component.focusColor", accent);
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Slider.thumbColor", accent);
        UIManager.put("Slider.trackValueColor", accent);
        UIManager.put("Slider.focusedColor", new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("TitlePane.background", UIConstants.DARK_SIDEBAR);
        UIManager.put("TitlePane.foreground", UIConstants.DARK_TEXT_PRIMARY);
    }

    /** Icon-only sun / moon button that toggles the theme and then runs {@code rebuild} to redraw the window. */
    public static JButton createToggleButton(Runnable rebuild) {
        boolean dark = isDark();
        Color bg = UIConstants.DARK_FIELD_BG;
        Color hover = UIConstants.DARK_BORDER;
        JButton button = UIConstants.createButton("", bg, hover,
                UIConstants.DARK_TEXT_PRIMARY, UIConstants.FONT_BUTTON);
        button.setIcon(new ModeIcon(!dark, 18, UIConstants.CREST_GOLD));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setBorder(new javax.swing.border.EmptyBorder(0, 0, 0, 0));
        button.setToolTipText(dark ? "Switch to light mode" : "Switch to dark mode");
        button.getAccessibleContext().setAccessibleName(dark ? "Switch to light mode" : "Switch to dark mode");
        Dimension size = new Dimension(UIConstants.BUTTON_HEIGHT_MD, UIConstants.BUTTON_HEIGHT_MD);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.addActionListener(e -> {
            toggle();
            rebuild.run();
        });
        return button;
    }

    /** Small sun (light mode) or crescent moon (dark mode) drawn in code. */
    static final class ModeIcon implements Icon {
        private final boolean moon;
        private final int size;
        private final Color color;

        ModeIcon(boolean moon, int size, Color color) {
            this.moon = moon;
            this.size = size;
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            float s = size;
            if (moon) {
                java.awt.geom.Area disc = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(x + s * 0.08f, y + s * 0.08f, s * 0.84f, s * 0.84f));
                disc.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(x + s * 0.34f, y - s * 0.06f, s * 0.78f, s * 0.78f)));
                g2.fill(disc);
            } else {
                float r = s * 0.22f, cx = x + s / 2f, cy = y + s / 2f;
                g2.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                g2.setStroke(new BasicStroke(Math.max(1.4f, s / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float r1 = s * 0.34f, r2 = s * 0.47f;
                    g2.draw(new java.awt.geom.Line2D.Float(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                            cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2));
                }
            }
            g2.dispose();
        }
    }
}
