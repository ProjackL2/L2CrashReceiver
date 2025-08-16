package l2.tools.exception;

/**
 * Base exception for all L2 Crash Receiver related errors.
 * Provides a consistent exception hierarchy for better error handling.
 */
public class CrashReceiverException extends Exception {
    
    private static final long serialVersionUID = 1L;

    public CrashReceiverException(String message) {
        super(message);
    }

    public CrashReceiverException(String format, Object... args) {
        super(String.format(format,args));
    }

    public CrashReceiverException(String message, Throwable cause) {
        super(message, cause);
    }

    public CrashReceiverException(Throwable cause) {
        super(cause);
    }
}
