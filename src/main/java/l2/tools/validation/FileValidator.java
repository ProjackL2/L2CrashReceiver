package l2.tools.validation;

import l2.tools.constant.FileConstants;
import l2.tools.exception.SecurityException;
import l2.tools.exception.ValidationException;
import l2.tools.monitoring.ServerMetrics;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Validates files for security and integrity.
 * Performs file type checking, content validation, and security scanning.
 */
public final class FileValidator {
    
    private static final Logger logger = Logger.getLogger(FileValidator.class.getSimpleName());
    
    /**
     * Validates file data for size and basic integrity.
     */
    public static void validateFileData(byte[] data, int maxFileSize) throws ValidationException {
        if (data == null) {
            throw new ValidationException("File data cannot be null");
        }
        
        if (data.length == 0) {
            throw new ValidationException("File data cannot be empty");
        }
        
        if (data.length > maxFileSize) {
            throw new ValidationException("File size (%d bytes) exceeds maximum allowed size (%d bytes)", data.length, maxFileSize);
        }
    }
    
    /**
     * Validates file type based on extension and content.
     */
    public static void validateFileType(byte[] data, String fileName) throws SecurityException {
        if (fileName == null || fileName.trim().isEmpty()) {
            logger.warning("File upload attempted with null or empty filename");
            throw new SecurityException("Filename cannot be null or empty");
        }
        
        String extension = FileNameUtils.getFileExtension(fileName).toLowerCase();
        String baseFileName = FileNameUtils.getBaseFileName(fileName).toLowerCase();
        
        validateFileName(baseFileName);
        validateFileExtension(extension);
        validateFileSignature(data, extension, fileName);
        validateFileContent(data, extension, fileName);
        
        logger.info(String.format("File type validation passed for: %s (%s)", fileName, extension));
    }
    
    /**
     * Validates filename for dangerous patterns.
     */
    private static void validateFileName(String baseFileName) throws SecurityException {
        if (FileConstants.DANGEROUS_FILENAMES.contains(baseFileName)) {
            logger.warning(String.format("Blocked dangerous filename: %s", baseFileName));
            ServerMetrics.getInstance().recordSecurityViolation();
            throw new SecurityException("Filename not allowed: " + baseFileName);
        }
    }
    
    /**
     * Validates file extension against allowed and dangerous lists.
     */
    private static void validateFileExtension(String extension) throws SecurityException {
        if (FileConstants.DANGEROUS_EXTENSIONS.contains(extension)) {
            logger.warning(String.format("Blocked dangerous file extension: %s", extension));
            ServerMetrics.getInstance().recordSecurityViolation();
            throw new SecurityException("File extension not allowed: " + extension);
        }
        
        if (!FileConstants.ALLOWED_EXTENSIONS.contains(extension)) {
            logger.warning(String.format("Blocked non-whitelisted file extension: %s", extension));
            ServerMetrics.getInstance().recordSecurityViolation();
            throw new SecurityException("File type not allowed: " + extension);
        }
    }
    
    /**
     * Validates file signature (magic bytes) for known file types.
     */
    private static void validateFileSignature(byte[] data, String extension, String fileName) throws SecurityException {
        if (FileConstants.EXT_DUMP.equals(extension)) {
            byte[] expectedSignature = FileConstants.DUMP_FILE_SIGNATURE;
            
            if (data.length < expectedSignature.length) {
                throw new SecurityException("File %s is too small to contain valid %s signature", fileName, extension);
            }
            
            for (int i = 0; i < expectedSignature.length; i++) {
                if (data[i] != expectedSignature[i]) {
                    logger.warning(String.format("Invalid file signature for %s (expected %s format)", fileName, extension));
                    throw new SecurityException("File signature does not match expected format for %s files", extension);
                }
            }
        }
    }
    
    /**
     * Validates file content for additional security checks.
     */
    private static void validateFileContent(byte[] data, String extension, String fileName) throws SecurityException {
        // Check for embedded executables or suspicious patterns
        if (containsSuspiciousPatterns(data)) {
            logger.warning(String.format("Suspicious content detected in file: %s", fileName));
            throw new SecurityException("File contains suspicious content patterns");
        }
        
        // Validate text-based files
        if (FileConstants.EXT_TEXT.equals(extension) || FileConstants.EXT_LOG.equals(extension)) {
            validateTextFile(data, fileName);
        }
        
        // Check file entropy to detect packed/encrypted content
        double entropy = calculateEntropy(data);
        if (entropy > FileConstants.HIGH_ENTROPY_THRESHOLD) {
            logger.warning(String.format("High entropy detected in file %s (%.2f) - possible packed/encrypted content", fileName, entropy));
            // For crash dumps, high entropy might be normal, so just log warning
            if (!FileConstants.EXT_DUMP.equals(extension)) {
                throw new SecurityException("File appears to be packed or encrypted");
            }
        }
    }
    
    /**
     * Checks for suspicious binary patterns that might indicate malware.
     */
    private static boolean containsSuspiciousPatterns(byte[] data) {
        if (data.length < 2) {
            return false;
        }
        
        // Check for PE executable (Windows)
        if (data.length >= 64 && data[0] == FileConstants.PE_EXECUTABLE_SIGNATURE[0] && 
            data[1] == FileConstants.PE_EXECUTABLE_SIGNATURE[1]) {
            logger.warning("PE executable signature detected");
            return true;
        }
        
        // Check for ELF executable (Linux)
        if (data.length >= 4) {
            boolean isElf = true;
            for (int i = 0; i < FileConstants.ELF_EXECUTABLE_SIGNATURE.length; i++) {
                if (data[i] != FileConstants.ELF_EXECUTABLE_SIGNATURE[i]) {
                    isElf = false;
                    break;
                }
            }
            if (isElf) {
                logger.warning("ELF executable signature detected");
                return true;
            }
        }
        
        // Check for script shebangs
        if (data[0] == '#' && data[1] == '!') {
            logger.warning("Script shebang detected");
            return true;
        }
        
        return false;
    }
    
    /**
     * Validates text-based files for additional security.
     */
    private static void validateTextFile(byte[] data, String fileName) throws SecurityException {
        String content = new String(data, StandardCharsets.UTF_8);

        // Check for null bytes (indication of binary content)
        if (content.contains("\0")) {
            throw new SecurityException("Text file contains null bytes - possible binary content");
        }

        // Check for suspicious script content
        String lowerContent = content.toLowerCase();
        for (String pattern : FileConstants.SUSPICIOUS_SCRIPT_PATTERNS) {
            if (lowerContent.contains(pattern)) {
                logger.warning(String.format("Suspicious pattern '%s' found in text file: %s", pattern, fileName));
                throw new SecurityException("Text file contains suspicious script content: " + pattern);
            }
        }
    }
    
    /**
     * Calculates Shannon entropy of data to detect packed/encrypted content.
     */
    private static double calculateEntropy(byte[] data) {
        if (data.length == 0) {
            return 0.0;
        }
        
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
    
    private FileValidator() {
        // Utility class - prevent instantiation
    }
}
