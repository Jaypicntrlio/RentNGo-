package gui;

import model.User;
import service.AuditLog;
import service.AuthService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Admin "Audit Log" view: every recorded action, newest first, read-only, with a search box. */
public class AuditLogPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLog auditLog;
    private final AuthService authService;

    private DefaultTableModel tableModel;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel countLabel;
    private JTextArea detailArea;

    public AuditLogPanel(AuditLog auditLog, AuthService authService) {
        this.auditLog = auditLog;
        this.authService = authService;
        setBackground(UIConstants.DARK_BG);
        buildUI();
        refresh();
    }

    private void buildUI() {
        setLayout(new BorderLayout(0, UIConstants.SPACING_MD));
        setBorder(new EmptyBorder(UIConstants.SPACING_LG, UIConstants.SPACING_XL, UIConstants.SPACING_XL, UIConstants.SPACING_XL));

        JPanel bar = new JPanel(new BorderLayout(UIConstants.SPACING_MD, 0));
        bar.setOpaque(false);
        searchField = UIConstants.createDarkTextField(28);
        searchField.putClientProperty("JTextField.placeholderText", "Search user, action, booking or details");
        searchField.setPreferredSize(new Dimension(340, UIConstants.INPUT_HEIGHT));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applySearch(); }
        });
        bar.add(searchField, BorderLayout.WEST);
        countLabel = UIConstants.createDarkSecondaryLabel(" ");
        bar.add(countLabel, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        String[] columns = {"Time", "User", "Action", "Booking", "Details"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false; // the log is read-only
            }
        };
        table = UIConstants.createDarkStyledTable(tableModel);
        // fixed widths for the short columns; Details takes the rest
        int[] widths = {150, 220, 170, 90};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setMinWidth(widths[i]);
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(4).setPreferredWidth(600);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        sorter = new TableRowSorter<>(tableModel);
        for (int i = 0; i < columns.length; i++) sorter.setSortable(i, false); // keep newest first
        table.setRowSorter(sorter);

        JScrollPane scroll = UIConstants.createDarkScrollPane(table);
        add(scroll, BorderLayout.CENTER);

        // the full details of the selected line (long ones are cut off in the table)
        detailArea = new JTextArea(3, 40);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(UIConstants.FONT_BODY);
        detailArea.setBackground(UIConstants.DARK_CARD);
        detailArea.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        detailArea.setBorder(new EmptyBorder(UIConstants.SPACING_SM, UIConstants.SPACING_MD, UIConstants.SPACING_SM, UIConstants.SPACING_MD));
        table.getSelectionModel().addListSelectionListener(e -> showSelectedDetails());

        JPanel south = new JPanel(new BorderLayout(0, UIConstants.SPACING_SM));
        south.setOpaque(false);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(new javax.swing.border.LineBorder(UIConstants.DARK_BORDER, 1));
        south.add(detailScroll, BorderLayout.CENTER);
        JLabel note = UIConstants.createDarkMutedLabel("Read-only. Lines are only ever added to "
                + (auditLog.getFile() != null ? auditLog.getFile().getName() : "the audit log") + "; nothing is changed or deleted.");
        south.add(note, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private void showSelectedDetails() {
        int row = table.getSelectedRow();
        if (row < 0) {
            detailArea.setText("Select a line to see its full details.");
            detailArea.setForeground(UIConstants.DARK_TEXT_MUTED);
            return;
        }
        int m = table.convertRowIndexToModel(row);
        detailArea.setText(tableModel.getValueAt(m, 0) + "  ·  " + tableModel.getValueAt(m, 1) + "  ·  "
                + tableModel.getValueAt(m, 2) + (tableModel.getValueAt(m, 3).toString().isEmpty() ? "" : "  ·  " + tableModel.getValueAt(m, 3))
                + "\n" + tableModel.getValueAt(m, 4));
        detailArea.setForeground(UIConstants.DARK_TEXT_PRIMARY);
        detailArea.setCaretPosition(0);
    }

    /** Reloads the log from disk. */
    public void refresh() {
        Map<String, String> names = new HashMap<>();
        for (User u : authService.getAllUsers()) names.put(u.getUserId(), u.getFullName());

        List<AuditLog.Entry> entries = auditLog.readNewestFirst();
        tableModel.setRowCount(0);
        for (AuditLog.Entry e : entries) {
            String user = e.getUserId().isEmpty() ? "—"
                    : names.containsKey(e.getUserId()) ? e.getUserId() + " · " + names.get(e.getUserId()) : e.getUserId();
            tableModel.addRow(new Object[]{e.getTimestamp().format(TIME), user, e.getAction(),
                    e.getBookingId().isEmpty() ? "" : e.getBookingId(), e.getDetails()});
        }
        applySearch();
    }

    private void applySearch() {
        String text = searchField.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        showSelectedDetails();
        int shown = table.getRowCount(), total = tableModel.getRowCount();
        countLabel.setText(shown == total ? total + (total == 1 ? " entry" : " entries") : "Showing " + shown + " of " + total);
    }
}
