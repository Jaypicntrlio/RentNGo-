package gui;

import model.Vehicle;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Draws a simple vector placeholder image (car or person) used where the
 * mockups show a photo. No external image files are required.
 */
public class SilhouettePanel extends JPanel {

    public enum Kind {
        CAR, SUV, TRUCK, PERSON;

        /** The picture for a vehicle, chosen from the vehicle's own category ("Car" -> CAR). */
        public static Kind of(Vehicle v) {
            return valueOf(v.getVehicleCategory().toUpperCase(java.util.Locale.ROOT));
        }
    }

    private Kind kind;
    private Color accent;
    private BufferedImage photo;
    private BufferedImage scaled;
    private Color borderColor = UIConstants.BORDER_LIGHT;

    public SilhouettePanel(Kind kind) {
        this(kind, UIConstants.PRIMARY);
    }

    public SilhouettePanel(Kind kind, Color accent) {
        this.kind = kind;
        this.accent = accent;
        setOpaque(true);
        setBackground(new Color(246, 248, 250));
        setPreferredSize(new Dimension(260, 170));
    }

    public void setKind(Kind kind) {
        this.kind = kind;
        repaint();
    }

    /** Shows a real photo instead of the drawn placeholder; null goes back to the placeholder. */
    public void setPhoto(BufferedImage photo) {
        this.photo = photo;
        this.scaled = null;
        repaint();
    }

    /** Dark background and border for the dark-themed windows. */
    public void setDarkMode(boolean dark) {
        setBackground(dark ? UIConstants.DARK_FIELD_BG : new Color(246, 248, 250));
        borderColor = dark ? UIConstants.DARK_BORDER : UIConstants.BORDER_LIGHT;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (getParent() != null) {
            g2.setColor(getParent().getBackground());
            g2.fillRect(0, 0, w, h);
        }

        if (photo != null) {
            drawPhoto(g2, w, h);
            g2.dispose();
            return;
        }

        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

        if (kind == Kind.PERSON) {
            drawPerson(g2, w, h);
        } else {
            drawCar(g2, w, h);
        }

        g2.dispose();
    }

    private void drawPhoto(Graphics2D g2, int w, int h) {
        // Product photos usually sit on a plain backdrop; extend that colour to the whole card.
        Color backdrop = new Color(photo.getRGB(2, 2));
        g2.setColor(backdrop);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);

        int pad = 6;
        double scale = Math.min((w - pad * 2) / (double) photo.getWidth(),
                (h - pad * 2) / (double) photo.getHeight());
        int iw = Math.max(1, (int) Math.round(photo.getWidth() * scale));
        int ih = Math.max(1, (int) Math.round(photo.getHeight() * scale));
        if (scaled == null || scaled.getWidth() != iw || scaled.getHeight() != ih) {
            scaled = downscale(photo, iw, ih);
        }
        g2.drawImage(scaled, (w - iw) / 2, (h - ih) / 2, null);

        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
    }

    /** Halves the image step by step so large photos stay sharp when shrunk. */
    static BufferedImage downscale(BufferedImage src, int tw, int th) {
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        do {
            cw = Math.max(tw, cw / 2);
            ch = Math.max(th, ch / 2);
            if (cw < tw * 2 && ch < th * 2) {
                cw = tw;
                ch = th;
            }
            BufferedImage next = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, cw, ch, null);
            g.dispose();
            cur = next;
        } while (cw != tw || ch != th);
        return cur;
    }

    private void drawCar(Graphics2D g2, int w, int h) {
        int bodyW = (int) (w * 0.62);
        int bodyH = (int) (h * 0.30);
        int bodyX = (w - bodyW) / 2;
        int bodyY = (int) (h * 0.46);

        // ground shadow
        g2.setColor(new Color(0, 0, 0, 25));
        g2.fillOval(bodyX - 10, bodyY + bodyH + 10, bodyW + 20, 14);

        // body
        RoundRectangle2D body = new RoundRectangle2D.Double(bodyX, bodyY, bodyW, bodyH, 18, 18);
        g2.setColor(accent);
        g2.fill(body);

        // cabin / roof
        int roofW = (int) (bodyW * 0.55);
        int roofH = (int) (bodyH * 1.1);
        int roofX = bodyX + (bodyW - roofW) / 2;
        int roofY = bodyY - (int) (roofH * 0.55);
        RoundRectangle2D roof = new RoundRectangle2D.Double(roofX, roofY, roofW, roofH, 16, 16);
        g2.fill(roof);

        // windows
        g2.setColor(new Color(255, 255, 255, 210));
        int winInset = 8;
        g2.fillRoundRect(roofX + winInset, roofY + winInset, roofW - winInset * 2, roofH / 2, 8, 8);

        // wheels
        int wheelD = (int) (bodyH * 0.9);
        g2.setColor(new Color(40, 40, 40));
        g2.fillOval(bodyX + bodyW / 6, bodyY + bodyH - wheelD / 2, wheelD, wheelD);
        g2.fillOval(bodyX + bodyW - bodyW / 6 - wheelD, bodyY + bodyH - wheelD / 2, wheelD, wheelD);
        g2.setColor(new Color(200, 200, 200));
        int hubD = wheelD / 2;
        g2.fillOval(bodyX + bodyW / 6 + hubD / 2, bodyY + bodyH - wheelD / 2 + hubD / 2, hubD, hubD);
        g2.fillOval(bodyX + bodyW - bodyW / 6 - wheelD + hubD / 2, bodyY + bodyH - wheelD / 2 + hubD / 2, hubD, hubD);

        // headlight
        g2.setColor(Color.WHITE);
        g2.fillOval(bodyX + bodyW - 16, bodyY + bodyH / 2 - 5, 10, 10);

        // type label
        String label = kind.name();
        g2.setFont(UIConstants.FONT_SMALL);
        g2.setColor(UIConstants.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, (w - fm.stringWidth(label)) / 2, h - 10);
    }

    private void drawPerson(Graphics2D g2, int w, int h) {
        int cx = w / 2;
        int headD = (int) (h * 0.36);
        int headX = cx - headD / 2;
        int headY = (int) (h * 0.14);

        g2.setColor(accent);
        g2.fillOval(headX, headY, headD, headD);

        int shoulderW = (int) (w * 0.62);
        int shoulderH = (int) (h * 0.42);
        int shoulderX = cx - shoulderW / 2;
        int shoulderY = headY + headD - 6;
        g2.fill(new RoundRectangle2D.Double(shoulderX, shoulderY, shoulderW, shoulderH, 40, 40));
    }
}
