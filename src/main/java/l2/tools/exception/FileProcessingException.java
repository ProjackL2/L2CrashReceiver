package l2.tools.exception;

import java.io.IOException;

/**
 * Exception thrown when file processing operations fail.
 * Used for file I/O errors, disk space issues, and file system errors.
 */
public class FileProcessingException extends CrashReceiverException {
    
    private static final long serialVersionUID = 1L;

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String format, Object... args) {
        super(format, args);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileProcessingException(String message, IOException cause) {
        super(message, cause);
    }
}
