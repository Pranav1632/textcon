package com.textcon.ui;

import com.textcon.MarkdownConverter;
import com.textcon.db.HistoryDAO;
import com.textcon.model.ConversionRecord;
import com.textcon.util.FileExporter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextConverterFrame extends JFrame {
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*#{1,6}\\s+");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*[^*]+\\*\\*|__[^_]+__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*[^*]+\\*(?!\\*)|(?<!_)_[^_]+_(?!_)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");

    private enum UiTheme {
        DARK("Dark", new Color(16, 20, 28), new Color(24, 30, 42), new Color(22, 28, 39), new Color(20, 33, 46),
                new Color(229, 236, 249), new Color(163, 176, 200), new Color(59, 146, 255), new Color(58, 72, 98),
                new Color(34, 44, 62), new Color(70, 85, 116)),
        LIGHT("Light", new Color(242, 245, 251), new Color(255, 255, 255), new Color(255, 255, 255), new Color(248, 251, 255),
                new Color(22, 28, 40), new Color(98, 110, 130), new Color(43, 126, 228), new Color(188, 198, 216),
                new Color(240, 245, 252), new Color(177, 191, 214)),
        STEEL("Steel", new Color(31, 35, 44), new Color(41, 47, 59), new Color(37, 43, 54), new Color(35, 46, 61),
                new Color(228, 235, 246), new Color(170, 182, 201), new Color(80, 158, 255), new Color(84, 97, 124),
                new Color(52, 61, 79), new Color(88, 106, 141));

        private final String label;
        private final Color appBg;
        private final Color panelBg;
        private final Color inputBg;
        private final Color outputBg;
        private final Color textPrimary;
        private final Color textMuted;
        private final Color accent;
        private final Color border;
        private final Color buttonBg;
        private final Color buttonBorder;

        UiTheme(String label, Color appBg, Color panelBg, Color inputBg, Color outputBg, Color textPrimary, Color textMuted,
                Color accent, Color border, Color buttonBg, Color buttonBorder) {
            this.label = label;
            this.appBg = appBg;
            this.panelBg = panelBg;
            this.inputBg = inputBg;
            this.outputBg = outputBg;
            this.textPrimary = textPrimary;
            this.textMuted = textMuted;
            this.accent = accent;
            this.border = border;
            this.buttonBg = buttonBg;
            this.buttonBorder = buttonBorder;
        }

        private static UiTheme fromLabel(String label) {
            for (UiTheme theme : values()) {
                if (theme.label.equalsIgnoreCase(label)) {
                    return theme;
                }
            }
            return DARK;
        }
    }

    private final JTextArea inputArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();
    private final JComboBox<String> formatCombo = new JComboBox<>(new String[]{"WhatsApp", "Telegram", "Discord", "Slack", "PDF"});
    private final JComboBox<String> exportThemeCombo = new JComboBox<>(new String[]{"Blue", "Classic", "Dark"});
    private final JLabel inputCountLabel = new JLabel("Chars: 0 | Words: 0");
    private final JLabel outputCountLabel = new JLabel("Chars: 0 | Words: 0");
    private final JLabel tagSummaryLabel = new JLabel("Found: 0 headings, 0 bold, 0 italic, 0 code blocks");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel savedLocationLabel = new JLabel("Last saved: -");
    private final JProgressBar busyProgressBar = new JProgressBar();

    private final MarkdownConverter converter = new MarkdownConverter();
    private final HistoryDAO historyDAO = new HistoryDAO();
    private final FileExporter exporter = new FileExporter();
    private final AppPreferences appPreferences = new AppPreferences();
    private final UndoManager undoManager = new UndoManager();
    private final Timer debounceTimer;

    private final List<JButton> actionButtons = new ArrayList<>();

    private JPanel topBar;
    private JPanel buttonBar;
    private JPanel bottomBar;
    private JPanel statusPanel;
    private JPanel inputPanel;
    private JPanel outputPanel;
    private JPanel inputCounterPanel;
    private JPanel outputCounterPanel;
    private JPanel leftActionsPanel;
    private JPanel rightActionsPanel;

    private boolean quickCopyEnabled;
    private boolean saveHistoryOnCopy = true;
    private boolean outputWrapEnabled = true;
    private UiTheme currentUiTheme = UiTheme.DARK;
    private File lastSaveDirectory = new File(System.getProperty("user.home"));
    private int previewDelayMs = 300;
    private String defaultExportTheme = "Blue";
    private SwingWorker<ConversionResult, Void> conversionWorker;
    private long conversionRequestId;
    private int busyTaskCount;

    public TextConverterFrame() {
        super("Markdown Text Converter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(new Dimension(1180, 720));
        setMinimumSize(new Dimension(940, 600));
        setLocationRelativeTo(null);

        loadStoredSettings();

        debounceTimer = new Timer(previewDelayMs, e -> performConversion());
        debounceTimer.setRepeats(false);

        initComponents();
        updateCountsAndTags();
        performConversion();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        exportThemeCombo.setSelectedItem(defaultExportTheme);
        setJMenuBar(buildMenuBar());

        Font editorFont = pickEditorFont();
        inputArea.setFont(editorFont);
        outputArea.setFont(editorFont);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        outputArea.setLineWrap(outputWrapEnabled);
        outputArea.setWrapStyleWord(outputWrapEnabled);
        inputArea.setMargin(new Insets(10, 12, 10, 12));
        outputArea.setMargin(new Insets(10, 12, 10, 12));
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
        formatCombo.addActionListener(e -> performConversion());
        exportThemeCombo.addActionListener(e -> {
            String selectedTheme = String.valueOf(exportThemeCombo.getSelectedItem());
            appPreferences.setExportThemeLabel(selectedTheme);
            updateStatus("Export theme: " + selectedTheme);
        });

        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 8));
        topBar.add(new JLabel("Target Format:"));
        topBar.add(formatCombo);
        topBar.add(new JLabel("Export Theme:"));
        topBar.add(exportThemeCombo);
        topBar.add(tagSummaryLabel);

        inputPanel = createTextPanel("Markdown Input", inputArea, inputCountLabel, true);
        outputPanel = createTextPanel("Converted Output", outputArea, outputCountLabel, false);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        bottomBar = new JPanel(new BorderLayout());
        buttonBar = buildButtonBar();
        bottomBar.add(buttonBar, BorderLayout.NORTH);

        statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        statusPanel.add(statusLabel);
        busyProgressBar.setIndeterminate(true);
        busyProgressBar.setVisible(false);
        busyProgressBar.setPreferredSize(new Dimension(92, 12));
        statusPanel.add(busyProgressBar);
        statusPanel.add(savedLocationLabel);
        bottomBar.add(statusPanel, BorderLayout.SOUTH);

        add(topBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);

        bindShortcuts();
        applyUiTheme(currentUiTheme);
    }

    private JPanel createTextPanel(String title, JTextArea area, JLabel countLabel, boolean isInputPanel) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(createPanelBorder(title, currentUiTheme));

        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createLineBorder(currentUiTheme.border, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel counterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        counterPanel.add(countLabel);
        panel.add(counterPanel, BorderLayout.SOUTH);

        if (isInputPanel) {
            inputCounterPanel = counterPanel;
        } else {
            outputCounterPanel = counterPanel;
        }

        return panel;
    }

    private JPanel buildButtonBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));

        leftActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        rightActionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        JButton copyButton = new JButton("Copy");
        JButton clearButton = new JButton("Clear");
        JButton saveButton = new JButton("Save As");
        JButton savePdfButton = new JButton("Save As PDF");
        JButton saveWordButton = new JButton("Save As Word");
        JButton saveHtmlButton = new JButton("Save As HTML");
        JButton useOutputButton = new JButton("Use Output");
        JButton historyButton = new JButton("History");

        copyButton.addActionListener(e -> copyOutputToClipboard());
        clearButton.addActionListener(e -> clearAll());
        saveButton.addActionListener(e -> saveOutputAsDialog(null));
        savePdfButton.addActionListener(e -> saveOutputAsDialog("PDF"));
        saveWordButton.addActionListener(e -> saveOutputAsDialog("DOCX"));
        saveHtmlButton.addActionListener(e -> saveOutputAsDialog("HTML"));
        useOutputButton.addActionListener(e -> loadOutputAsInput());
        historyButton.addActionListener(e -> openHistoryDialog());

        addActionButton(copyButton);
        addActionButton(clearButton);
        addActionButton(saveButton);
        addActionButton(savePdfButton);
        addActionButton(saveWordButton);
        addActionButton(saveHtmlButton);
        addActionButton(useOutputButton);
        addActionButton(historyButton);

        leftActionsPanel.add(copyButton);
        leftActionsPanel.add(clearButton);
        leftActionsPanel.add(saveButton);
        leftActionsPanel.add(savePdfButton);
        leftActionsPanel.add(saveWordButton);
        leftActionsPanel.add(saveHtmlButton);
        leftActionsPanel.add(useOutputButton);
        rightActionsPanel.add(historyButton);

        panel.add(leftActionsPanel, BorderLayout.WEST);
        panel.add(rightActionsPanel, BorderLayout.EAST);

        return panel;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem saveItem = new JMenuItem("Save As");
        JMenuItem savePdfItem = new JMenuItem("Save As PDF");
        JMenuItem saveWordItem = new JMenuItem("Save As Word");
        JMenuItem saveHtmlItem = new JMenuItem("Save As HTML");
        JMenuItem exitItem = new JMenuItem("Exit");

        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        openItem.addActionListener(e -> openInputFile());
        saveItem.addActionListener(e -> saveOutputAsDialog(null));
        savePdfItem.addActionListener(e -> saveOutputAsDialog("PDF"));
        saveWordItem.addActionListener(e -> saveOutputAsDialog("DOCX"));
        saveHtmlItem.addActionListener(e -> saveOutputAsDialog("HTML"));
        exitItem.addActionListener(e -> dispose());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(savePdfItem);
        fileMenu.add(saveWordItem);
        fileMenu.add(saveHtmlItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem historyItem = new JMenuItem("History");
        historyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
        historyItem.addActionListener(e -> openHistoryDialog());
        viewMenu.add(historyItem);

        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem saveToHistoryItem = new JMenuItem("Save Preview To History");
        JMenuItem openLastFolderItem = new JMenuItem("Open Last Save Folder");
        JMenuItem refreshItem = new JMenuItem("Refresh Preview");
        saveToHistoryItem.addActionListener(e -> saveCurrentConversionToHistory("Preview saved to history"));
        openLastFolderItem.addActionListener(e -> openLastSaveFolder());
        refreshItem.addActionListener(e -> performConversion());
        toolsMenu.add(saveToHistoryItem);
        toolsMenu.add(openLastFolderItem);
        toolsMenu.add(refreshItem);

        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem preferencesItem = new JMenuItem("Preferences...");
        preferencesItem.addActionListener(e -> openSettingsDialog());
        settingsMenu.add(preferencesItem);
        settingsMenu.addSeparator();

        JMenu uiThemeMenu = new JMenu("App Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        for (UiTheme theme : UiTheme.values()) {
            JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(theme.label, theme == currentUiTheme);
            themeItem.addActionListener(e -> {
                currentUiTheme = theme;
                applyUiTheme(theme);
                appPreferences.setUiThemeLabel(theme.label);
                updateStatus("App theme changed to " + theme.label);
            });
            themeGroup.add(themeItem);
            uiThemeMenu.add(themeItem);
        }
        settingsMenu.add(uiThemeMenu);

        JCheckBoxMenuItem historyOnCopyItem = new JCheckBoxMenuItem("Save Copied Output To History", saveHistoryOnCopy);
        historyOnCopyItem.addActionListener(e -> {
            saveHistoryOnCopy = historyOnCopyItem.isSelected();
            appPreferences.setSaveHistoryOnCopy(saveHistoryOnCopy);
            updateStatus(saveHistoryOnCopy ? "History on copy enabled" : "History on copy disabled");
        });
        settingsMenu.add(historyOnCopyItem);

        JMenu exportThemeMenu = new JMenu("Default Export Theme");
        ButtonGroup exportThemeGroup = new ButtonGroup();
        for (String label : new String[]{"Blue", "Classic", "Dark"}) {
            JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(label, label.equals(exportThemeCombo.getSelectedItem()));
            themeItem.addActionListener(e -> exportThemeCombo.setSelectedItem(label));
            exportThemeGroup.add(themeItem);
            exportThemeMenu.add(themeItem);
        }
        settingsMenu.add(exportThemeMenu);

        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        menuBar.add(toolsMenu);
        menuBar.add(settingsMenu);
        return menuBar;
    }

    private void bindShortcuts() {
        bindAction("save-as", KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveOutputAsDialog(null);
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

    private void performConversion() {
        String original = inputArea.getText();
        String type = String.valueOf(formatCombo.getSelectedItem());
        long requestId = ++conversionRequestId;

        if (conversionWorker != null && !conversionWorker.isDone()) {
            conversionWorker.cancel(true);
        }

        beginBusy("Updating preview...");
        conversionWorker = new SwingWorker<>() {
            @Override
            protected ConversionResult doInBackground() {
                String converted = convertByType(type, original);
                return new ConversionResult(type, converted);
            }

            @Override
            protected void done() {
                try {
                    if (requestId != conversionRequestId) {
                        return;
                    }
                    ConversionResult result = get();
                    outputArea.setText(result.convertedText);
                    updateCountsAndTags();
                    quickCopyEnabled = !result.convertedText.isBlank();
                    updateStatus("Preview updated (" + result.conversionType + ")");
                } catch (CancellationException ignored) {
                    // Ignore canceled preview runs when newer input arrives.
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            TextConverterFrame.this,
                            rootMessage(ex),
                            "Conversion Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    updateStatus("Conversion failed");
                } finally {
                    endBusy();
                }
            }
        };
        conversionWorker.execute();
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
        if (saveHistoryOnCopy) {
            saveCurrentConversionToHistory("Output copied and saved to history");
        } else {
            updateStatus("Output copied to clipboard");
        }
    }

    private void saveCurrentConversionToHistory(String successMessage) {
        String original = inputArea.getText();
        String converted = outputArea.getText();
        if (converted == null || converted.isBlank()) {
            updateStatus("No output available for history");
            return;
        }
        String conversionType = String.valueOf(formatCombo.getSelectedItem());
        beginBusy("Saving history...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                historyDAO.insertConversion(original, converted, conversionType);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    updateStatus(successMessage);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            TextConverterFrame.this,
                            rootMessage(ex),
                            "History Save Failed",
                            JOptionPane.ERROR_MESSAGE
                    );
                    updateStatus("Failed to save history");
                } finally {
                    endBusy();
                }
            }
        }.execute();
    }

    private void clearAll() {
        inputArea.setText("");
        outputArea.setText("");
        quickCopyEnabled = false;
        updateCountsAndTags();
        updateStatus("Cleared input and output");
    }

    private void openInputFile() {
        JFileChooser chooser = new JFileChooser(lastSaveDirectory);
        chooser.setDialogTitle("Open Markdown/Text File");
        chooser.setFileFilter(new FileNameExtensionFilter("Text/Markdown", "txt", "md", "markdown"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.getParentFile() != null) {
                lastSaveDirectory = file.getParentFile();
                appPreferences.setLastSaveDirectory(lastSaveDirectory.getAbsolutePath());
            }

            beginBusy("Loading file...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws IOException {
                    return Files.readString(file.toPath(), StandardCharsets.UTF_8);
                }

                @Override
                protected void done() {
                    try {
                        String text = get();
                        inputArea.setText(text);
                        performConversion();
                        updateStatus("Loaded file: " + file.getName());
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                                TextConverterFrame.this,
                                rootMessage(ex),
                                "Open Failed",
                                    JOptionPane.ERROR_MESSAGE
                        );
                        updateStatus("Failed to open file");
                    } finally {
                        endBusy();
                    }
                }
            }.execute();
        }
    }

    private void saveOutputAsDialog(String preferredFormat) {
        String convertedText = outputArea.getText();
        String markdownText = inputArea.getText();
        if (convertedText == null || convertedText.isBlank()) {
            updateStatus("No output to save");
            return;
        }
        String structuredSource = (markdownText == null || markdownText.isBlank()) ? convertedText : markdownText;

        JFileChooser chooser = new JFileChooser(lastSaveDirectory);
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File (*.txt)", "txt");
        FileNameExtensionFilter taggedTxtFilter = new FileNameExtensionFilter("Tagged Text File (*.txt)", "txt");
        FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF File (*.pdf)", "pdf");
        FileNameExtensionFilter wordFilter = new FileNameExtensionFilter("Word File (*.docx)", "docx");
        FileNameExtensionFilter htmlFilter = new FileNameExtensionFilter("HTML File (*.html)", "html");
        FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("JSON File (*.json)", "json");
        FileNameExtensionFilter rtfFilter = new FileNameExtensionFilter("RTF File (*.rtf)", "rtf");
        FileNameExtensionFilter mdFilter = new FileNameExtensionFilter("Markdown File (*.md)", "md");

        chooser.setDialogTitle("Save As");
        chooser.addChoosableFileFilter(txtFilter);
        chooser.addChoosableFileFilter(taggedTxtFilter);
        chooser.addChoosableFileFilter(pdfFilter);
        chooser.addChoosableFileFilter(wordFilter);
        chooser.addChoosableFileFilter(htmlFilter);
        chooser.addChoosableFileFilter(jsonFilter);
        chooser.addChoosableFileFilter(rtfFilter);
        chooser.addChoosableFileFilter(mdFilter);
        chooser.setFileFilter(resolvePreferredFilter(
                preferredFormat, txtFilter, taggedTxtFilter, pdfFilter, wordFilter, htmlFilter, jsonFilter, rtfFilter, mdFilter
        ));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            FileNameExtensionFilter chosenFilter = (FileNameExtensionFilter) chooser.getFileFilter();
            if (selected.getParentFile() != null) {
                lastSaveDirectory = selected.getParentFile();
                appPreferences.setLastSaveDirectory(lastSaveDirectory.getAbsolutePath());
            }

            String chosenFormat = exportFormatFromFilter(chosenFilter, pdfFilter, wordFilter, htmlFilter, jsonFilter, rtfFilter, mdFilter, taggedTxtFilter);
            String conversionType = String.valueOf(formatCombo.getSelectedItem());
            FileExporter.ExportTheme exportTheme = selectedExportTheme();
            beginBusy("Saving file...");

            new SwingWorker<SaveResult, Void>() {
                @Override
                protected SaveResult doInBackground() {
                    SaveResult result = performExport(selected, chosenFormat, structuredSource, markdownText, convertedText, conversionType, exportTheme);
                    if (result.exportError == null) {
                        try {
                            historyDAO.insertConversion(markdownText, convertedText, conversionType, result.targetFile.getAbsolutePath(), chosenFormat);
                            result.historySaved = true;
                        } catch (Exception ex) {
                            result.historySaved = false;
                            result.historyError = rootMessage(ex);
                        }
                    }
                    return result;
                }

                @Override
                protected void done() {
                    try {
                        SaveResult result = get();
                        if (result.exportError != null) {
                            JOptionPane.showMessageDialog(
                                    TextConverterFrame.this,
                                    result.exportError,
                                    "Save Failed",
                                    JOptionPane.ERROR_MESSAGE
                            );
                            updateStatus("Save failed");
                            return;
                        }

                        savedLocationLabel.setText("Last saved: " + result.targetFile.getAbsolutePath());
                        updateSaveStatus(formatLabelForStatus(result.format), result.targetFile, result.historySaved);
                        if (!result.historySaved && result.historyError != null) {
                            JOptionPane.showMessageDialog(
                                    TextConverterFrame.this,
                                    result.historyError,
                                    "History Save Failed",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                                TextConverterFrame.this,
                                rootMessage(ex),
                                "Save Failed",
                                JOptionPane.ERROR_MESSAGE
                        );
                        updateStatus("Save failed");
                    } finally {
                        endBusy();
                    }
                }
            }.execute();
        }
    }

    private FileNameExtensionFilter resolvePreferredFilter(
            String preferredFormat,
            FileNameExtensionFilter txtFilter,
            FileNameExtensionFilter taggedTxtFilter,
            FileNameExtensionFilter pdfFilter,
            FileNameExtensionFilter wordFilter,
            FileNameExtensionFilter htmlFilter,
            FileNameExtensionFilter jsonFilter,
            FileNameExtensionFilter rtfFilter,
            FileNameExtensionFilter mdFilter
    ) {
        if ("PDF".equalsIgnoreCase(preferredFormat)) {
            return pdfFilter;
        }
        if ("DOCX".equalsIgnoreCase(preferredFormat)) {
            return wordFilter;
        }
        if ("HTML".equalsIgnoreCase(preferredFormat)) {
            return htmlFilter;
        }
        if ("JSON".equalsIgnoreCase(preferredFormat)) {
            return jsonFilter;
        }
        if ("RTF".equalsIgnoreCase(preferredFormat)) {
            return rtfFilter;
        }
        if ("MD".equalsIgnoreCase(preferredFormat)) {
            return mdFilter;
        }
        if ("TAGGED_TXT".equalsIgnoreCase(preferredFormat)) {
            return taggedTxtFilter;
        }
        return txtFilter;
    }

    private File ensureExtension(File file, String extension) {
        String path = file.getAbsolutePath();
        if (path.toLowerCase(Locale.ROOT).endsWith(extension)) {
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
            File parent = new File(record.getExportPath()).getParentFile();
            if (parent != null) {
                lastSaveDirectory = parent;
                appPreferences.setLastSaveDirectory(lastSaveDirectory.getAbsolutePath());
            }
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

    private void addActionButton(JButton button) {
        actionButtons.add(button);
    }

    private void styleActionButton(JButton button, UiTheme theme) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setForeground(theme.textPrimary);
        button.setBackground(theme.buttonBg);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(theme.buttonBorder, 1, true));
        button.setFocusPainted(false);
    }

    private void styleComboBox(JComboBox<String> comboBox, UiTheme theme) {
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        comboBox.setBackground(theme.inputBg);
        comboBox.setForeground(theme.textPrimary);
        comboBox.setFocusable(false);
    }

    private void setMutedText(JLabel label, UiTheme theme) {
        label.setForeground(theme.textMuted);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
    }

    private void openLastSaveFolder() {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "Desktop open is not supported on this system.", "Unsupported", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lastSaveDirectory == null || !lastSaveDirectory.exists()) {
            JOptionPane.showMessageDialog(this, "No saved folder available yet.", "Not Available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(lastSaveDirectory);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open folder:\n" + ex.getMessage(), "Open Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSettingsDialog() {
        JComboBox<String> uiThemeSelect = new JComboBox<>(new String[]{"Dark", "Light", "Steel"});
        uiThemeSelect.setSelectedItem(currentUiTheme.label);

        JComboBox<String> exportThemeSelect = new JComboBox<>(new String[]{"Blue", "Classic", "Dark"});
        exportThemeSelect.setSelectedItem(exportThemeCombo.getSelectedItem());

        JCheckBox historyOnCopyCheck = new JCheckBox("Save copied output to history", saveHistoryOnCopy);
        JCheckBox wrapOutputCheck = new JCheckBox("Wrap output text", outputWrapEnabled);
        JSpinner debounceMs = new JSpinner(new SpinnerNumberModel(debounceTimer.getDelay(), 100, 2000, 50));

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.add(new JLabel("App UI Theme"));
        panel.add(uiThemeSelect);
        panel.add(new JLabel("Default Export Theme"));
        panel.add(exportThemeSelect);
        panel.add(new JLabel("Live Preview Delay (ms)"));
        panel.add(debounceMs);
        panel.add(historyOnCopyCheck);
        panel.add(new JLabel(""));
        panel.add(wrapOutputCheck);
        panel.add(new JLabel(""));

        int result = JOptionPane.showConfirmDialog(this, panel, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            currentUiTheme = UiTheme.fromLabel(String.valueOf(uiThemeSelect.getSelectedItem()));
            applyUiTheme(currentUiTheme);
            exportThemeCombo.setSelectedItem(exportThemeSelect.getSelectedItem());
            saveHistoryOnCopy = historyOnCopyCheck.isSelected();
            outputWrapEnabled = wrapOutputCheck.isSelected();
            outputArea.setLineWrap(outputWrapEnabled);
            outputArea.setWrapStyleWord(outputWrapEnabled);
            previewDelayMs = (Integer) debounceMs.getValue();
            debounceTimer.setDelay(previewDelayMs);
            persistCurrentSettings();
            updateStatus("Settings updated");
        }
    }

    private void applyUiTheme(UiTheme theme) {
        getContentPane().setBackground(theme.appBg);
        topBar.setBackground(theme.appBg);
        buttonBar.setBackground(theme.appBg);
        bottomBar.setBackground(theme.appBg);
        statusPanel.setBackground(theme.appBg);
        leftActionsPanel.setBackground(theme.appBg);
        rightActionsPanel.setBackground(theme.appBg);

        inputPanel.setBackground(theme.panelBg);
        outputPanel.setBackground(theme.panelBg);
        inputCounterPanel.setBackground(theme.panelBg);
        outputCounterPanel.setBackground(theme.panelBg);

        inputArea.setBackground(theme.inputBg);
        outputArea.setBackground(theme.outputBg);
        inputArea.setForeground(theme.textPrimary);
        outputArea.setForeground(theme.textPrimary);
        inputArea.setCaretColor(theme.accent);
        outputArea.setCaretColor(theme.accent);

        inputPanel.setBorder(createPanelBorder("Markdown Input", theme));
        outputPanel.setBorder(createPanelBorder("Converted Output", theme));

        for (JButton button : actionButtons) {
            styleActionButton(button, theme);
        }
        styleComboBox(formatCombo, theme);
        styleComboBox(exportThemeCombo, theme);

        statusLabel.setForeground(theme.textPrimary);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        busyProgressBar.setForeground(theme.accent);
        busyProgressBar.setBackground(theme.panelBg);
        busyProgressBar.setBorder(BorderFactory.createLineBorder(theme.border, 1, true));
        setMutedText(savedLocationLabel, theme);
        setMutedText(tagSummaryLabel, theme);
        setMutedText(inputCountLabel, theme);
        setMutedText(outputCountLabel, theme);

        if (getJMenuBar() != null) {
            styleMenuBar(getJMenuBar(), theme);
        }
        repaint();
    }

    private void styleMenuBar(JMenuBar menuBar, UiTheme theme) {
        menuBar.setBackground(theme.appBg);
        menuBar.setForeground(theme.textPrimary);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                styleMenu(menu, theme);
            }
        }
    }

    private void styleMenu(JMenu menu, UiTheme theme) {
        menu.setForeground(theme.textPrimary);
        menu.setBackground(theme.panelBg);
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item == null) {
                continue;
            }
            item.setForeground(theme.textPrimary);
            item.setBackground(theme.panelBg);
            if (item instanceof JMenu subMenu) {
                styleMenu(subMenu, theme);
            }
        }
    }

    private TitledBorder createPanelBorder(String title, UiTheme theme) {
        TitledBorder titledBorder = BorderFactory.createTitledBorder(new LineBorder(theme.border, 1, true), title);
        titledBorder.setTitleColor(theme.textPrimary);
        titledBorder.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        return titledBorder;
    }

    private FileExporter.ExportTheme selectedExportTheme() {
        return FileExporter.ExportTheme.fromLabel(String.valueOf(exportThemeCombo.getSelectedItem()));
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

    private void loadStoredSettings() {
        currentUiTheme = UiTheme.fromLabel(appPreferences.getUiThemeLabel(currentUiTheme.label));
        defaultExportTheme = appPreferences.getExportThemeLabel(defaultExportTheme);
        saveHistoryOnCopy = appPreferences.isSaveHistoryOnCopy(saveHistoryOnCopy);
        outputWrapEnabled = appPreferences.isOutputWrapEnabled(outputWrapEnabled);
        previewDelayMs = appPreferences.getPreviewDelayMs(previewDelayMs);

        String defaultPath = lastSaveDirectory.getAbsolutePath();
        String configuredPath = appPreferences.getLastSaveDirectory(defaultPath);
        File configuredDirectory = new File(configuredPath);
        if (configuredDirectory.isDirectory()) {
            lastSaveDirectory = configuredDirectory;
        }
    }

    private void persistCurrentSettings() {
        appPreferences.setUiThemeLabel(currentUiTheme.label);
        appPreferences.setExportThemeLabel(String.valueOf(exportThemeCombo.getSelectedItem()));
        appPreferences.setSaveHistoryOnCopy(saveHistoryOnCopy);
        appPreferences.setOutputWrapEnabled(outputWrapEnabled);
        appPreferences.setPreviewDelayMs(previewDelayMs);
        appPreferences.setLastSaveDirectory(lastSaveDirectory.getAbsolutePath());
    }

    private void updateSaveStatus(String formatLabel, File target, boolean historySaved) {
        String status = "Saved " + formatLabel + ": " + target.getName();
        if (!historySaved) {
            status += " (history unavailable)";
        }
        updateStatus(status);
    }

    private void beginBusy(String message) {
        busyTaskCount++;
        busyProgressBar.setVisible(true);
        updateStatus(message);
    }

    private void endBusy() {
        if (busyTaskCount > 0) {
            busyTaskCount--;
        }
        if (busyTaskCount == 0) {
            busyProgressBar.setVisible(false);
        }
    }

    private String exportFormatFromFilter(
            FileNameExtensionFilter chosenFilter,
            FileNameExtensionFilter pdfFilter,
            FileNameExtensionFilter wordFilter,
            FileNameExtensionFilter htmlFilter,
            FileNameExtensionFilter jsonFilter,
            FileNameExtensionFilter rtfFilter,
            FileNameExtensionFilter mdFilter,
            FileNameExtensionFilter taggedTxtFilter
    ) {
        if (chosenFilter == pdfFilter) {
            return "PDF";
        }
        if (chosenFilter == wordFilter) {
            return "DOCX";
        }
        if (chosenFilter == htmlFilter) {
            return "HTML";
        }
        if (chosenFilter == jsonFilter) {
            return "JSON";
        }
        if (chosenFilter == rtfFilter) {
            return "RTF";
        }
        if (chosenFilter == mdFilter) {
            return "MD";
        }
        if (chosenFilter == taggedTxtFilter) {
            return "TAGGED_TXT";
        }
        return "TXT";
    }

    private SaveResult performExport(
            File selected,
            String format,
            String structuredSource,
            String markdownText,
            String convertedText,
            String conversionType,
            FileExporter.ExportTheme exportTheme
    ) {
        SaveResult result = new SaveResult();
        result.format = format;
        try {
            switch (format) {
                case "PDF" -> {
                    result.targetFile = ensureExtension(selected, ".pdf");
                    exporter.exportPDF(structuredSource, result.targetFile, exportTheme);
                }
                case "DOCX" -> {
                    result.targetFile = ensureExtension(selected, ".docx");
                    exporter.exportDOCX(structuredSource, result.targetFile, exportTheme);
                }
                case "HTML" -> {
                    result.targetFile = ensureExtension(selected, ".html");
                    exporter.exportHTML(structuredSource, result.targetFile, exportTheme);
                }
                case "JSON" -> {
                    result.targetFile = ensureExtension(selected, ".json");
                    exporter.exportJSON(markdownText, convertedText, conversionType, result.targetFile);
                }
                case "RTF" -> {
                    result.targetFile = ensureExtension(selected, ".rtf");
                    exporter.exportRTF(convertedText, result.targetFile);
                }
                case "MD" -> {
                    result.targetFile = ensureExtension(selected, ".md");
                    exporter.exportMD(structuredSource, result.targetFile);
                }
                case "TAGGED_TXT" -> {
                    result.targetFile = ensureExtension(selected, ".txt");
                    exporter.exportTaggedTXT(structuredSource, result.targetFile);
                }
                default -> {
                    result.targetFile = ensureExtension(selected, ".txt");
                    exporter.exportTXT(convertedText, result.targetFile);
                }
            }
        } catch (IOException ex) {
            result.exportError = rootMessage(ex);
        }
        return result;
    }

    private String formatLabelForStatus(String format) {
        return switch (format) {
            case "DOCX" -> "Word DOCX";
            case "MD" -> "Markdown";
            case "TAGGED_TXT" -> "tagged TXT";
            default -> format;
        };
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

    private static final class SaveResult {
        private File targetFile;
        private String format;
        private boolean historySaved;
        private String historyError;
        private String exportError;
    }

    private static final class ConversionResult {
        private final String conversionType;
        private final String convertedText;

        private ConversionResult(String conversionType, String convertedText) {
            this.conversionType = conversionType;
            this.convertedText = convertedText;
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
}
