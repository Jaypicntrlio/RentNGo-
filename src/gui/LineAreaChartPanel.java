package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Minimal line + filled-area chart with a light grid, for a dark-themed card.
 * Pure Java2D, no external library.
 */
public class LineAreaChartPanel extends JPanel {
    private String[] labels;
    private double[] values;
    private Color lineColor;

    public LineAreaChartPanel(String[] labels, double[] values, Color lineColor) {
        this.labels = labels;
        this.values = values;
        this.lineColor = lineColor;
        setOpaque(false);
        setPreferredSize(new Dimension(400, 200));
    }

    public void setData(String[] labels, double[] values) {
        this.labels = labels;
        this.values = values;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (labels == null || values == null || values.length < 2) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int leftPad = 44;
        int bottomPad = 20;
        int topPad = 10;
        int chartW = w - leftPad;
        int chartH = h - topPad - bottomPad;

        double max = 0, min = Double.MAX_VALUE;
        for (double v : values) {
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        if (max == min) max = min + 1;
        double range = max - min;

        g2.setFont(UIConstants.FONT_SMALL);
        g2.setColor(UIConstants.DARK_BORDER);
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            int y = topPad + chartH * i / gridLines;
            g2.drawLine(leftPad, y, w, y);
            double val = max - (range * i / gridLines);
            String valText = formatValue(val);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(UIConstants.DARK_TEXT_MUTED);
            g2.drawString(valText, leftPad - fm.stringWidth(valText) - 8, y + fm.getAscent() / 2 - 1);
            g2.setColor(UIConstants.DARK_BORDER);
        }

        int n = values.length;
        double stepX = n > 1 ? (double) chartW / (n - 1) : 0;
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = (int) (leftPad + stepX * i);
            double ratio = (values[i] - min) / range;
            py[i] = (int) (topPad + chartH - chartH * ratio);
        }

        // filled area under the line
        Path2D.Float area = new Path2D.Float();
        area.moveTo(px[0], topPad + chartH);
        for (int i = 0; i < n; i++) area.lineTo(px[i], py[i]);
        area.lineTo(px[n - 1], topPad + chartH);
        area.closePath();

        Color fillTop = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 90);
        Color fillBottom = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0);
        g2.setPaint(new GradientPaint(0, topPad, fillTop, 0, topPad + chartH, fillBottom));
        g2.fill(area);

        // line
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(lineColor);
        Path2D.Float line = new Path2D.Float();
        line.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) line.lineTo(px[i], py[i]);
        g2.draw(line);

        // dots
        for (int i = 0; i < n; i++) {
            g2.setColor(lineColor);
            g2.fill(new Ellipse2D.Float(px[i] - 3.5f, py[i] - 3.5f, 7, 7));
        }

        // x labels
        g2.setColor(UIConstants.DARK_TEXT_MUTED);
        for (int i = 0; i < n; i++) {
            String label = labels[i];
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, px[i] - fm.stringWidth(label) / 2, h - 4);
        }

        g2.dispose();
    }

    private String formatValue(double v) {
        if (v >= 1000) return String.format("%.1fk", v / 1000.0);
        return String.format("%.0f", v);
    }
}
