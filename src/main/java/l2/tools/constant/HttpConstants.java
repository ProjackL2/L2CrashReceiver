package l2.tools.constant;

/**
 * HTTP-related constants used throughout the application.
 * Centralizes magic strings and values for better maintainability.
 */
public final class HttpConstants {
    
    // HTTP Methods
    public static final String POST_METHOD = "POST";
    public static final String GET_METHOD = "GET";
    
    // HTTP Headers
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String CONTENT_LENGTH_HEADER = "Content-Length";
    public static final String CONNECTION_HEADER = "Connection";
    public static final String USER_AGENT_HEADER = "User-Agent";
    
    // HTTP Status Codes
    public static final int STATUS_OK = 200;
    public static final int STATUS_BAD_REQUEST = 400;
    public static final int STATUS_REQUEST_TIMEOUT = 408;
    public static final int STATUS_PAYLOAD_TOO_LARGE = 413;
    public static final int STATUS_INTERNAL_SERVER_ERROR = 500;
    
    // HTTP Status Messages
    public static final String STATUS_OK_MESSAGE = "OK";
    public static final String STATUS_BAD_REQUEST_MESSAGE = "Bad Request";
    public static final String STATUS_REQUEST_TIMEOUT_MESSAGE = "Request Timeout";
    public static final String STATUS_PAYLOAD_TOO_LARGE_MESSAGE = "Payload Too Large";
    public static final String STATUS_INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
    
    // Content Types
    public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=utf-8";
    public static final String CONTENT_TYPE_MULTIPART_FORM_DATA = "multipart/form-data";
    
    // HTTP Protocol
    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String CRLF = "\r\n";
    public static final String HEADER_SEPARATOR = ": ";
    
    // Connection Values
    public static final String CONNECTION_CLOSE = "close";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";
    
    // Limits
    public static final int MAX_REQUEST_LINE_LENGTH = 2048;
    public static final int MAX_HEADER_COUNT = 50;
    
    private HttpConstants() {
        // Utility class - prevent instantiation
    }
}
