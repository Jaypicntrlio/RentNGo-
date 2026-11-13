package gui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * Centralized UI constants and helper methods for consistent styling across the application.
 */
public final class UIConstants {

    // ===== Color Palette =====
    public static final Color PRIMARY = new Color(41, 128, 185);       // Blue
    public static final Color PRIMARY_DARK = new Color(31, 97, 141);
    public static final Color PRIMARY_LIGHT = new Color(52, 152, 219);
    public static final Color SUCCESS = new Color(46, 204, 113);        // Green
    public static final Color SUCCESS_DARK = new Color(39, 174, 96);
    public static final Color WARNING = new Color(241, 196, 15);        // Yellow/Amber
    public static final Color DANGER = new Color(231, 76, 60);          // Red
    public static final Color DANGER_DARK = new Color(192, 57, 43);
    public static final Color PURPLE = new Color(155, 89, 182);
    public static final Color PURPLE_DARK = new Color(142, 68, 173);
    public static final Color GRAY = new Color(149, 165, 166);
    public static final Color GRAY_DARK = new Color(127, 140, 141);

    public static final Color BG_PRIMARY = new Color(245, 248, 250);    // Light gray-blue
    public static final Color BG_WHITE = Color.WHITE;
    public static final Color BG_CARD = new Color(250, 251, 252);
    public static final Color BG_HOVER = new Color(236, 240, 241);
    public static final Color BORDER_LIGHT = new Color(189, 195, 199);
    public static final Color BORDER_MEDIUM = new Color(149, 165, 166);
    public static final Color TEXT_PRIMARY = new Color(44, 62, 80);
    public static final Color TEXT_SECONDARY = new Color(127, 140, 141);
    public static final Color TEXT_MUTED = new Color(149, 165, 166);
    public static final Color TEXT_ON_PRIMARY = Color.WHITE;
    public static final Color TEXT_ON_DARK = Color.WHITE;

    // Status colors
    public static final Color STATUS_PENDING_BG = new Color(255, 243, 205);
    public static final Color STATUS_PENDING_FG = new Color(241, 196, 15);
    public static final Color STATUS_CONFIRMED_BG = new Color(215, 235, 255);
    public static final Color STATUS_CONFIRMED_FG = new Color(41, 128, 185);
    public static final Color STATUS_ACTIVE_BG = new Color(215, 235, 255);
    public static final Color STATUS_ACTIVE_FG = new Color(41, 128, 185);
    public static final Color STATUS_COMPLETED_BG = new Color(212, 237, 218);
    public static final Color STATUS_COMPLETED_FG = new Color(39, 174, 96);
    public static final Color STATUS_CANCELLED_BG = new Color(235, 237, 240);
    public static final Color STATUS_CANCELLED_FG = new Color(149, 165, 166);

    // ===== Theme Palette (dashboard / sidebar) =====
    // The DARK_* names are historical: they hold the colors of the current theme and are swapped by
    // applyPalette() when Theme switches between dark and light. Windows read them when they are built.
    public static Color DARK_BG;
    public static Color DARK_SIDEBAR;
    public static Color DARK_CARD;
    public static Color DARK_BORDER;
    public static Color DARK_TEXT_PRIMARY;
    public static Color DARK_TEXT_MUTED;
    public static Color DARK_ACCENT_BLUE;
    public static Color DARK_TREND_UP;
    public static Color DARK_TREND_DOWN;
    public static Color DARK_FIELD_BG;
    public static Color DARK_ROW_SELECTED;
    /** "Rent" and "Go" in the wordmark, and other ivory text on the brand header. */
    public static Color BRAND_WORD;
    public static Border BORDER_INPUT_DARK;
    public static Border BORDER_INPUT_DARK_FOCUS;
    /** Neutral button (Close, Cancel, Browse) on the current theme. */
    public static Color DARK_BUTTON_NEUTRAL;

    // ===== Typography =====
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBHEADING = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_BUTTON_LARGE = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_TABLE_HEADER = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_TABLE_CELL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    // ===== Spacing =====
    public static final int SPACING_XS = 4;
    public static final int SPACING_SM = 8;
    public static final int SPACING_MD = 12;
    public static final int SPACING_LG = 16;
    public static final int SPACING_XL = 24;
    public static final int SPACING_XXL = 32;

    // ===== Dimensions =====
    public static final int INPUT_HEIGHT = 36;
    public static final int BUTTON_HEIGHT_SM = 32;
    public static final int BUTTON_HEIGHT_MD = 40;
    public static final int BUTTON_HEIGHT_LG = 44;
    public static final int TABLE_ROW_HEIGHT = 36;
    public static final int TABLE_HEADER_HEIGHT = 40;
    public static final int DIALOG_WIDTH = 500;
    public static final int DIALOG_WIDTH_LARGE = 600;
    public static final int CORNER_RADIUS = 6;
    public static final int CORNER_RADIUS_LG = 10;

    // ===== Borders =====
    public static final Border BORDER_INPUT = new CompoundBorder(
            new LineBorder(BORDER_LIGHT, 1),
            new EmptyBorder(8, 12, 8, 12)
    );
    public static final Border BORDER_INPUT_FOCUS = new CompoundBorder(
            new LineBorder(PRIMARY, 2),
            new EmptyBorder(7, 11, 7, 11)
    );
    public static final Border BORDER_CARD = new CompoundBorder(
            new LineBorder(BORDER_LIGHT, 1),
            new EmptyBorder(SPACING_LG, SPACING_LG, SPACING_LG, SPACING_LG)
    );
    public static final Border BORDER_TABLE = new LineBorder(BORDER_LIGHT, 1);
    public static final Border BORDER_TABLE_HEADER = new CompoundBorder(
            new MatteBorder(0, 0, 2, 0, BORDER_MEDIUM),
            new EmptyBorder(8, 12, 8, 12)
    );

