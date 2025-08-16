package l2.tools.validation;

import l2.tools.constant.Constants;
import l2.tools.constant.FileConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for file name operations and sanitization.
 * Provides safe file naming and path manipulation functions.
 */
public final class FileNameUtils {
    
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern(Constants.TIMESTAMP_FORMAT);
    
    /**
     * Extracts file extension from filename.
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        
        return "";
    }
    
    /**
     * Extracts base filename without extension.
     */
    public static String getBaseFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        // Remove path if present
        String baseName = fileName;
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < fileName.length() - 1) {
            baseName = fileName.substring(lastSlash + 1);
        }
        
        // Remove extension
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            return baseName.substring(0, lastDot);
        }
        
        return baseName;
    }
    
    /**
     * Sanitizes filename for safe file system usage.
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return generateDefaultFileName();
        }
        
        // Remove path separators and normalize
        String sanitized = fileName.replaceAll("[/\\\\:]", "_")
                                  .replaceAll("[^a-zA-Z0-9._-]", "_")
                                  .replaceAll("_{2,}", "_")
                                  .trim();
        
        // Remove leading/trailing dots and underscores
        sanitized = sanitized.replaceAll("^[._]+|[._]+$", "");
        
        if (sanitized.isEmpty()) {
            return generateDefaultFileName();
        }
        
        if (sanitized.length() > FileConstants.MAX_FILENAME_LENGTH) {
            sanitized = truncateFileName(sanitized);
        }
        
        return sanitized;
    }
    
    /**
     * Truncates filename while preserving extension.
     */
    private static String truncateFileName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            String extension = fileName.substring(lastDot);
            String name = fileName.substring(0, lastDot);
            int maxNameLength = FileConstants.MAX_FILENAME_LENGTH - extension.length();
            return name.substring(0, Math.min(name.length(), maxNameLength)) + extension;
        } else {
            return fileName.substring(0, FileConstants.MAX_FILENAME_LENGTH);
        }
    }
    
    /**
     * Generates a default filename with timestamp.
     */
    public static String generateDefaultFileName() {
        return Constants.DEFAULT_CRASH_PREFIX +
               LocalDateTime.now().format(TIMESTAMP_FORMATTER) + 
               FileConstants.EXT_DUMP;
    }
    
    /**
     * Generates a unique filename by appending timestamp.
     */
    public static String generateUniqueFileName(String baseFileName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        int lastDot = baseFileName.lastIndexOf('.');
        if (lastDot > 0) {
            String name = baseFileName.substring(0, lastDot);
            String extension = baseFileName.substring(lastDot);
            return name + "_" + timestamp + extension;
        } else {
            return baseFileName + "_" + timestamp;
        }
    }
    
    /**
     * Replaces file extension with a new one.
     */
    public static String replaceExtension(String fileName, String newExtension) {
        if (fileName == null) {
            return Constants.DEFAULT_DESCRIPTION_NAME + newExtension;
        }
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot) + newExtension;
        } else {
            return fileName + newExtension;
        }
    }
    
    private FileNameUtils() {
        // Utility class - prevent instantiation
    }
}
