package com.textcon;

import com.textcon.ui.TextConverterFrame;
import com.textcon.ui.ThemeInstaller;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeInstaller.installDarkTheme();
            new TextConverterFrame().setVisible(true);
        });
    }
}
