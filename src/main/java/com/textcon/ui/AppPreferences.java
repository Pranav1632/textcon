package com.textcon.ui;

import java.util.prefs.Preferences;

final class AppPreferences {
    private static final String KEY_UI_THEME = "ui_theme";
    private static final String KEY_EXPORT_THEME = "export_theme";
    private static final String KEY_HISTORY_ON_COPY = "history_on_copy";
    private static final String KEY_OUTPUT_WRAP = "output_wrap";
    private static final String KEY_PREVIEW_DELAY_MS = "preview_delay_ms";
    private static final String KEY_LAST_SAVE_DIRECTORY = "last_save_directory";

    private final Preferences preferences = Preferences.userNodeForPackage(AppPreferences.class);

    String getUiThemeLabel(String defaultValue) {
        return preferences.get(KEY_UI_THEME, defaultValue);
    }

    String getExportThemeLabel(String defaultValue) {
        return preferences.get(KEY_EXPORT_THEME, defaultValue);
    }

    boolean isSaveHistoryOnCopy(boolean defaultValue) {
        return preferences.getBoolean(KEY_HISTORY_ON_COPY, defaultValue);
    }

    boolean isOutputWrapEnabled(boolean defaultValue) {
        return preferences.getBoolean(KEY_OUTPUT_WRAP, defaultValue);
    }

    int getPreviewDelayMs(int defaultValue) {
        int configured = preferences.getInt(KEY_PREVIEW_DELAY_MS, defaultValue);
        if (configured < 100) {
            return 100;
        }
        return Math.min(configured, 2000);
    }

    String getLastSaveDirectory(String defaultValue) {
        return preferences.get(KEY_LAST_SAVE_DIRECTORY, defaultValue);
    }

    void setUiThemeLabel(String theme) {
        preferences.put(KEY_UI_THEME, theme);
    }

    void setExportThemeLabel(String exportTheme) {
        preferences.put(KEY_EXPORT_THEME, exportTheme);
    }

    void setSaveHistoryOnCopy(boolean enabled) {
        preferences.putBoolean(KEY_HISTORY_ON_COPY, enabled);
    }

    void setOutputWrapEnabled(boolean enabled) {
        preferences.putBoolean(KEY_OUTPUT_WRAP, enabled);
    }

    void setPreviewDelayMs(int delayMs) {
        preferences.putInt(KEY_PREVIEW_DELAY_MS, delayMs);
    }

    void setLastSaveDirectory(String directory) {
        preferences.put(KEY_LAST_SAVE_DIRECTORY, directory);
    }
}
