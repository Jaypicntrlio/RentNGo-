package gui;

import service.ProjectPaths;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/** Checks before a receipt or export is written to a file the user picked. */
final class FileSaving {

    private FileSaving() {}

    /**
     * False (after telling the user) when the file is inside RentNGo's data folder, or when it already
     * exists and the user doesn't want to replace it. Saving over data/accounts.txt would stop the app
     * from starting.
     */
    static boolean okToWrite(Component parent, File file) {
        if (isInsideDataFolder(file)) {
            JOptionPane.showMessageDialog(parent, "Please save to another folder. The data folder holds RentNGo's "
                    + "own files, and replacing one of them would damage the saved data.", "Choose Another Folder",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (file.exists()) {
            int answer = JOptionPane.showConfirmDialog(parent, file.getName() + " already exists. Replace it?",
                    "Replace File", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            return answer == JOptionPane.YES_OPTION;
        }
        return true;
    }

    static boolean isInsideDataFolder(File file) {
        try {
            File data = ProjectPaths.dir("data").getCanonicalFile();
            for (File f = file.getCanonicalFile(); f != null; f = f.getParentFile()) {
                if (f.equals(data)) return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
