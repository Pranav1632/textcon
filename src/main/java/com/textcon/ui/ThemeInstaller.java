    package com.textcon.ui;

    import javax.swing.UIManager;
    import javax.swing.plaf.ColorUIResource;
    import javax.swing.plaf.FontUIResource;
    import java.awt.Color;
    import java.awt.Font;
    import java.util.Enumeration;

    public final class ThemeInstaller {
        private ThemeInstaller() {
        }

        public static void installDarkTheme() {
            tryNimbusLookAndFeel();
            applyDarkDefaults();
            applyGlobalFonts();
        }

        private static void tryNimbusLookAndFeel() {
            try {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equalsIgnoreCase(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        return;
                    }
                }
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Falls back to platform default.
            }
        }

        private static void applyDarkDefaults() {
            ColorUIResource bgApp = color(16, 20, 28);
            ColorUIResource bgPanel = color(24, 30, 42);
            ColorUIResource bgInput = color(21, 27, 38);
            ColorUIResource bgSelection = color(45, 118, 255);
            ColorUIResource textPrimary = color(228, 235, 246);
            ColorUIResource textMuted = color(166, 177, 198);
            ColorUIResource accent = color(60, 146, 255);

            UIManager.put("control", bgPanel);
            UIManager.put("info", bgPanel);
            UIManager.put("nimbusBase", color(22, 30, 44));
            UIManager.put("nimbusBlueGrey", color(31, 40, 57));
            UIManager.put("nimbusLightBackground", bgInput);
            UIManager.put("nimbusSelectionBackground", bgSelection);
            UIManager.put("nimbusSelectedText", color(245, 249, 255));
            UIManager.put("text", textPrimary);

            UIManager.put("Panel.background", bgPanel);
            UIManager.put("Viewport.background", bgInput);
            UIManager.put("ScrollPane.background", bgInput);
            UIManager.put("TextArea.background", bgInput);
            UIManager.put("TextArea.foreground", textPrimary);
            UIManager.put("TextArea.caretForeground", accent);
            UIManager.put("TextArea.selectionBackground", bgSelection);
            UIManager.put("TextArea.selectionForeground", color(245, 249, 255));
            UIManager.put("TextField.background", bgInput);
            UIManager.put("TextField.foreground", textPrimary);
            UIManager.put("TextField.caretForeground", accent);

            UIManager.put("ComboBox.background", bgInput);
            UIManager.put("ComboBox.foreground", textPrimary);
            UIManager.put("ComboBox.selectionBackground", bgSelection);
            UIManager.put("ComboBox.selectionForeground", color(245, 249, 255));
            UIManager.put("Button.background", color(35, 45, 64));
            UIManager.put("Button.foreground", textPrimary);
            UIManager.put("Button.select", color(47, 59, 81));
            UIManager.put("MenuBar.background", bgApp);
            UIManager.put("MenuBar.foreground", textPrimary);
            UIManager.put("Menu.background", bgPanel);
            UIManager.put("Menu.foreground", textPrimary);
            UIManager.put("MenuItem.background", bgPanel);
            UIManager.put("MenuItem.foreground", textPrimary);
            UIManager.put("Separator.foreground", color(52, 62, 84));
            UIManager.put("Label.foreground", textPrimary);
            UIManager.put("ToolTip.background", color(36, 43, 59));
            UIManager.put("ToolTip.foreground", color(240, 245, 255));
            UIManager.put("OptionPane.background", bgPanel);
            UIManager.put("OptionPane.messageForeground", textPrimary);
        }

        private static void applyGlobalFonts() {
            FontUIResource uiFont = new FontUIResource(bestUiFont(13f));
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object value = UIManager.get(key);
                if (value instanceof FontUIResource) {
                    UIManager.put(key, uiFont);
                }
            }
        }

        private static Font bestUiFont(float size) {
            String[] preferred = {"Segoe UI Variable", "Segoe UI", "Noto Sans", Font.SANS_SERIF};
            for (String name : preferred) {
                Font font = new Font(name, Font.PLAIN, Math.round(size));
                if (font.getFamily().equalsIgnoreCase(name) || name.equals(Font.SANS_SERIF)) {
                    return font;
                }
            }
            return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
        }

        private static ColorUIResource color(int r, int g, int b) {
            return new ColorUIResource(new Color(r, g, b));
        }
    }
