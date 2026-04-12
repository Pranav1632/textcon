package com.textcon.ui;

import com.textcon.MarkdownConverter;
import com.textcon.db.HistoryDAO;
import com.textcon.model.ConversionRecord;
import com.textcon.util.FileExporter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextConverterFrame extends JFrame {
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*#{1,6}\\s+");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*[^*]+\\*\\*|__[^_]+__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*[^*]+\\*(?!\\*)|(?<!_)_[^_]+_(?!_)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Color APP_BG = new Color(16, 20, 28);
    private static final Color PANEL_BG = new Color(24, 30, 42);
    private static final Color INPUT_BG = new Color(22, 28, 39);
    private static final Color OUTPUT_BG = new Color(20, 33, 46);
    private static final Color TEXT_PRIMARY = new Color(229, 236, 249);
    private static final Color TEXT_MUTED = new Color(163, 176, 200);
    private static final Color ACCENT = new Color(59, 146, 255);
    private static final Color BORDER = new Color(58, 72, 98);

    private final JTextArea inputArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();
    private final JComboBox<String> formatCombo = new JComboBox<>(new String[]{"WhatsApp", "Telegram", "Discord", "Slack", "PDF"});
    private final JLabel inputCountLabel = new JLabel("Chars: 0 | Words: 0");
    private final JLabel outputCountLabel = new JLabel("Chars: 0 | Words: 0");
    private final JLabel tagSummaryLabel = new JLabel("Found: 0 headings, 0 bold, 0 italic, 0 code blocks");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel savedLocationLabel = new JLabel("Last saved: -");

    private final MarkdownConverter converter = new MarkdownConverter();
    private final HistoryDAO historyDAO = new HistoryDAO();
    private final FileExporter exporter = new FileExporter();
    private final UndoManager undoManager = new UndoManager();
    private final Timer debounceTimer;

    private boolean quickCopyEnabled;

    public TextConverterFrame() {
        super("Markdown Text Converter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(1080, 680));
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);

        debounceTimer = new Timer(300, e -> performConversion(false));
        debounceTimer.setRepeats(false);

        initComponents();
        updateCountsAndTags();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(APP_BG);
        setJMenuBar(buildMenuBar());

        Font editorFont = pickEditorFont();
        inputArea.setFont(editorFont);
        outputArea.setFont(editorFont);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        inputArea.setMargin(new Insets(10, 12, 10, 12));
        outputArea.setMargin(new Insets(10, 12, 10, 12));
        inputArea.setBackground(INPUT_BG);
        outputArea.setBackground(OUTPUT_BG);
        inputArea.setForeground(TEXT_PRIMARY);
        outputArea.setForeground(TEXT_PRIMARY);
        inputArea.setCaretColor(ACCENT);
        outputArea.setCaretColor(ACCENT);
        inputArea.setSelectionColor(new Color(58, 118, 214));
        outputArea.setSelectionColor(new Color(58, 118, 214));
        inputArea.setSelectedTextColor(new Color(243, 248, 255));
        outputArea.setSelectedTextColor(new Color(243, 248, 255));
        outputArea.setEditable(false);

        inputArea.getDocument().addUndoableEditListener(undoManager);
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onInputChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onInputChanged();
            }
        });
        formatCombo.addActionListener(e -> performConversion(false));
        styleComboBox(formatCombo);

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 8));
        topBar.setBackground(APP_BG);
        topBar.add(new JLabel("Target Format:"));
        topBar.add(formatCombo);
        topBar.add(tagSummaryLabel);
        setMutedText(tagSummaryLabel);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                createTextPanel("Markdown Input", inputArea, inputCountLabel),
                createTextPanel("Converted Output", outputArea, outputCountLabel)
        );
        splitPane.setResizeWeight(0.5);
        splitPane.setBackground(APP_BG);
        splitPane.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(APP_BG);
        bottomBar.add(buildButtonBar(), BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        statusPanel.setBackground(APP_BG);
        statusPanel.add(statusLabel);
        statusPanel.add(savedLocationLabel);
        setMutedText(savedLocationLabel);
        bottomBar.add(statusPanel, BorderLayout.SOUTH);

        add(topBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);

        bindShortcuts();
    }

    private JPanel createTextPanel(String title, JTextArea area, JLabel countLabel) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(PANEL_BG);
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                new LineBorder(BORDER, 1, true),
                title
        );
        titledBorder.setTitleColor(new Color(199, 212, 235));
        titledBorder.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.setBorder(titledBorder);

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        scrollPane.getViewport().setBackground(area.getBackground());
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel counterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        counterPanel.setBackground(PANEL_BG);
        setMutedText(countLabel);
        counterPanel.add(countLabel);
        panel.add(counterPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildButtonBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        panel.setBackground(APP_BG);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JPanel centerAction = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        leftActions.setBackground(APP_BG);
        centerAction.setBackground(APP_BG);
        rightActions.setBackground(APP_BG);

        JButton convertButton = new JButton("Convert");
        JButton copyButton = new JButton("Copy");
        JButton clearButton = new JButton("Clear");
        JButton saveButton = new JButton("Save");
        JButton useOutputButton = new JButton("Use Output");
        JButton historyButton = new JButton("History");

        convertButton.addActionListener(e -> performConversion(true));
        copyButton.addActionListener(e -> copyOutputToClipboard());
        clearButton.addActionListener(e -> clearAll());
        saveButton.addActionListener(e -> saveOutput());
        useOutputButton.addActionListener(e -> loadOutputAsInput());
        historyButton.addActionListener(e -> openHistoryDialog());

        stylePrimaryConvertButton(convertButton);
        styleActionButton(copyButton);
        styleActionButton(clearButton);
        styleActionButton(saveButton);
        styleActionButton(useOutputButton);
        styleActionButton(historyButton);

        leftActions.add(copyButton);
        leftActions.add(clearButton);
        leftActions.add(saveButton);
        leftActions.add(useOutputButton);

        centerAction.add(convertButton);

        rightActions.add(historyButton);

        panel.add(leftActions, BorderLayout.WEST);
        panel.add(centerAction, BorderLayout.CENTER);
        panel.add(rightActions, BorderLayout.EAST);

        return panel;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(APP_BG);
        menuBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem saveItem = new JMenuItem("Save");
        JMenuItem exitItem = new JMenuItem("Exit");

        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        openItem.addActionListener(e -> openInputFile());
        saveItem.addActionListener(e -> saveOutput());
        exitItem.addActionListener(e -> dispose());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem historyItem = new JMenuItem("History");
        historyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
        historyItem.addActionListener(e -> openHistoryDialog());
        viewMenu.add(historyItem);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        return menuBar;
    }

    private void bindShortcuts() {
        bindAction("convert", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                performConversion(true);
            }
        });

        bindAction("copy-output", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (quickCopyEnabled && !outputArea.getText().isBlank()) {
                    copyOutputToClipboard();
                }
            }
        });

        bindAction("history", KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openHistoryDialog();
            }
        });

        bindAction("undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    if (undoManager.canUndo()) {
                        undoManager.undo();
                        updateStatus("Undo");
                    }
                } catch (CannotUndoException ignored) {
                    updateStatus("Nothing to undo");
                }
            }
        });

        bindAction("redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    if (undoManager.canRedo()) {
                        undoManager.redo();
                        updateStatus("Redo");
                    }
                } catch (CannotRedoException ignored) {
                    updateStatus("Nothing to redo");
                }
            }
        });
    }

    private void bindAction(String name, KeyStroke keyStroke, Action action) {
        InputMap map = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        map.put(keyStroke, name);
        getRootPane().getActionMap().put(name, action);
    }

    private void onInputChanged() {
        updateCountsAndTags();
        debounceTimer.restart();
    }

    private void performConversion(boolean saveToHistory) {
        try {
            String original = inputArea.getText();
            String type = String.valueOf(formatCombo.getSelectedItem());
            String converted = convertByType(type, original);
            outputArea.setText(converted);

            updateCountsAndTags();
            quickCopyEnabled = !converted.isBlank();

            if (saveToHistory) {
                historyDAO.insertConversion(original, converted, type);
                updateStatus("Converted to " + type + " and saved to history OK");
            } else {
                updateStatus("Preview updated (" + type + ")");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Conversion Error", JOptionPane.ERROR_MESSAGE);
            updateStatus("Conversion failed");
        }
    }

    private String convertByType(String type, String input) {
        if ("PDF".equals(type)) {
            return converter.toPdf(input);
        }
        if ("Telegram".equals(type)) {
            return converter.toTelegram(input);
        }
        if ("Discord".equals(type)) {
            return converter.toDiscord(input);
        }
        if ("Slack".equals(type)) {
            return converter.toSlack(input);
        }
        return converter.toWhatsApp(input);
    }

    private void copyOutputToClipboard() {
        String text = outputArea.getText();
        if (text == null || text.isBlank()) {
            updateStatus("No output to copy");
            return;
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        updateStatus("Output copied to clipboard");
    }

    private void clearAll() {
        inputArea.setText("");
        outputArea.setText("");
        quickCopyEnabled = false;
        updateCountsAndTags();
        updateStatus("Cleared input and output");
    }

    private void openInputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Markdown/Text File");
        chooser.setFileFilter(new FileNameExtensionFilter("Text/Markdown", "txt", "md", "markdown"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                inputArea.setText(text);
                performConversion(false);
                updateStatus("Loaded file: " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Open Failed", JOptionPane.ERROR_MESSAGE);
                updateStatus("Failed to open file");
            }
        }
    }

    private void saveOutput() {
        String convertedText = outputArea.getText();
        String markdownText = inputArea.getText();
        if (convertedText == null || convertedText.isBlank()) {
            updateStatus("No output to save");
            return;
        }
        String structuredSource = (markdownText == null || markdownText.isBlank()) ? convertedText : markdownText;

        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File (*.txt)", "txt");
        FileNameExtensionFilter taggedTxtFilter = new FileNameExtensionFilter("Tagged Text File (*.txt)", "txt");
        FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF File (*.pdf)", "pdf");

        chooser.setDialogTitle("Export Output");
        chooser.addChoosableFileFilter(txtFilter);
        chooser.addChoosableFileFilter(taggedTxtFilter);
        chooser.addChoosableFileFilter(pdfFilter);
        chooser.setFileFilter(txtFilter);

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            FileNameExtensionFilter chosenFilter = (FileNameExtensionFilter) chooser.getFileFilter();

            try {
                if (chosenFilter == pdfFilter) {
                    File target = ensureExtension(selected, ".pdf");
                    exporter.exportPDF(structuredSource, target);
                    historyDAO.insertConversion(markdownText, convertedText, String.valueOf(formatCombo.getSelectedItem()), target.getAbsolutePath(), "PDF");
                    savedLocationLabel.setText("Last saved: " + target.getAbsolutePath());
                    updateStatus("Saved PDF with heading/code tags: " + target.getName());
                } else if (chosenFilter == taggedTxtFilter) {
                    File target = ensureExtension(selected, ".txt");
                    exporter.exportTaggedTXT(structuredSource, target);
                    historyDAO.insertConversion(markdownText, convertedText, String.valueOf(formatCombo.getSelectedItem()), target.getAbsolutePath(), "TAGGED_TXT");
                    savedLocationLabel.setText("Last saved: " + target.getAbsolutePath());
                    updateStatus("Saved tagged TXT (H/CODE/LI/P): " + target.getName());
                } else {
                    File target = ensureExtension(selected, ".txt");
                    exporter.exportTXT(convertedText, target);
                    historyDAO.insertConversion(markdownText, convertedText, String.valueOf(formatCombo.getSelectedItem()), target.getAbsolutePath(), "TXT");
                    savedLocationLabel.setText("Last saved: " + target.getAbsolutePath());
                    updateStatus("Saved TXT: " + target.getName());
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Save Failed", JOptionPane.ERROR_MESSAGE);
                updateStatus("Save failed");
            }
        }
    }

    private File ensureExtension(File file, String extension) {
        String path = file.getAbsolutePath();
        if (path.toLowerCase().endsWith(extension)) {
            return file;
        }
        return new File(path + extension);
    }

    private void openHistoryDialog() {
        HistoryDialog dialog = new HistoryDialog(this, historyDAO, this::loadFromHistory);
        dialog.setVisible(true);
    }

    private void loadFromHistory(ConversionRecord record) {
        inputArea.setText(record.getOriginalText());
        outputArea.setText(record.getConvertedText());
        if (record.getConversionType() != null) {
            formatCombo.setSelectedItem(record.getConversionType());
        }
        quickCopyEnabled = record.getConvertedText() != null && !record.getConvertedText().isBlank();
        if (record.getExportPath() != null && !record.getExportPath().isBlank()) {
            savedLocationLabel.setText("Last saved: " + record.getExportPath());
        }
        updateCountsAndTags();
        updateStatus("Loaded conversion #" + record.getId());
    }

    private void updateCountsAndTags() {
        String input = inputArea.getText();
        String output = outputArea.getText();

        inputCountLabel.setText("Chars: " + lengthOf(input) + " | Words: " + countWords(input));
        outputCountLabel.setText("Chars: " + lengthOf(output) + " | Words: " + countWords(output));

        int headings = countMatches(HEADING_PATTERN, input);
        int bold = countMatches(BOLD_PATTERN, input);
        int italic = countMatches(ITALIC_PATTERN, input);
        int codeBlocks = countMatches(CODE_BLOCK_PATTERN, input);

        tagSummaryLabel.setText(
                "Found: " + headings + " headings, " + bold + " bold, " + italic + " italic, " + codeBlocks + " code blocks"
        );
    }

    private int countMatches(Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }

    private void stylePrimaryConvertButton(JButton convertButton) {
        convertButton.setPreferredSize(new Dimension(188, 44));
        convertButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        convertButton.setForeground(Color.WHITE);
        convertButton.setBackground(new Color(38, 128, 255));
        convertButton.setOpaque(true);
        convertButton.setBorder(BorderFactory.createLineBorder(new Color(71, 154, 255), 1, true));
        convertButton.setFocusPainted(false);
    }

    private void styleActionButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setForeground(TEXT_PRIMARY);
        button.setBackground(new Color(34, 44, 62));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(new Color(70, 85, 116), 1, true));
        button.setFocusPainted(false);
    }

    private void styleComboBox(JComboBox<String> comboBox) {
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        comboBox.setBackground(new Color(29, 39, 56));
        comboBox.setForeground(TEXT_PRIMARY);
        comboBox.setFocusable(false);
    }

    private void setMutedText(JLabel label) {
        label.setForeground(TEXT_MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
    }

    private Font pickEditorFont() {
        String[] preferred = {"Segoe UI Emoji", "Segoe UI Variable", "Segoe UI", "Noto Color Emoji", "Dialog"};
        String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String preferredName : preferred) {
            for (String availableName : available) {
                if (availableName.equalsIgnoreCase(preferredName)) {
                    return new Font(availableName, Font.PLAIN, 15);
                }
            }
        }
        return new Font("Dialog", Font.PLAIN, 15);
    }

    private void loadOutputAsInput() {
        String text = outputArea.getText();
        if (text == null || text.isBlank()) {
            updateStatus("No converted output to reuse");
            return;
        }
        inputArea.setText(text);
        inputArea.requestFocusInWindow();
        inputArea.setCaretPosition(inputArea.getText().length());
        updateStatus("Converted output copied back into input");
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
}
