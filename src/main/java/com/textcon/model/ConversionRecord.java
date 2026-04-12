package com.textcon.model;

public class ConversionRecord {
    private final int id;
    private final String originalText;
    private final String convertedText;
    private final String conversionType;
    private final String exportPath;
    private final String exportFormat;
    private final String createdAt;

    public ConversionRecord(int id, String originalText, String convertedText, String conversionType, String createdAt) {
        this(id, originalText, convertedText, conversionType, null, null, createdAt);
    }

    public ConversionRecord(
            int id,
            String originalText,
            String convertedText,
            String conversionType,
            String exportPath,
            String exportFormat,
            String createdAt
    ) {
        this.id = id;
        this.originalText = originalText;
        this.convertedText = convertedText;
        this.conversionType = conversionType;
        this.exportPath = exportPath;
        this.exportFormat = exportFormat;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getConvertedText() {
        return convertedText;
    }

    public String getConversionType() {
        return conversionType;
    }

    public String getExportPath() {
        return exportPath;
    }

    public String getExportFormat() {
        return exportFormat;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
