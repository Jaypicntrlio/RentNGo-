package service;

import java.io.File;

/** Locates the RentNGo project folder so data and images live beside src/ and out/. */
public final class ProjectPaths {

    private static File root;

    private ProjectPaths() {}

    public static File root() {
        if (root == null) {
            root = locateRoot();
        }
        return root;
    }

    public static File dir(String name) {
        File dir = new File(root(), name);
        dir.mkdirs();
        return dir;
    }

    /** Where a user's profile photo is stored: images/profiles/{userId}.png */
    public static File profilePhoto(String userId) {
        return new File(dir("images/profiles"), userId + ".png");
    }

    private static File locateRoot() {
        // Classes live in RentNGo/src or RentNGo/out, so the project root is one level up.
        try {
            File codeDir = new File(ProjectPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File parent = codeDir.isDirectory() ? codeDir.getParentFile() : codeDir.getParentFile().getParentFile();
            if (parent != null) {
                return parent;
            }
        } catch (Exception ignored) {
            // fall through to the working directory
        }
        File cwd = new File(System.getProperty("user.dir"));
        if (cwd.getName().equalsIgnoreCase("src") || cwd.getName().equalsIgnoreCase("out")) {
            cwd = cwd.getParentFile();
        }
        return cwd;
    }
}
