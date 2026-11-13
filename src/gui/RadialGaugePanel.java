package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;

/**
 * A single-value radial progress ring with a centered label, e.g. "$94".
 * Pure Java2D, no external library.
 */
public class RadialGaugePanel extends JPanel {
    private double percent; // 0..100
    private String centerText;
    private Color ringColor;

    public RadialGaugePanel(double percent, String centerText, Color ringColor) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.centerText = centerText;
        this.ringColor = ringColor;
        setOpaque(false);
        setPreferredSize(new Dimension(120, 120));
    }

    public void setValue(double percent, String centerText) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.centerText = centerText;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int size = Math.min(getWidth(), getHeight()) - 8;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        int thickness = Math.max(8, size / 9);

        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(UIConstants.BORDER_LIGHT);
        g2.draw(new Arc2D.Double(x, y, size, size, 0, 360, Arc2D.OPEN));

        g2.setColor(ringColor);
        double sweep = -(percent / 100.0) * 360;
        g2.draw(new Arc2D.Double(x, y, size, size, 90, sweep, Arc2D.OPEN));

        if (centerText != null) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, size / 5));
            g2.setColor(UIConstants.TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(centerText);
            g2.drawString(centerText, getWidth() / 2 - tw / 2, getHeight() / 2 + fm.getAscent() / 3);
        }

        g2.dispose();
    }
}
