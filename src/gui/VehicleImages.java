package gui;

import model.Vehicle;
import service.ProjectPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads vehicle photos from the project's "images" folder (RentNGo/images).
 * A vehicle uses its own imagePath when set, otherwise a file named after its
 * ID (VEH001.jpg / .jpeg / .png). Vehicles without a photo get null, so callers
 * can fall back to the drawn placeholder.
 */
public final class VehicleImages {

    private static final String[] EXTENSIONS = {"jpg", "jpeg", "png"};
    private static final Map<String, BufferedImage> cache = new HashMap<>();
    private static File imagesDir;

    private VehicleImages() {}

    public static File getImagesDir() {
        if (imagesDir == null) {
            imagesDir = ProjectPaths.dir("images");
        }
        return imagesDir;
    }

    /** Returns the vehicle's photo, or null when it has none. */
    public static BufferedImage load(Vehicle vehicle) {
        File file = findFile(vehicle);
        if (file == null) return null;
        String key = file.getAbsolutePath() + "@" + file.lastModified();
        if (cache.containsKey(key)) return cache.get(key);
        BufferedImage img = null;
        try {
            img = ImageIO.read(file);
        } catch (IOException ignored) {
            // unreadable file: treat as no photo
        }
        cache.put(key, img);
        return img;
    }

    private static File findFile(Vehicle vehicle) {
        if (vehicle == null) return null;
        File dir = getImagesDir();
        String path = vehicle.getImagePath();
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (!f.isAbsolute()) f = new File(dir, path);
            if (f.isFile()) return f;
        }
        for (String ext : EXTENSIONS) {
            File f = new File(dir, vehicle.getVehicleId() + "." + ext);
            if (f.isFile()) return f;
        }
        return null;
    }

    /**
     * Copies a chosen picture into the images folder as {vehicleId}.{ext},
     * replacing any older photo of that vehicle. Returns the stored file name.
     */
    public static String importImage(File source, String vehicleId) throws IOException {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "png";
        File dir = getImagesDir();
        for (String old : EXTENSIONS) {
            File f = new File(dir, vehicleId + "." + old);
            if (!f.getName().equals(vehicleId + "." + ext)) f.delete();
        }
        File target = new File(dir, vehicleId + "." + ext);
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target.getName();
    }

    public static boolean isSupported(File file) {
        String n = file.getName().toLowerCase();
        for (String ext : EXTENSIONS) {
            if (n.endsWith("." + ext)) return true;
        }
        return false;
    }
}