    private UIConstants() {}

    // ===== Component Factories =====

    public static JLabel createLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static JLabel createTitleLabel(String text) {
        return createLabel(text, FONT_TITLE, PRIMARY);
    }

    public static JLabel createHeadingLabel(String text) {
        return createLabel(text, FONT_HEADING, TEXT_PRIMARY);
    }

    public static JLabel createSubheadingLabel(String text) {
        return createLabel(text, FONT_SUBHEADING, TEXT_PRIMARY);
    }

    public static JLabel createBodyLabel(String text) {
        return createLabel(text, FONT_BODY, TEXT_PRIMARY);
    }

    public static JLabel createSecondaryLabel(String text) {
        return createLabel(text, FONT_BODY, TEXT_SECONDARY);
    }

    public static JLabel createMutedLabel(String text) {
        return createLabel(text, FONT_SMALL, TEXT_MUTED);
    }

    public static JTextField createTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(FONT_BODY);
        field.setBorder(BORDER_INPUT);
        field.setBackground(BG_WHITE);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(PRIMARY);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_FOCUS);
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT);
            }
        });
        return field;
    }

    public static JPasswordField createPasswordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(FONT_BODY);
        field.setBorder(BORDER_INPUT);
        field.setBackground(BG_WHITE);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(PRIMARY);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_FOCUS);
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT);
            }
        });
        return field;
    }

    public static JComboBox<String> createComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(FONT_BODY);
        combo.setBackground(BG_WHITE);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(BORDER_INPUT);
        combo.setPreferredSize(new Dimension(200, INPUT_HEIGHT));
        ((JComponent) combo.getEditor().getEditorComponent()).setBorder(new EmptyBorder(0, 0, 0, 0));
        return combo;
    }

    public static JSpinner createSpinner(SpinnerNumberModel model) {
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(FONT_BODY);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBorder(BORDER_INPUT);
            tf.setBackground(BG_WHITE);
            tf.setForeground(TEXT_PRIMARY);
            tf.setFont(FONT_BODY);
        }
        spinner.setPreferredSize(new Dimension(120, INPUT_HEIGHT));
        return spinner;
    }

    public static JButton createPrimaryButton(String text) {
        return createButton(text, PRIMARY, PRIMARY_DARK, TEXT_ON_PRIMARY, FONT_BUTTON);
    }

    public static JButton createSecondaryButton(String text) {
        return createButton(text, GRAY, GRAY_DARK, TEXT_ON_PRIMARY, FONT_BUTTON);
    }

    public static JButton createSuccessButton(String text) {
        return createButton(text, SUCCESS, SUCCESS_DARK, TEXT_ON_PRIMARY, FONT_BUTTON);
    }

    public static JButton createDangerButton(String text) {
        return createButton(text, DANGER, DANGER_DARK, TEXT_ON_PRIMARY, FONT_BUTTON);
    }

    public static JButton createWarningButton(String text) {
        return createButton(text, WARNING, new Color(211, 172, 13), TEXT_PRIMARY, FONT_BUTTON);
    }

    public static JButton createPurpleButton(String text) {
        return createButton(text, PURPLE, PURPLE_DARK, TEXT_ON_PRIMARY, FONT_BUTTON);
    }

    public static JButton createButton(String text, Color bg, Color hoverBg, Color fg, Font font) {
        JButton button = new JButton(text) {
            private boolean hover = false;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = hover ? hoverBg : bg;
                if (!isEnabled()) {
                    // Faded fill, but the label keeps its own color so it stays readable
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                }
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                if (isEnabled()) {
                    g2.dispose();
                    super.paintComponent(g);
                    return;
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Insets in = getInsets();
                Rectangle view = new Rectangle(in.left, in.top, getWidth() - in.left - in.right, getHeight() - in.top - in.bottom);
                Rectangle iconR = new Rectangle(), textR = new Rectangle();
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String shown = SwingUtilities.layoutCompoundLabel(this, fm, getText(), getIcon(),
                        getVerticalAlignment(), getHorizontalAlignment(), getVerticalTextPosition(),
                        getHorizontalTextPosition(), view, iconR, textR, getIconTextGap());
                if (getIcon() != null) {
                    getIcon().paintIcon(this, g2, iconR.x, iconR.y);
                }
                g2.setColor(getForeground());
                g2.drawString(shown, textR.x, textR.y + fm.getAscent());
                g2.dispose();
            }
            @Override protected void paintBorder(Graphics g) {
                // No border
            }
        };
        button.setFont(font);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(SPACING_SM, SPACING_XL, SPACING_SM, SPACING_XL));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                button.repaint();
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                button.repaint();
            }
        });
        return button;
    }

    public static JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BG_WHITE);
        panel.setBorder(BORDER_CARD);
        return panel;
    }

    public static JPanel createHeaderPanel(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        panel.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_LIGHT),
                new EmptyBorder(SPACING_LG, SPACING_XL, SPACING_LG, SPACING_XL)
        ));

        JLabel titleLabel = createHeadingLabel(title);
        panel.add(titleLabel, BorderLayout.WEST);

        if (subtitle != null && !subtitle.isEmpty()) {
            JLabel subLabel = createSecondaryLabel(subtitle);
            panel.add(subLabel, BorderLayout.EAST);
        }
        return panel;
    }

    public static JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? BG_WHITE : BG_CARD);
                } else {
                    c.setBackground(new Color(200, 220, 240));
                }
                return c;
            }
        };
        table.setFont(FONT_TABLE_CELL);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setIntercellSpacing(new Dimension(SPACING_MD, SPACING_XS));
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);

        table.getTableHeader().setFont(FONT_TABLE_HEADER);
        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setForeground(TEXT_PRIMARY);
        table.getTableHeader().setBorder(BORDER_TABLE_HEADER);
        table.getTableHeader().setPreferredSize(new Dimension(0, TABLE_HEADER_HEIGHT));
        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    public static JScrollPane createScrollPane(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BORDER_TABLE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.setBackground(BG_WHITE);
        scroll.getViewport().setBackground(BG_WHITE);
        return scroll;
    }

    // ===== Theme palette =====

    static {
        applyPalette(false);
    }

    /** Fills the DARK_* palette with the dark colors, or with the light ones when {@code light} is true. */
    static void applyPalette(boolean light) {
        if (light) {
            DARK_BG = new Color(245, 244, 241);
            DARK_SIDEBAR = new Color(236, 234, 229);
            DARK_CARD = new Color(255, 255, 255);
            DARK_BORDER = new Color(221, 218, 211);
            DARK_TEXT_PRIMARY = new Color(28, 28, 32);
            DARK_TEXT_MUTED = new Color(104, 104, 112);
            DARK_ACCENT_BLUE = new Color(37, 99, 235);
            DARK_TREND_UP = new Color(22, 140, 70);
            DARK_TREND_DOWN = new Color(205, 45, 45);
            DARK_FIELD_BG = new Color(250, 249, 247);
            DARK_ROW_SELECTED = new Color(214, 227, 251);
            BRAND_WORD = new Color(28, 28, 32);
            DARK_BUTTON_NEUTRAL = new Color(210, 206, 198);
        } else {
            DARK_BG = new Color(16, 16, 18);
            DARK_SIDEBAR = new Color(20, 20, 23);
            DARK_CARD = new Color(26, 26, 30);
            DARK_BORDER = new Color(42, 42, 48);
            DARK_TEXT_PRIMARY = new Color(245, 245, 247);
            DARK_TEXT_MUTED = new Color(150, 150, 158);
            DARK_ACCENT_BLUE = new Color(79, 142, 247);
            DARK_TREND_UP = new Color(34, 197, 94);
            DARK_TREND_DOWN = new Color(239, 68, 68);
            DARK_FIELD_BG = new Color(30, 30, 34);
            DARK_ROW_SELECTED = new Color(40, 56, 86);
            BRAND_WORD = new Color(243, 239, 230); // CREST_IVORY (not initialised yet when this first runs)
            DARK_BUTTON_NEUTRAL = new Color(60, 60, 68);
        }
        BORDER_INPUT_DARK = new CompoundBorder(new LineBorder(DARK_BORDER, 1), new EmptyBorder(8, 12, 8, 12));
        BORDER_INPUT_DARK_FOCUS = new CompoundBorder(new LineBorder(DARK_ACCENT_BLUE, 2), new EmptyBorder(7, 11, 7, 11));
    }

    // ===== Theme component factories (dashboards and the windows opened from them) =====

    public static JLabel createDarkHeadingLabel(String text) {
        return createLabel(text, FONT_HEADING, DARK_TEXT_PRIMARY);
    }

    public static JLabel createDarkSubheadingLabel(String text) {
        return createLabel(text, FONT_SUBHEADING, DARK_TEXT_PRIMARY);
    }

    public static JLabel createDarkBodyLabel(String text) {
        return createLabel(text, FONT_BODY, DARK_TEXT_PRIMARY);
    }

    public static JLabel createDarkSecondaryLabel(String text) {
        return createLabel(text, FONT_BODY, DARK_TEXT_MUTED);
    }

    public static JLabel createDarkMutedLabel(String text) {
        return createLabel(text, FONT_SMALL, DARK_TEXT_MUTED);
    }

    public static JTextField createDarkTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(FONT_BODY);
        field.setBorder(BORDER_INPUT_DARK);
        field.setBackground(DARK_FIELD_BG);
        field.setForeground(DARK_TEXT_PRIMARY);
        field.setCaretColor(DARK_ACCENT_BLUE);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_DARK_FOCUS);
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_DARK);
            }
        });
        return field;
    }

    public static JPasswordField createDarkPasswordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(FONT_BODY);
        field.setBorder(BORDER_INPUT_DARK);
        field.setBackground(DARK_FIELD_BG);
        field.setForeground(DARK_TEXT_PRIMARY);
        field.setCaretColor(DARK_ACCENT_BLUE);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_DARK_FOCUS);
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BORDER_INPUT_DARK);
            }
        });
        return field;
    }

    public static JComboBox<String> createDarkComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        styleDarkCombo(combo);
        combo.setPreferredSize(new Dimension(200, INPUT_HEIGHT));
        return combo;
    }

    /**
     * Dark styling for any combo box. The Windows look-and-feel ignores combo background colors,
     * so this swaps in a Basic UI that paints the box, arrow, and popup list itself.
     */
    public static void styleDarkCombo(JComboBox<?> combo) {
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(DARK_FIELD_BG);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(DARK_TEXT_MUTED);
                        g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        java.awt.geom.Path2D.Float chevron = new java.awt.geom.Path2D.Float();
                        chevron.moveTo(cx - 4, cy - 2);
                        chevron.lineTo(cx, cy + 2);
                        chevron.lineTo(cx + 4, cy - 2);
                        g2.draw(chevron);
                        g2.dispose();
                    }
                };
                arrow.setBorder(new EmptyBorder(0, 0, 0, 0));
                arrow.setContentAreaFilled(false);
                arrow.setFocusPainted(false);
                arrow.setPreferredSize(new Dimension(28, 0));
                return arrow;
            }

            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(DARK_FIELD_BG);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
                javax.swing.plaf.basic.BasicComboPopup popup = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
                popup.setBorder(new LineBorder(DARK_BORDER, 1));
                return popup;
            }
        });

        combo.setFont(FONT_BODY);
        combo.setBackground(DARK_FIELD_BG);
        combo.setForeground(DARK_TEXT_PRIMARY);
        combo.setBorder(BORDER_INPUT_DARK);

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                list.setBackground(DARK_FIELD_BG);
                list.setSelectionBackground(DARK_ROW_SELECTED);
                setOpaque(true);
                setBackground(isSelected && index >= 0 ? DARK_ROW_SELECTED : DARK_FIELD_BG);
                setForeground(DARK_TEXT_PRIMARY);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
    }

    public static JSpinner createDarkSpinner(SpinnerNumberModel model) {
        JSpinner spinner = new JSpinner(model);
        styleDarkSpinnerEditor(spinner);
        spinner.setPreferredSize(new Dimension(120, INPUT_HEIGHT));
        return spinner;
    }

    /**
     * Dark styling for a spinner (number, date, ...): dark frame, dark arrow buttons, dark text field.
     * Call after any setEditor(...). The Windows look-and-feel paints its own white frame, so the UI is replaced.
     */
    public static void styleDarkSpinnerEditor(JSpinner spinner) {
        spinner.setUI(new javax.swing.plaf.basic.BasicSpinnerUI() {
            @Override protected Component createNextButton() {
                JButton b = darkSpinnerArrow(true);
                b.setName("Spinner.nextButton");
                installNextButtonListeners(b);
                return b;
            }

            @Override protected Component createPreviousButton() {
                JButton b = darkSpinnerArrow(false);
                b.setName("Spinner.previousButton");
                installPreviousButtonListeners(b);
                return b;
            }
        });
        spinner.setFont(FONT_BODY);
        spinner.setBackground(DARK_FIELD_BG);
        spinner.setBorder(new LineBorder(DARK_BORDER, 1));

        JComponent editor = spinner.getEditor();
        editor.setBackground(DARK_FIELD_BG);
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBorder(new EmptyBorder(6, 10, 6, 10));
            tf.setHorizontalAlignment(JTextField.LEFT);
            tf.setBackground(DARK_FIELD_BG);
            tf.setForeground(DARK_TEXT_PRIMARY);
            tf.setCaretColor(DARK_ACCENT_BLUE);
            tf.setFont(FONT_BODY);
            tf.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    spinner.setBorder(new LineBorder(DARK_ACCENT_BLUE, 2));
                }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    spinner.setBorder(new LineBorder(DARK_BORDER, 1));
                }
            });
        }
    }

    /** Dark tabs: dark background, light text, blue underline on the selected tab. Replaces the Windows tab UI. */
    public static void styleDarkTabs(JTabbedPane tabs) {
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() {
                super.installDefaults();
                tabInsets = new Insets(10, 18, 10, 18);
                selectedTabPadInsets = new Insets(0, 0, 0, 0);
                contentBorderInsets = new Insets(1, 0, 0, 0);
                tabAreaInsets = new Insets(0, 0, 0, 0);
            }

            @Override protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? DARK_CARD : DARK_BG);
                g.fillRect(x, y, w, h);
            }

            @Override protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                if (isSelected) {
                    g.setColor(DARK_ACCENT_BLUE);
                    g.fillRect(x, y + h - 3, w, 3);
                }
            }

            @Override protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(isSelected ? FONT_BODY_BOLD : font);
                g2.setColor(isSelected ? DARK_TEXT_PRIMARY : DARK_TEXT_MUTED);
                g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
            }

            @Override protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) { }

            @Override protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                Insets insets = tabPane.getInsets();
                int top = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) + insets.top;
                g.setColor(DARK_BORDER);
                g.fillRect(insets.left, top, tabPane.getWidth() - insets.left - insets.right, 1);
            }
        });
        tabs.setFont(FONT_BODY);
        tabs.setBackground(DARK_BG);
        tabs.setForeground(DARK_TEXT_PRIMARY);
        tabs.setOpaque(true);
    }

    private static JButton darkSpinnerArrow(boolean up) {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? DARK_BORDER : DARK_FIELD_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(DARK_TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                int dy = up ? -1 : 1;
                java.awt.geom.Path2D.Float chevron = new java.awt.geom.Path2D.Float();
                chevron.moveTo(cx - 3.5f, cy - 1.5f * dy);
                chevron.lineTo(cx, cy + 1.5f * dy);
                chevron.lineTo(cx + 3.5f, cy - 1.5f * dy);
                g2.draw(chevron);
                g2.dispose();
            }
        };
        b.setBorder(new MatteBorder(0, 1, 0, 0, DARK_BORDER));
        b.setContentAreaFilled(false);
        b.setFocusable(false);
        b.setRolloverEnabled(true);
        b.setPreferredSize(new Dimension(20, 12));
        return b;
    }

    public static JPanel createDarkCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(DARK_CARD);
        panel.setBorder(new CompoundBorder(
                new LineBorder(DARK_BORDER, 1),
                new EmptyBorder(SPACING_LG, SPACING_LG, SPACING_LG, SPACING_LG)
        ));
        return panel;
    }

    public static JTable createDarkStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                // Only stripe Swing's built-in renderers; custom ones (e.g. status columns) keep their own colors.
                if (renderer.getClass().getName().startsWith("javax.swing")) {
                    if (c instanceof JComponent) {
                        ((JComponent) c).setBorder(new EmptyBorder(0, SPACING_MD, 0, SPACING_MD));
                    }
                    if (!isRowSelected(row)) {
                        c.setBackground(row % 2 == 0 ? DARK_CARD : DARK_BG);
                        c.setForeground(DARK_TEXT_PRIMARY);
                    } else {
                        c.setBackground(DARK_ROW_SELECTED);
                        c.setForeground(DARK_TEXT_PRIMARY);
                    }
                }
                return c;
            }
        };
        table.setSelectionBackground(DARK_ROW_SELECTED);
        table.setSelectionForeground(DARK_TEXT_PRIMARY);

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, false, false, row, column);
                setOpaque(true);
                setBackground(DARK_BG);
                setForeground(DARK_TEXT_PRIMARY);
                setFont(FONT_TABLE_HEADER);
                setHorizontalAlignment(SwingConstants.LEFT);
                setBorder(new CompoundBorder(
                        new MatteBorder(0, 0, 2, column < t.getColumnCount() - 1 ? 1 : 0, DARK_BORDER),
                        new EmptyBorder(0, 12, 0, 12)
                ));
                return this;
            }
        };
        table.getTableHeader().setDefaultRenderer(headerRenderer);
        table.setFont(FONT_TABLE_CELL);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setShowGrid(true);
        table.setGridColor(DARK_BORDER);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setBackground(DARK_CARD);
        table.setForeground(DARK_TEXT_PRIMARY);

        table.getTableHeader().setFont(FONT_TABLE_HEADER);
        table.getTableHeader().setBackground(DARK_BG);
        table.getTableHeader().setForeground(DARK_TEXT_PRIMARY);
        table.getTableHeader().setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 2, 0, DARK_BORDER),
                new EmptyBorder(8, 12, 8, 12)
        ));
        table.getTableHeader().setPreferredSize(new Dimension(0, TABLE_HEADER_HEIGHT));
        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    public static JScrollPane createDarkScrollPane(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new LineBorder(DARK_BORDER, 1));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.setBackground(DARK_BG);
        scroll.getViewport().setBackground(DARK_CARD);
        return scroll;
    }

    public static void styleDarkDialog(JDialog dialog, String title) {
        dialog.setTitle(title);
        applyAppIcon(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getRootPane().setBorder(new LineBorder(DARK_BORDER, 1));
        dialog.getContentPane().setBackground(DARK_BG);
    }

    public static void styleDarkFrame(JFrame frame, String title, int width, int height) {
        styleFrame(frame, title, width, height);
        frame.getContentPane().setBackground(DARK_BG);
    }

    public static void styleDialog(JDialog dialog, String title) {
        dialog.setTitle(title);
        applyAppIcon(dialog);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getRootPane().setBorder(new LineBorder(BORDER_MEDIUM, 1));
    }

    public static JButton createTileButton(String icon, String title, Color accent) {
        JButton button = new JButton() {
            private boolean hover = false;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), CORNER_RADIUS_LG, CORNER_RADIUS_LG));
                g2.setColor(hover ? accent : BORDER_LIGHT);
                g2.setStroke(new BasicStroke(hover ? 2f : 1f));
                g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, CORNER_RADIUS_LG, CORNER_RADIUS_LG));
                g2.dispose();
                super.paintComponent(g);
            }
            @Override protected void paintBorder(Graphics g) { }
        };
        button.setLayout(new BorderLayout());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(SPACING_LG, SPACING_MD, SPACING_LG, SPACING_MD));

        JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        iconLabel.setForeground(accent);

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(FONT_SUBHEADING);
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(SPACING_SM, 0, 0, 0));

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(iconLabel, BorderLayout.CENTER);
        content.add(titleLabel, BorderLayout.SOUTH);
        button.add(content, BorderLayout.CENTER);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { button.repaint(); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { button.repaint(); }
        });
        return button;
    }

    public static JButton createSidebarButton(String icon, String text, boolean active) {
        return createSidebarButton(icon, text, active, false);
    }

    public static JButton createSidebarButton(String icon, String text, boolean active, boolean dark) {
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        iconLabel.setForeground(active ? TEXT_ON_PRIMARY : (dark ? DARK_ACCENT_BLUE : PRIMARY));
        return createSidebarButton(iconLabel, text, active, dark);
    }

    public static JButton createSidebarButton(Icon icon, String text, boolean active) {
        return createSidebarButton(icon, text, active, false);
    }

    public static JButton createSidebarButton(Icon icon, String text, boolean active, boolean dark) {
        JLabel iconLabel = new JLabel(icon);
        return createSidebarButton(iconLabel, text, active, dark);
    }

    private static JButton createSidebarButton(JLabel iconLabel, String text, boolean active, boolean dark) {
        Color activeBg = dark ? new Color(DARK_ACCENT_BLUE.getRed(), DARK_ACCENT_BLUE.getGreen(), DARK_ACCENT_BLUE.getBlue(), 35) : PRIMARY;
        Color activeFg = dark ? DARK_ACCENT_BLUE : TEXT_ON_PRIMARY;
        Color inactiveFg = dark ? DARK_TEXT_MUTED : TEXT_PRIMARY;

        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(activeBg);
                    g2.fill(new RoundRectangle2D.Float(6, 2, getWidth() - 12, getHeight() - 4, CORNER_RADIUS, CORNER_RADIUS));
                }
                g2.dispose();
                super.paintComponent(g);
            }
            @Override protected void paintBorder(Graphics g) { }
        };
        button.setLayout(new FlowLayout(FlowLayout.LEFT, SPACING_SM, 0));
        button.setOpaque(false);
        button.add(iconLabel);

        JLabel textLabel = new JLabel(text);
        textLabel.setFont(active ? FONT_BUTTON_LARGE : FONT_BODY_BOLD);
        textLabel.setForeground(active ? activeFg : inactiveFg);
        button.add(textLabel);

        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(SPACING_SM, SPACING_MD, SPACING_SM, SPACING_MD));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    public static void styleFrame(JFrame frame, String title, int width, int height) {
        frame.setTitle(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(width, height));
        applyAppIcon(frame);
    }

    /** Small vector icons for action buttons; drawn in code because the button font lacks these glyphs. */
    public static class ButtonIcon implements Icon {
        public enum Kind { EDIT, DELETE, CHECK, CARD }

        private final Kind kind;
        private final int size;
        private final Color color;

        public ButtonIcon(Kind kind) {
            this(kind, 14, Color.WHITE);
        }

        public ButtonIcon(Kind kind, int size, Color color) {
            this.kind = kind;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            float s = size;
            g2.setStroke(new BasicStroke(Math.max(1.5f, s * 0.12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (kind) {
                case EDIT: {
                    g2.setStroke(new BasicStroke(s * 0.22f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                    g2.draw(new java.awt.geom.Line2D.Float(s * 0.36f, s * 0.64f, s * 0.86f, s * 0.14f));
                    java.awt.geom.Path2D.Float tip = new java.awt.geom.Path2D.Float();
                    tip.moveTo(s * 0.06f, s * 0.94f);
                    tip.lineTo(s * 0.17f, s * 0.6f);
                    tip.lineTo(s * 0.4f, s * 0.83f);
                    tip.closePath();
                    g2.fill(tip);
                    break;
                }
                case DELETE: {
                    g2.draw(new java.awt.geom.Line2D.Float(s * 0.1f, s * 0.22f, s * 0.9f, s * 0.22f));
                    g2.draw(new java.awt.geom.Line2D.Float(s * 0.38f, s * 0.08f, s * 0.62f, s * 0.08f));
                    java.awt.geom.Path2D.Float bin = new java.awt.geom.Path2D.Float();
                    bin.moveTo(s * 0.2f, s * 0.32f);
                    bin.lineTo(s * 0.27f, s * 0.94f);
                    bin.lineTo(s * 0.73f, s * 0.94f);
                    bin.lineTo(s * 0.8f, s * 0.32f);
                    g2.draw(bin);
                    g2.draw(new java.awt.geom.Line2D.Float(s * 0.42f, s * 0.44f, s * 0.43f, s * 0.8f));
                    g2.draw(new java.awt.geom.Line2D.Float(s * 0.58f, s * 0.44f, s * 0.57f, s * 0.8f));
                    break;
                }
                case CHECK: {
                    g2.setStroke(new BasicStroke(s * 0.16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    java.awt.geom.Path2D.Float tick = new java.awt.geom.Path2D.Float();
                    tick.moveTo(s * 0.12f, s * 0.54f);
                    tick.lineTo(s * 0.4f, s * 0.8f);
                    tick.lineTo(s * 0.9f, s * 0.22f);
                    g2.draw(tick);
                    break;
                }
                case CARD: {
                    g2.draw(new RoundRectangle2D.Float(s * 0.04f, s * 0.18f, s * 0.92f, s * 0.64f, s * 0.14f, s * 0.14f));
                    g2.fill(new java.awt.geom.Rectangle2D.Float(s * 0.04f, s * 0.33f, s * 0.92f, s * 0.13f));
                    g2.fill(new java.awt.geom.Rectangle2D.Float(s * 0.16f, s * 0.6f, s * 0.24f, s * 0.08f));
                    break;
                }
            }
            g2.dispose();
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    /** Vector "exit door + arrow" icon used for Logout. */
    public static class LogoutIcon implements Icon {
        private final int w;
        private final int h;
        private final Color color;

        public LogoutIcon(int height) {
            this(height, TEXT_PRIMARY);
        }

        public LogoutIcon(int height, Color color) {
            this.h = height;
            this.w = (int) (height * 1.15);
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);

            // Door (right side, slightly ajar)
            RoundRectangle2D door = new RoundRectangle2D.Float(w * 0.52f, h * 0.05f, w * 0.40f, h * 0.90f, h * 0.08f, h * 0.08f);
            g2.fill(door);
            g2.setColor(Color.WHITE);
            g2.fill(new java.awt.geom.Ellipse2D.Float(w * 0.58f, h * 0.47f, h * 0.07f, h * 0.07f));
            g2.setColor(color);

            // Door-frame bracket, open to the left
            float strokeW = Math.max(1.5f, h * 0.10f);
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float bracket = new java.awt.geom.Path2D.Float();
            bracket.moveTo(w * 0.40f, h * 0.18f);
            bracket.lineTo(w * 0.30f, h * 0.18f);
            bracket.lineTo(w * 0.30f, h * 0.82f);
            bracket.lineTo(w * 0.40f, h * 0.82f);
            g2.draw(bracket);

            // Exit arrow pointing left, through the bracket opening
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Float(w * 0.30f, h * 0.5f, w * 0.0f, h * 0.5f));
            int[] ax = {(int) (w * 0.0f), (int) (w * 0.20f), (int) (w * 0.20f)};
            int[] ay = {(int) (h * 0.5f), (int) (h * 0.32f), (int) (h * 0.68f)};
            g2.fillPolygon(ax, ay, 3);

            g2.dispose();
        }

        @Override public int getIconWidth() { return w; }
        @Override public int getIconHeight() { return h; }
    }

    /** Small padlock for the "Change Password" sidebar entry, drawn to match LogoutIcon. */
    public static class KeyIcon implements Icon {
        private final int size;
        private final Color color;

        public KeyIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            float s = size;
            float strokeW = Math.max(1.5f, s * 0.11f);
            // shackle
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Arc2D.Float(s * 0.27f, s * 0.06f, s * 0.46f, s * 0.56f, 0, 180, java.awt.geom.Arc2D.OPEN));
            g2.draw(new java.awt.geom.Line2D.Float(s * 0.27f, s * 0.34f, s * 0.27f, s * 0.46f));
            g2.draw(new java.awt.geom.Line2D.Float(s * 0.73f, s * 0.34f, s * 0.73f, s * 0.46f));
            // body with a keyhole
            g2.fill(new RoundRectangle2D.Float(s * 0.14f, s * 0.44f, s * 0.72f, s * 0.52f, s * 0.14f, s * 0.14f));
            g2.setColor(DARK_SIDEBAR);
            g2.fill(new java.awt.geom.Ellipse2D.Float(s * 0.43f, s * 0.58f, s * 0.14f, s * 0.14f));
            g2.fill(new java.awt.geom.Rectangle2D.Float(s * 0.475f, s * 0.68f, s * 0.05f, s * 0.14f));
            g2.dispose();
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    // ===== Heritage Crest brand (gold shield + serif "RentNGo" wordmark) =====

    public static final Color CREST_GOLD = new Color(201, 164, 92);
    public static final Color CREST_IVORY = new Color(243, 239, 230);
    public static final Color CREST_DARK = new Color(20, 20, 22);
    public static final Color CREST_TAGLINE = new Color(142, 139, 133);
    public static final String BRAND_TAGLINE = "DRIVE IN DISTINCTION";

    private static String serifFamily;

    /** Elegant serif for the brand: Garamond when installed, otherwise Georgia. */
    public static Font brandSerif(int style, float size) {
        if (serifFamily == null) {
            serifFamily = "Georgia";
            for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (f.equalsIgnoreCase("Garamond")) {
                    serifFamily = f;
                    break;
                }
            }
        }
        return new Font(serifFamily, style, 12).deriveFont(size);
    }

    /**
     * Gold shield badge with a serif "R" over a sports-car silhouette. Below 28px it switches
     * to the solid gold shield with a dark "R", which stays readable at small sizes.
     */
    public static class CrestIcon implements Icon {
        private final int w;
        private final int h;

        public CrestIcon(int height) {
            this.h = height;
            this.w = Math.round(height * 0.8f);
        }

        private java.awt.geom.Path2D.Float shield(float x, float y, float sw, float sh) {
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(x + sw * 0.5f, y);
            p.lineTo(x + sw, y + sh * 0.13f);
            p.lineTo(x + sw, y + sh * 0.52f);
            p.curveTo(x + sw, y + sh * 0.78f, x + sw * 0.74f, y + sh * 0.92f, x + sw * 0.5f, y + sh);
            p.curveTo(x + sw * 0.26f, y + sh * 0.92f, x, y + sh * 0.78f, x, y + sh * 0.52f);
            p.lineTo(x, y + sh * 0.13f);
            p.closePath();
            return p;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            boolean compact = h < 28;

            float stroke = compact ? 0 : Math.max(1.5f, h * 0.045f);
            java.awt.geom.Path2D.Float outer = shield(stroke / 2, stroke / 2, w - stroke, h - stroke);

            if (compact) {
                g2.setColor(CREST_GOLD);
                g2.fill(outer);
                drawR(g2, CREST_DARK, 0.62f, 0.50f);
            } else {
                g2.setColor(CREST_DARK);
                g2.fill(outer);
                g2.setColor(CREST_GOLD);
                g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(outer);

                // thin inner keyline
                float inset = h * 0.10f;
                g2.setStroke(new BasicStroke(Math.max(0.8f, h * 0.012f)));
                g2.setColor(new Color(CREST_GOLD.getRed(), CREST_GOLD.getGreen(), CREST_GOLD.getBlue(), 170));
                g2.draw(shield(inset, inset * 0.95f, w - inset * 2, h - inset * 2.05f));

                drawR(g2, CREST_GOLD, 0.47f, 0.455f);
                drawCar(g2);
            }
            g2.dispose();
        }

        private void drawR(Graphics2D g2, Color color, float sizeRatio, float centerY) {
            Font f = brandSerif(Font.PLAIN, h * sizeRatio);
            java.awt.font.GlyphVector gv = f.createGlyphVector(g2.getFontRenderContext(), "R");
            java.awt.geom.Rectangle2D b = gv.getVisualBounds();
            float gx = (float) (w / 2.0 - b.getCenterX());
            float gy = (float) (h * centerY - b.getCenterY());
            g2.setColor(color);
            g2.fill(gv.getOutline(gx, gy));
        }

        /** Low sports-car profile across the lower third of the shield. */
        private void drawCar(Graphics2D g2) {
            float cw = w * 0.58f;
            float cx = (w - cw) / 2f;
            float base = h * 0.735f;
            float ch = h * 0.085f;
            java.awt.geom.Path2D.Float car = new java.awt.geom.Path2D.Float();
            car.moveTo(cx, base);
            car.curveTo(cx, base - ch * 0.55f, cx + cw * 0.08f, base - ch * 0.6f, cx + cw * 0.2f, base - ch * 0.62f);
            car.curveTo(cx + cw * 0.32f, base - ch * 1.25f, cx + cw * 0.62f, base - ch * 1.3f, cx + cw * 0.74f, base - ch * 0.66f);
            car.curveTo(cx + cw * 0.92f, base - ch * 0.6f, cx + cw, base - ch * 0.4f, cx + cw, base);
            car.closePath();
            g2.setColor(CREST_GOLD);
            g2.fill(car);

            float wd = ch * 0.85f;
            for (float fx : new float[]{0.22f, 0.76f}) {
                float wx = cx + cw * fx - wd / 2;
                float wy = base - wd * 0.45f;
                g2.setColor(CREST_DARK);
                g2.fill(new java.awt.geom.Ellipse2D.Float(wx - wd * 0.15f, wy - wd * 0.15f, wd * 1.3f, wd * 1.3f));
                g2.setColor(CREST_GOLD);
                g2.fill(new java.awt.geom.Ellipse2D.Float(wx + wd * 0.18f, wy + wd * 0.18f, wd * 0.64f, wd * 0.64f));
            }
        }

        @Override public int getIconWidth() { return w; }
        @Override public int getIconHeight() { return h; }
    }

    /** Crest + serif "Rent<i>N</i>Go" wordmark, with an optional letter-spaced tagline. */
    public static class BrandLockup extends JComponent {
        private final CrestIcon crest;
        private final Font wordFont;
        private final Font nFont;
        private final Font tagFont;
        private final boolean showTagline;
        private final int gap;

        public BrandLockup(int iconHeight, int fontSize, boolean showTagline) {
            this.crest = new CrestIcon(iconHeight);
            // Garamond is light; use its bold cut at small sizes so the wordmark stays crisp.
            int weight = fontSize < 26 ? Font.BOLD : Font.PLAIN;
            this.wordFont = brandSerif(weight, fontSize * 1.18f);
            this.nFont = brandSerif(weight | Font.ITALIC, fontSize * 1.18f);
            java.util.Map<java.awt.font.TextAttribute, Object> track = new java.util.HashMap<>();
            track.put(java.awt.font.TextAttribute.TRACKING, 0.42f);
            this.tagFont = new Font("Segoe UI", Font.PLAIN, Math.max(9, Math.round(fontSize * 0.30f))).deriveFont(track);
            this.showTagline = showTagline;
            this.gap = Math.max(8, Math.round(iconHeight * 0.32f));
            setOpaque(false);
            Dimension d = computeSize();
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        private int wordWidth(FontMetrics wm, FontMetrics nm) {
            return wm.stringWidth("Rent") + nm.stringWidth("N") + wm.stringWidth("Go");
        }

        private Dimension computeSize() {
            FontMetrics wm = getFontMetrics(wordFont);
            FontMetrics nm = getFontMetrics(nFont);
            FontMetrics tm = getFontMetrics(tagFont);
            int textW = wordWidth(wm, nm);
            int textH = wm.getAscent() + wm.getDescent() / 2;
            if (showTagline) {
                textW = Math.max(textW, tm.stringWidth(BRAND_TAGLINE));
                textH += tm.getHeight() + 2;
            }
            return new Dimension(crest.getIconWidth() + gap + textW + 2, Math.max(crest.getIconHeight(), textH) + 2);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Dimension d = computeSize();
            int ox = Math.max(0, (getWidth() - d.width) / 2);
            int oy = (getHeight() - d.height) / 2 + 1;
            crest.paintIcon(this, g2, ox, oy + (d.height - 2 - crest.getIconHeight()) / 2);

            FontMetrics wm = g2.getFontMetrics(wordFont);
            FontMetrics nm = g2.getFontMetrics(nFont);
            FontMetrics tm = g2.getFontMetrics(tagFont);
            int textH = wm.getAscent() + wm.getDescent() / 2 + (showTagline ? tm.getHeight() + 2 : 0);
            int tx = ox + crest.getIconWidth() + gap;
            int baseline = oy + (d.height - 2 - textH) / 2 + wm.getAscent();

            g2.setFont(wordFont);
            g2.setColor(BRAND_WORD);
            g2.drawString("Rent", tx, baseline);
            int nx = tx + wm.stringWidth("Rent");
            g2.setFont(nFont);
            g2.setColor(CREST_GOLD);
            g2.drawString("N", nx, baseline);
            g2.setFont(wordFont);
            g2.setColor(BRAND_WORD);
            g2.drawString("Go", nx + nm.stringWidth("N"), baseline);

            if (showTagline) {
                g2.setFont(tagFont);
                g2.setColor(CREST_TAGLINE);
                g2.drawString(BRAND_TAGLINE, tx + 2, baseline + wm.getDescent() / 2 + tm.getAscent() + 4);
            }
            g2.dispose();
        }
    }

    /** Full brand lockup without tagline (sidebars and headers). */
    public static JComponent createBrandPanel(int iconHeight, int fontSize, Color restColor) {
        return new BrandLockup(iconHeight, fontSize, false);
    }

    /** Full brand lockup, optionally with the "DRIVE IN DISTINCTION" tagline. */
    public static JComponent createBrandPanel(int iconHeight, int fontSize, boolean showTagline) {
        return new BrandLockup(iconHeight, fontSize, showTagline);
    }

    /** Puts the gold crest in the window's title bar and taskbar button. */
    public static void applyAppIcon(Window window) {
        java.util.List<Image> icons = new java.util.ArrayList<>();
        for (int size : new int[]{16, 20, 24, 32, 48, 64}) {
            CrestIcon crest = new CrestIcon(size);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            crest.paintIcon(null, g, (size - crest.getIconWidth()) / 2, 0);
            g.dispose();
            icons.add(img);
        }
        window.setIconImages(icons);
    }
}