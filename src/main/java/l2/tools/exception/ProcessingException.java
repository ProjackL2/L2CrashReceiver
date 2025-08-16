package l2.tools.exception;

import java.io.IOException;

/**
 * Exception thrown when file processing operations fail.
 * Used for file I/O errors, disk space issues, and file system errors.
 */
public class ProcessingException extends CrashReceiverException {
    
    private static final long serialVersionUID = 1L;

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String format, Object... args) {
        super(format, args);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessingException(String message, IOException cause) {
        super(message, cause);
    }
}
