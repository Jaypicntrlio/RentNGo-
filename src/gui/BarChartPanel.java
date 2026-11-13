package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Minimal vertical column chart: no external charting library, pure Java2D.
 */
public class BarChartPanel extends JPanel {
    private String[] labels;
    private double[] values;
    private Color barColor;
    private boolean showValueLabels = true;
    private boolean darkMode = false;
    private boolean countValues = false;

    public BarChartPanel(String[] labels, double[] values, Color barColor) {
        this.labels = labels;
        this.values = values;
        this.barColor = barColor;
        setOpaque(false);
        setPreferredSize(new Dimension(400, 180));
    }

    public void setData(String[] labels, double[] values) {
        this.labels = labels;
        this.values = values;
        repaint();
    }

    public void setShowValueLabels(boolean show) {
        this.showValueLabels = show;
    }

    /** Label bars with plain counts instead of peso amounts. */
    public void setCountValues(boolean countValues) {
        this.countValues = countValues;
        repaint();
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (labels == null || values == null || labels.length == 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int topPad = showValueLabels ? 22 : 8;
        int bottomPad = 20;
        int chartH = h - topPad - bottomPad;

        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;

        int n = values.length;
        double slot = (double) w / n;
        double barW = Math.min(slot * 0.5, 44);

        Color axisColor = darkMode ? UIConstants.DARK_BORDER : UIConstants.BORDER_LIGHT;
        Color valueTextColor = darkMode ? UIConstants.DARK_TEXT_MUTED : UIConstants.TEXT_SECONDARY;
        Color labelTextColor = darkMode ? UIConstants.DARK_TEXT_MUTED : UIConstants.TEXT_MUTED;

        g2.setColor(axisColor);
        g2.drawLine(0, h - bottomPad, w, h - bottomPad);

        g2.setFont(UIConstants.FONT_SMALL);
        for (int i = 0; i < n; i++) {
            double ratio = values[i] / max;
            int barH = (int) (chartH * ratio);
            int x = (int) (i * slot + (slot - barW) / 2);
            int y = h - bottomPad - barH;

            g2.setColor(barColor);
            g2.fill(new RoundRectangle2D.Double(x, y, barW, Math.max(barH, 2), 6, 6));

            if (showValueLabels && values[i] > 0) {
                String valText = formatValue(values[i]);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(valueTextColor);
                g2.drawString(valText, x + (int) (barW - fm.stringWidth(valText)) / 2, y - 6);
            }

            String label = labels[i];
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(labelTextColor);
            g2.drawString(label, (int) (x + (barW - fm.stringWidth(label)) / 2), h - 4);
        }

        g2.dispose();
    }

    private String formatValue(double v) {
        if (countValues) return String.format("%,.0f", v);
        if (v >= 1000) return String.format("\u20B1%.1fk", v / 1000.0);
        return String.format("\u20B1%,.0f", v);
    }
}
