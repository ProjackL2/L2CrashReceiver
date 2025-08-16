package l2.tools.service;

import l2.tools.config.ServerConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for handling file operations with proper validation and security checks.
 */
public final class FileService {
    
    private static final Logger logger = Logger.getLogger(FileService.class.getName());
    
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".dmp", // Windows crash dump files
        ".txt", // Text descriptions
        ".log" // Log files
    ));

    private static final Map<String, byte[]> FILE_SIGNATURES = new HashMap<String, byte[]>() {{
        put(".dmp", new byte[]{0x4D, 0x44, 0x4D, 0x50}); // "MDMP"
        put(".txt", new byte[]{});
        put(".log", new byte[]{});
    }};
    
    // Dangerous file extensions that should never be allowed
    private static final Set<String> DANGEROUS_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".msi", ".dll",
        ".sh", ".bash", ".zsh", ".csh", ".fish",
        ".ps1", ".ps2", ".psm1", ".psd1",
        ".vbs", ".vbe", ".js", ".jse", ".wsf", ".wsh",
        ".php", ".asp", ".aspx", ".jsp", ".py", ".rb", ".pl",
        ".jar", ".war", ".ear",
        ".app", ".dmg", ".pkg",
        ".deb", ".rpm", ".snap",
        ".iso", ".img", ".bin"
    ));
    
    // Dangerous filenames that should be blocked
    private static final Set<String> DANGEROUS_FILENAMES = new HashSet<>(Arrays.asList(
        "autorun.inf", "desktop.ini", "thumbs.db", ".htaccess", ".htpasswd",
        "web.config", "app.config", "machine.config",
        "hosts", "passwd", "shadow", "sudoers"
    ));
    
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
        validateFileType(data, originalFileName);
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
     * Saves a file with a new filename ending.
     */
    public Path saveFileWithPostfix(byte[] data, String baseFileName, String postfix) throws IOException {
        String textFileName = replaceExtension(baseFileName, postfix);
        return saveFile(data, textFileName);
    }
    
    /**
     * Saves description/error text as a text file.
     */
    public Path saveDescription(String description, String baseFileName) throws IOException {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        
        String textFileName = replaceExtension(baseFileName, ".txt");
        return saveFile(description.getBytes(StandardCharsets.UTF_8), textFileName);
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
    
    /**
     * Validates file type based on extension and content signatures.
     * 
     * @param data the file data to validate
     * @param fileName the original filename
     * @throws SecurityException if file type is not allowed or suspicious
     */
    private void validateFileType(byte[] data, String fileName) throws SecurityException {
        if (fileName == null || fileName.trim().isEmpty()) {
            logger.warning("File upload attempted with null or empty filename");
            throw new SecurityException("Filename cannot be null or empty");
        }
        
        String extension = getFileExtension(fileName).toLowerCase();
        String baseFileName = getBaseFileName(fileName).toLowerCase();
        
        // Check for dangerous filenames
        if (DANGEROUS_FILENAMES.contains(baseFileName)) {
            logger.warning(String.format("Blocked dangerous filename: %s", fileName));
            throw new SecurityException("Filename not allowed: " + baseFileName);
        }
        
        // Check for dangerous extensions
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            logger.warning(String.format("Blocked dangerous file extension: %s for file %s", extension, fileName));
            throw new SecurityException("File extension not allowed: " + extension);
        }
        
        // Check if extension is in allowed list
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            logger.warning(String.format("Blocked non-whitelisted file extension: %s for file %s", extension, fileName));
            throw new SecurityException("File type not allowed: " + extension);
        }
        
        // Validate file signature for specific types
        validateFileSignature(data, extension, fileName);
        
        // Additional content validation
        validateFileContent(data, extension, fileName);
        
        logger.info(String.format("File type validation passed for: %s (%s)", fileName, extension));
    }
    
    /**
     * Validates file signature (magic bytes) for known file types.
     */
    private void validateFileSignature(byte[] data, String extension, String fileName) throws SecurityException {
        byte[] expectedSignature = FILE_SIGNATURES.get(extension);
        
        if (expectedSignature != null && expectedSignature.length > 0) {
            if (data.length < expectedSignature.length) {
                throw new SecurityException(
                    String.format("File %s is too small to contain valid %s signature", fileName, extension)
                );
            }
            
            // Check if file starts with expected signature
            for (int i = 0; i < expectedSignature.length; i++) {
                if (data[i] != expectedSignature[i]) {
                    logger.warning(String.format("Invalid file signature for %s (expected %s format)", 
                        fileName, extension));
                    throw new SecurityException(
                        String.format("File signature does not match expected format for %s files", extension)
                    );
                }
            }
        }
    }
    
    /**
     * Validates file content for additional security checks.
     */
    private void validateFileContent(byte[] data, String extension, String fileName) throws SecurityException {
        // Check for embedded executables or suspicious patterns
        if (containsSuspiciousPatterns(data)) {
            logger.warning(String.format("Suspicious content detected in file: %s", fileName));
            throw new SecurityException("File contains suspicious content patterns");
        }
        
        // Validate text-based files
        if (extension.equals(".txt") || extension.equals(".log")) {
            validateTextFile(data, extension, fileName);
        }
        
        // Check file entropy to detect packed/encrypted content
        if (calculateEntropy(data) > 7.5) {
            logger.warning(String.format("High entropy detected in file %s (%.2f) - possible packed/encrypted content", fileName, calculateEntropy(data)));
            // For crash dumps, high entropy might be normal, so just log warning
            if (!extension.equals(".dmp")) {
                throw new SecurityException("File appears to be packed or encrypted");
            }
        }
    }
    
    /**
     * Checks for suspicious binary patterns that might indicate malware.
     */
    private boolean containsSuspiciousPatterns(byte[] data) {
        // Check for common executable signatures
        if (data.length >= 2) {
            // PE executable (Windows)
            if (data.length >= 64 && data[0] == 0x4D && data[1] == 0x5A) { // "MZ"
                logger.warning("PE executable signature detected");
                return true;
            }
            
            // ELF executable (Linux)
            if (data.length >= 4 && data[0] == 0x7F && data[1] == 0x45 && 
                data[2] == 0x4C && data[3] == 0x46) { // "\x7fELF"
                logger.warning("ELF executable signature detected");
                return true;
            }
        }
        
        // Check for script shebangs
        if (data.length >= 2 && data[0] == '#' && data[1] == '!') {
            logger.warning("Script shebang detected");
            return true;
        }
        
        return false;
    }
    
    /**
     * Validates text-based files for additional security.
     */
    private void validateTextFile(byte[] data, String extension, String fileName) throws SecurityException {
        String content = new String(data, StandardCharsets.UTF_8);

        // Check for null bytes (indication of binary content)
        if (content.contains("\0")) {
            throw new SecurityException("Text file contains null bytes - possible binary content");
        }

        // Check for suspicious script content
        String lowerContent = content.toLowerCase();
        String[] suspiciousPatterns = {
            "<script", "javascript:", "eval(", "system(", "exec(", "shell_exec(",
            "passthru(", "file_get_contents", "file_put_contents", "fopen(", "fwrite(",
            "<?php", "<%", "<jsp:", "import os", "import subprocess", "__import__"
        };

        for (String pattern : suspiciousPatterns) {
            if (lowerContent.contains(pattern)) {
                logger.warning(String.format("Suspicious pattern '%s' found in text file: %s", pattern, fileName));
                throw new SecurityException("Text file contains suspicious script content: " + pattern);
            }
        }
    }
    
    /**
     * Calculates Shannon entropy of data to detect packed/encrypted content.
     */
    private double calculateEntropy(byte[] data) {
        if (data.length == 0) return 0.0;
        
        int[] frequencies = new int[256];
        for (byte b : data) {
            frequencies[b & 0xFF]++;
        }
        
        double entropy = 0.0;
        int length = data.length;
        
        for (int freq : frequencies) {
            if (freq > 0) {
                double probability = (double) freq / length;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }
        
        return entropy;
    }
    
    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String fileName) {
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
    private String getBaseFileName(String fileName) {
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
