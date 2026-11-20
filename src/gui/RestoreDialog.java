package gui;

import model.exceptions.ValidationException;
import service.AuthService;
import service.BackupService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin "Restore": pick a backup (from backups/ or any other folder), confirm, and RentNGo replaces
 * its data with it. The current data is backed up first, then the app restarts at the login screen.
 */
public class RestoreDialog extends JDialog {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthService authService;
    private final BackupService backupService;
    private DefaultTableModel model;
    private JTable table;
    private List<File> backups;

    public RestoreDialog(JFrame parent, AuthService authService, BackupService backupService) {
        super(parent, true);
        this.authService = authService;
        this.backupService = backupService;
        UIConstants.styleDarkDialog(this, "Restore Data");
        buildUI();
        loadBackups();
        setSize(760, 480);
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, UIConstants.SPACING_LG));
        root.setBackground(UIConstants.DARK_BG);
        root.setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG, UIConstants.SPACING_LG));

        JPanel header = new JPanel(new BorderLayout(0, UIConstants.SPACING_XS));
        header.setOpaque(false);
        header.add(UIConstants.createDarkHeadingLabel("Restore Data"), BorderLayout.NORTH);
        header.add(UIConstants.createDarkSecondaryLabel("Replaces all bookings, payments, accounts and vehicles with a backup. "
                + "The current data is backed up first."), BorderLayout.CENTER);
        header.add(UIConstants.createDarkMutedLabel("Backups folder: " + backupService.getBackupsDir().getAbsolutePath()),
                BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"Backup", "Made", "Size", "Kind"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = UIConstants.createDarkStyledTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(330);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        root.add(UIConstants.createDarkScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_SM, 0));
        buttons.setOpaque(false);
        JButton otherFile = UIConstants.createSecondaryButton("Choose Other File...");
        otherFile.addActionListener(e -> chooseFile());
        JButton close = UIConstants.createSecondaryButton("Close");
        close.addActionListener(e -> dispose());
        JButton restore = UIConstants.createDangerButton("Restore Selected");
        restore.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Choose a backup in the list first.", "Restore", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            restore(backups.get(table.convertRowIndexToModel(row)));
        });
        buttons.add(otherFile);
        buttons.add(close);
        buttons.add(restore);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadBackups() {
        backups = backupService.listBackups();
        model.setRowCount(0);
        for (File f : backups) {
            model.addRow(new Object[]{f.getName(), made(f), size(f),
                    f.getName().contains(BackupService.BEFORE_RESTORE_SUFFIX) ? "Saved before a restore" : "Backup"});
        }
        if (!backups.isEmpty()) table.setRowSelectionInterval(0, 0);
    }

    private static String made(File f) {
        return Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).format(WHEN);
    }

    private static String size(File f) {
        long kb = Math.max(1, (f.length() + 1023) / 1024);
        return kb < 1024 ? kb + " KB" : String.format("%.1f MB", kb / 1024.0);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(backupService.getBackupsDir());
        chooser.setDialogTitle("Choose a RentNGo Backup");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("RentNGo backups (*.zip)", "zip"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            restore(chooser.getSelectedFile());
        }
    }

    private void restore(File backup) {
        if (!WindowSession.requireAdmin(this, authService)) return;
        String message = "Restore the data from " + backup.getName() + " (made " + made(backup) + ")?\n\n"
                + "All bookings, payments, customers and vehicles changed since then\n"
                + "will be replaced by the backup.\n\n"
                + "The current data is backed up first, so this can be undone.\n"
                + "RentNGo will then restart, and you will need to log in again.";
        if (JOptionPane.showConfirmDialog(this, message, "Restore Data", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        BackupService.RestoreResult result;
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            result = backupService.restore(backup);
        } catch (ValidationException | IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Restore Failed", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
        JOptionPane.showMessageDialog(this, "The data was restored from " + result.getRestored().getName() + ".\n"
                        + "The data from before the restore is saved as " + result.getSavedBefore().getName() + ".\n\n"
                        + "RentNGo will now restart.",
                "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
        WindowSession.restartApp();
    }
}
