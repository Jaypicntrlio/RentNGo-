package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Arc2D;

/**
 * Minimal donut chart with a side legend; pure Java2D, no external library.
 */
public class DonutChartPanel extends JPanel {
    private String[] labels;
    private double[] values;
    private Color[] colors;

    public DonutChartPanel(String[] labels, double[] values, Color[] colors) {
        this.labels = labels;
        this.values = values;
        this.colors = colors;
        setOpaque(false);
        setLayout(new BorderLayout(UIConstants.SPACING_MD, 0));

        RingPanel ring = new RingPanel();
        ring.setPreferredSize(new Dimension(84, 84));
        add(ring, BorderLayout.WEST);
        add(buildLegend(), BorderLayout.CENTER);
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel();
        legend.setOpaque(false);
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setBorder(new EmptyBorder(UIConstants.SPACING_SM, 0, 0, 0));

        double total = 0;
        for (double v : values) total += v;

        for (int i = 0; i < labels.length; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SM, 3));
            row.setOpaque(false);

            JLabel dot = new JLabel("●");
            dot.setForeground(colors[i % colors.length]);
            row.add(dot);

            double pct = total > 0 ? (values[i] / total * 100) : 0;
            JLabel text = new JLabel(String.format("%s (%.0f%%)", labels[i], pct));
            text.setFont(UIConstants.FONT_SMALL);
            text.setForeground(UIConstants.TEXT_SECONDARY);
            row.add(text);

            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            legend.add(row);
        }
        return legend;
    }

    private class RingPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            int thickness = Math.max(10, size / 6);

            double total = 0;
            for (double v : values) total += v;

            if (total <= 0) {
                g2.setColor(UIConstants.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(thickness));
                g2.draw(new Arc2D.Double(x, y, size, size, 0, 360, Arc2D.OPEN));
                g2.dispose();
                return;
            }

            double startAngle = 90;
            g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < values.length; i++) {
                double sweep = -(values[i] / total) * 360;
                g2.setColor(colors[i % colors.length]);
                g2.draw(new Arc2D.Double(x, y, size, size, startAngle, sweep, Arc2D.OPEN));
                startAngle += sweep;
            }
            g2.dispose();
        }
    }
}
