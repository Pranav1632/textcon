package com.textcon;

import com.textcon.ui.TextConverterFrame;
import com.textcon.ui.ThemeInstaller;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.Locale;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (!isWindows()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null,
                    "TextCon currently supports Windows 10/11 only.",
                    "Unsupported OS",
                    JOptionPane.ERROR_MESSAGE
            ));
            return;
        }

        SwingUtilities.invokeLater(() -> {
            ThemeInstaller.installDarkTheme();
            new TextConverterFrame().setVisible(true);
        });
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
