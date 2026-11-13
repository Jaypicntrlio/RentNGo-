package gui;

import service.ProjectPaths;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Profile pictures for admins and customers, stored as images/profiles/{userId}.png.
 * Uploaded pictures are centre-cropped to a square so they fill the round avatar.
 */
public final class ProfilePhotos {

    private static final int STORED_SIZE = 320;
    private static final Map<String, BufferedImage> cache = new HashMap<>();

    private ProfilePhotos() {}

    /** The user's photo, or null when they have none. */
    public static BufferedImage load(String userId) {
        if (userId == null) return null;
        File f = ProjectPaths.profilePhoto(userId);
        if (!f.isFile()) return null;
        String key = f.getAbsolutePath() + "@" + f.lastModified();
        if (!cache.containsKey(key)) {
            try {
                cache.put(key, ImageIO.read(f));
            } catch (IOException e) {
                cache.put(key, null);
            }
        }
        return cache.get(key);
    }

    /** Reads a chosen picture; returns null if it is not a readable JPG/PNG. */
    public static BufferedImage read(File file) {
        try {
            return VehicleImages.isSupported(file) ? ImageIO.read(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Saves a square, centre-cropped copy of the picture as the user's profile photo. */
    public static void save(String userId, BufferedImage picture) throws IOException {
        ImageIO.write(squareCrop(picture, STORED_SIZE), "png", ProjectPaths.profilePhoto(userId));
    }

    public static BufferedImage squareCrop(BufferedImage src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        // people's faces sit in the upper part of portrait photos, so bias the crop upward
        int x = (src.getWidth() - side) / 2;
        int y = src.getHeight() > src.getWidth() ? (src.getHeight() - side) / 4 : (src.getHeight() - side) / 2;
        BufferedImage square = src.getSubimage(x, y, side, side);
        return SilhouettePanel.downscale(square, Math.min(size, side), Math.min(size, side));
    }

    /**
     * Round avatar: the photo cropped to a circle with a thin gold ring, or a neutral
     * person silhouette when there is no photo.
     */
    public static class Avatar implements Icon {
        private final int size;
        private BufferedImage photo;
        private BufferedImage scaled;

        public Avatar(String userId, int size) {
            this(load(userId), size);
        }

        public Avatar(BufferedImage photo, int size) {
            this.photo = photo;
            this.size = size;
        }

        public void setPhoto(BufferedImage photo) {
            this.photo = photo;
            this.scaled = null;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            float ring = Math.max(1.5f, size * 0.03f);

            if (photo != null) {
                if (scaled == null) {
                    scaled = circular(photo, size);
                }
                g2.drawImage(scaled, 0, 0, null);
                g2.setColor(UIConstants.CREST_GOLD);
            } else {
                g2.setColor(UIConstants.DARK_FIELD_BG);
                g2.fill(new Ellipse2D.Float(0, 0, size, size));
                drawPerson(g2);
                g2.setColor(UIConstants.DARK_BORDER);
            }
            g2.setStroke(new BasicStroke(ring));
            g2.draw(new Ellipse2D.Float(ring / 2, ring / 2, size - ring, size - ring));
            g2.dispose();
        }

        private void drawPerson(Graphics2D g2) {
            Shape clip = g2.getClip();
            g2.clip(new Ellipse2D.Float(0, 0, size, size));
            g2.setColor(new Color(90, 90, 98));
            float head = size * 0.36f;
            g2.fill(new Ellipse2D.Float((size - head) / 2, size * 0.2f, head, head));
            float body = size * 0.7f;
            g2.fill(new Ellipse2D.Float((size - body) / 2, size * 0.62f, body, body * 0.8f));
            g2.setClip(clip);
        }

        /** Crops the square photo to an anti-aliased circle. */
        private static BufferedImage circular(BufferedImage photo, int size) {
            BufferedImage square = photo.getWidth() == photo.getHeight()
                    ? photo : squareCrop(photo, Math.max(photo.getWidth(), photo.getHeight()));
            BufferedImage resized = SilhouettePanel.downscale(square, size, size);
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(0, 0, size, size));
            g.setComposite(AlphaComposite.SrcIn);
            g.drawImage(resized, 0, 0, null);
            g.dispose();
            return out;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
