package com.textcon.ui;

import com.textcon.db.HistoryDAO;
import com.textcon.model.ConversionRecord;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HistoryDialog extends JDialog {
    public interface HistorySelectionListener {
        void onHistorySelected(ConversionRecord record);
    }

    private final HistoryDAO historyDAO;
    private final HistorySelectionListener selectionListener;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private JButton openSavedButton;
    private JButton deleteButton;
    private JButton clearAllButton;
    private List<ConversionRecord> rows = new ArrayList<>();
    private static final Color PANEL_BG = new Color(24, 30, 42);
    private static final Color TABLE_BG = new Color(21, 27, 38);
    private static final Color TEXT_PRIMARY = new Color(229, 236, 249);
    private static final Color TEXT_MUTED = new Color(163, 176, 200);
    private static final Color SELECTION_BG = new Color(58, 118, 214);

    public HistoryDialog(JFrame owner, HistoryDAO historyDAO, HistorySelectionListener selectionListener) {
        super(owner, "Conversion History", true);
        this.historyDAO = historyDAO;
        this.selectionListener = selectionListener;

        tableModel = new DefaultTableModel(new Object[]{"#", "Type", "Saved As", "Saved Path", "Preview", "Date/Time"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setBackground(TABLE_BG);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(new Color(60, 73, 100));
        table.getTableHeader().setBackground(PANEL_BG);
        table.getTableHeader().setForeground(TEXT_MUTED);
        table.setSelectionBackground(SELECTION_BG);
        table.setSelectionForeground(new Color(243, 248, 255));

        initUI();
        loadRows();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(PANEL_BG);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    int index = table.getSelectedRow();
                    ConversionRecord record = rows.get(index);
                    if (selectionListener != null) {
                        selectionListener.onHistorySelected(record);
                    }
                    dispose();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        scrollPane.getViewport().setBackground(TABLE_BG);
        add(scrollPane, BorderLayout.CENTER);
        add(buildActionPanel(), BorderLayout.SOUTH);
        configureColumnWidths();

        setPreferredSize(new Dimension(980, 380));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        openSavedButton = new JButton("Open Saved File");
        deleteButton = new JButton("Delete");
        clearAllButton = new JButton("Clear All");
        JButton closeButton = new JButton("Close");

        openSavedButton.addActionListener(e -> openSavedFile());
        deleteButton.addActionListener(e -> deleteSelected());
        clearAllButton.addActionListener(e -> clearAllRows());
        closeButton.addActionListener(e -> dispose());

        panel.add(openSavedButton);
        panel.add(deleteButton);
        panel.add(clearAllButton);
        panel.add(closeButton);
        return panel;
    }

    private void deleteSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ConversionRecord record = rows.get(selected);
        setBusy(true, "Deleting...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                historyDAO.deleteById(record.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRows();
                } catch (Exception ex) {
                    setBusy(false, "Delete failed");
                    JOptionPane.showMessageDialog(
                            HistoryDialog.this,
                            rootMessage(ex),
                            "Delete Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void clearAllRows() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete all conversion history?",
                "Confirm Clear All",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            setBusy(true, "Clearing...");
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    historyDAO.clearAll();
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        loadRows();
                    } catch (Exception ex) {
                        setBusy(false, "Clear failed");
                        JOptionPane.showMessageDialog(
                                HistoryDialog.this,
                                rootMessage(ex),
                                "Clear Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }.execute();
        }
    }

    private void loadRows() {
        setBusy(true, "Loading history...");
        new SwingWorker<List<ConversionRecord>, Void>() {
            @Override
            protected List<ConversionRecord> doInBackground() {
                return historyDAO.getAll();
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    tableModel.setRowCount(0);
                    for (ConversionRecord row : rows) {
                        tableModel.addRow(new Object[]{
                                row.getId(),
                                row.getConversionType(),
                                row.getExportFormat() == null ? "-" : row.getExportFormat(),
                                row.getExportPath() == null ? "-" : row.getExportPath(),
                                preview(row.getConvertedText()),
                                row.getCreatedAt()
                        });
                    }
                    setBusy(false, "Conversion History");
                } catch (Exception ex) {
                    setBusy(false, "History load failed");
                    JOptionPane.showMessageDialog(
                            HistoryDialog.this,
                            rootMessage(ex),
                            "History Load Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void openSavedFile() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ConversionRecord record = rows.get(selected);
        String exportPath = record.getExportPath();
        if (exportPath == null || exportPath.isBlank()) {
            JOptionPane.showMessageDialog(this, "This history item has no saved file.", "No Saved File", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File file = new File(exportPath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "Saved file no longer exists:\n" + exportPath, "Missing File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "Desktop open is not supported on this system.", "Unsupported", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open file:\n" + ex.getMessage(), "Open Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void configureColumnWidths() {
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(95);
        table.getColumnModel().getColumn(3).setPreferredWidth(350);
        table.getColumnModel().getColumn(4).setPreferredWidth(240);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
    }

    private String preview(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= 40) {
            return clean;
        }
        return clean.substring(0, 40) + "...";
    }

    private void setBusy(boolean busy, String title) {
        table.setEnabled(!busy);
        if (openSavedButton != null) {
            openSavedButton.setEnabled(!busy);
        }
        if (deleteButton != null) {
            deleteButton.setEnabled(!busy);
        }
        if (clearAllButton != null) {
            clearAllButton.setEnabled(!busy);
        }
        setTitle(title);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.toString();
        }
        return message;
    }
}
