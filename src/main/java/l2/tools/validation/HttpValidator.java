package l2.tools.validation;

import l2.tools.constant.FileConstants;
import l2.tools.constant.HttpConstants;
import l2.tools.exception.ValidationException;

import java.util.logging.Logger;

/**
 * Validates HTTP requests for security and compliance.
 * Performs header validation and content security checks.
 */
public final class HttpValidator {
    
    private static final Logger logger = Logger.getLogger(HttpValidator.class.getSimpleName());
    
    /**
     * Validates HTTP request line length.
     */
    public static void validateRequestLine(String requestLine) throws ValidationException {
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new ValidationException("Empty request line");
        }

        if (requestLine.length() > HttpConstants.MAX_REQUEST_LINE_LENGTH) {
            logger.warning("Request line too long: " + requestLine.length() + " bytes");
            throw new ValidationException("Request line too long: " + requestLine.length() + " bytes");
        }
    }
    
    /**
     * Validates header size limits.
     */
    public static void validateHeaderSize(int totalHeaderSize, int maxHeaderSize) throws ValidationException {
        if (totalHeaderSize > maxHeaderSize) {
            logger.warning(String.format("Request headers too large: %d bytes (max: %d)", totalHeaderSize, maxHeaderSize));
            throw new ValidationException("Request headers too large: %d bytes (max: %d)", totalHeaderSize, maxHeaderSize);
        }
    }
    
    /**
     * Validates header count to prevent header bombing.
     */
    public static void validateHeaderCount(int headerCount) throws ValidationException {
        if (headerCount > HttpConstants.MAX_HEADER_COUNT) {
            logger.warning("Too many headers: " + headerCount);
            throw new ValidationException("Too many headers: " + headerCount);
        }
    }
    
    /**
     * Validates individual header for suspicious content.
     */
    public static void validateHeader(String key, String value) throws ValidationException {
        if (key.isEmpty()) {
            throw new ValidationException("Empty header name");
        }

        if (containsSuspiciousHeaderContent(key, value)) {
            logger.warning("Suspicious header detected: " + key + ": " + value);
            throw new ValidationException("Suspicious header content");
        }
    }
    
    /**
     * Validates Content-Length header.
     */
    public static int validateContentLength(String contentLengthHeader) throws ValidationException {
        if (contentLengthHeader == null) {
            throw new ValidationException("Content-Length header is required");
        }
        
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthHeader);
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid Content-Length: " + contentLengthHeader);
        }
        
        if (contentLength <= 0) {
            throw new ValidationException("Content-Length must be positive");
        }
        
        return contentLength;
    }
    
    /**
     * Validates request size against configuration limits.
     */
    public static void validateRequestSize(int contentLength, int maxRequestSize, String clientAddress) throws ValidationException {
        if (contentLength > maxRequestSize) {
            logger.warning(String.format("Request too large: %d bytes (max: %d) from %s", contentLength, maxRequestSize, clientAddress));
            throw new ValidationException("Request body too large: %d bytes (max: %d)", contentLength, maxRequestSize);
        }
    }
    
    /**
     * Checks for suspicious content in HTTP headers that might indicate attacks.
     */
    private static boolean containsSuspiciousHeaderContent(String key, String value) {
        String lowerKey = key.toLowerCase();
        String lowerValue = value.toLowerCase();

        // Check for script injection attempts
        for (String pattern : FileConstants.SUSPICIOUS_HEADER_PATTERNS) {
            if (lowerValue.contains(pattern)) {
                return true;
            }
        }

        // Check for path traversal in certain headers
        if ((lowerKey.contains("file") || lowerKey.contains("path")) &&
            (value.contains("../") || value.contains("..\\"))) {
            return true;
        }

        return false;
    }
    
    private HttpValidator() {
        // Utility class - prevent instantiation
    }
}
