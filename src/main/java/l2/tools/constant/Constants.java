package l2.tools.constant;

/**
 * Constants related to crash report processing.
 * Defines field names and file naming conventions.
 */
public final class Constants {
    
    // Form field names from L2 crash reports
    public static final String VERSION_FIELD = "CRVersion";
    public static final String ERROR_FIELD = "error";
    public static final String DUMP_FILE_FIELD = "dumpfile";
    public static final String GAME_LOG_FIELD = "gamelog";
    public static final String NETWORK_LOG_FIELD = "networklog";
    
    // File naming postfixes
    public static final String GAME_LOG_POSTFIX = "_game.log";
    public static final String NETWORK_LOG_POSTFIX = "_net.log";
    public static final String DESCRIPTION_POSTFIX = ".txt";
    
    // Default file naming
    public static final String DEFAULT_CRASH_PREFIX = "crash_";
    public static final String DEFAULT_DESCRIPTION_NAME = "description";
    
    // Timestamp format for file naming
    public static final String TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss";
    
    private Constants() {
        // Utility class - prevent instantiation
    }
}
