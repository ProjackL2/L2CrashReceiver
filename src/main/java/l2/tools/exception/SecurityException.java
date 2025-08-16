package l2.tools.exception;

/**
 * Exception thrown when security violations are detected.
 * Used for file security checks, path traversal detection, and suspicious content.
 */
public class SecurityException extends CrashReceiverException {
    
    private static final long serialVersionUID = 1L;

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String format, Object... args) {
        super(format, args);
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
