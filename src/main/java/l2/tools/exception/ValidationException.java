package l2.tools.exception;

/**
 * Exception thrown when input validation fails.
 * Used for file validation, request validation, and configuration validation.
 */
public class ValidationException extends CrashReceiverException {
    
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String format, Object... args) {
        super(format, args);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
