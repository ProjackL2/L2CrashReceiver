package l2.tools.service;

import l2.tools.config.ServerConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Service for handling file operations with proper validation and security checks.
 */
public final class FileService {
    
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private final ServerConfiguration configuration;
    
    public FileService(ServerConfiguration configuration) {
        this.configuration = configuration;
    }
    
    /**
     * Initializes the upload directory, creating it if it doesn't exist.
     */
    public void initializeUploadDirectory() throws IOException {
        Path uploadDir = configuration.getUploadDirectory();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        if (!Files.isDirectory(uploadDir)) {
            throw new IOException("Upload path exists but is not a directory: " + uploadDir);
        }
        
        if (!Files.isWritable(uploadDir)) {
            throw new IOException("Upload directory is not writable: " + uploadDir);
        }
    }
    
    /**
     * Saves file data to the upload directory with proper validation.
     *
     * @param data the file data to save
     * @param originalFileName the original filename (will be sanitized)
     * @return the actual saved file path
     * @throws IOException if saving fails
     * @throws SecurityException if filename is invalid or path traversal is detected
     */
    public Path saveFile(byte[] data, String originalFileName) throws IOException {
        validateFileData(data);
        String sanitizedFileName = sanitizeFileName(originalFileName);
        Path targetPath = resolveTargetPath(sanitizedFileName);
        
        // Ensure the resolved path is within the upload directory
        Path normalizedTarget = targetPath.normalize();
        Path uploadDir = configuration.getUploadDirectory().normalize();
        
        if (!normalizedTarget.startsWith(uploadDir)) {
            throw new SecurityException("Attempt to save file outside upload directory: " + normalizedTarget);
        }
        
        // Handle file name conflicts by appending timestamp
        Path finalPath = ensureUniqueFileName(normalizedTarget);
        
        Files.write(finalPath, data, StandardOpenOption.CREATE_NEW);
        return finalPath;
    }
    
    /**
     * Saves description/error text as a text file.
     */
    public Path saveDescription(String description, String baseFileName) throws IOException {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        
        String textFileName = replaceExtension(baseFileName, ".txt");
        return saveFile(description.getBytes("UTF-8"), textFileName);
    }
    
    private void validateFileData(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("File data cannot be null");
        }
        
        if (data.length == 0) {
            throw new IllegalArgumentException("File data cannot be empty");
        }
        
        if (data.length > configuration.getMaxFileSize()) {
            throw new IllegalArgumentException(
                String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes)", 
                    data.length, configuration.getMaxFileSize())
            );
        }
    }
    
    private String sanitizeFileName(String fileName) {
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
        
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            // Keep extension if present
            int lastDot = sanitized.lastIndexOf('.');
            if (lastDot > 0 && lastDot < sanitized.length() - 1) {
                String extension = sanitized.substring(lastDot);
                String name = sanitized.substring(0, lastDot);
                int maxNameLength = MAX_FILENAME_LENGTH - extension.length();
                sanitized = name.substring(0, Math.min(name.length(), maxNameLength)) + extension;
            } else {
                sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
            }
        }
        
        return sanitized;
    }
    
    private String generateDefaultFileName() {
        return "crash_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".dmp";
    }
    
    private Path resolveTargetPath(String fileName) {
        return configuration.getUploadDirectory().resolve(fileName);
    }
    
    private Path ensureUniqueFileName(Path targetPath) throws IOException {
        if (!Files.exists(targetPath)) {
            return targetPath;
        }
        
        String fileName = targetPath.getFileName().toString();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        
        int lastDot = fileName.lastIndexOf('.');
        String uniqueFileName;
        
        if (lastDot > 0) {
            String name = fileName.substring(0, lastDot);
            String extension = fileName.substring(lastDot);
            uniqueFileName = name + "_" + timestamp + extension;
        } else {
            uniqueFileName = fileName + "_" + timestamp;
        }
        
        return targetPath.getParent().resolve(uniqueFileName);
    }
    
    private String replaceExtension(String fileName, String newExtension) {
        if (fileName == null) {
            return "description" + newExtension;
        }
        
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot) + newExtension;
        } else {
            return fileName + newExtension;
        }
    }
}
