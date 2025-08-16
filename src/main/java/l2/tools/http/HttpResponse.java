package l2.tools.http;

import l2.tools.constant.HttpConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable representation of an HTTP response with status code, headers, and body.
 * Provides factory methods for common response types and builder pattern for custom responses.
 */
public final class HttpResponse {
    
    private final int statusCode;
    private final String statusMessage;
    private final Map<String, String> headers;
    private final String body;
    
    private HttpResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.statusMessage = builder.statusMessage;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.body = builder.body != null ? builder.body : "";
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static HttpResponse ok() {
        return new Builder()
            .status(HttpConstants.STATUS_OK, HttpConstants.STATUS_OK_MESSAGE)
            .defaultHeaders()
            .build();
    }
    
    public static HttpResponse ok(String body) {
        return new Builder()
            .status(HttpConstants.STATUS_OK, HttpConstants.STATUS_OK_MESSAGE)
            .defaultHeaders()
            .body(body)
            .build();
    }
    
    public static HttpResponse badRequest(String message) {
        return new Builder()
            .status(HttpConstants.STATUS_BAD_REQUEST, HttpConstants.STATUS_BAD_REQUEST_MESSAGE)
            .defaultHeaders()
            .body(message)
            .build();
    }
    
    public static HttpResponse payloadTooLarge(String message) {
        return new Builder()
            .status(HttpConstants.STATUS_PAYLOAD_TOO_LARGE, HttpConstants.STATUS_PAYLOAD_TOO_LARGE_MESSAGE)
            .defaultHeaders()
            .body(message)
            .build();
    }
    
    public static HttpResponse requestTimeout(String message) {
        return new Builder()
            .status(HttpConstants.STATUS_REQUEST_TIMEOUT, HttpConstants.STATUS_REQUEST_TIMEOUT_MESSAGE)
            .defaultHeaders()
            .body(message)
            .build();
    }
    
    public static HttpResponse internalServerError(String message) {
        return new Builder()
            .status(HttpConstants.STATUS_INTERNAL_SERVER_ERROR, HttpConstants.STATUS_INTERNAL_SERVER_ERROR_MESSAGE)
            .defaultHeaders()
            .body(message)
            .build();
    }
    
    /**
     * Writes this HTTP response to the given output stream.
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        
        // Status line
        response.append(HttpConstants.HTTP_VERSION)
                .append(" ")
                .append(statusCode)
                .append(" ")
                .append(statusMessage)
                .append(HttpConstants.CRLF);
        
        // Headers
        Map<String, String> allHeaders = new HashMap<>(headers);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        allHeaders.put(HttpConstants.CONTENT_LENGTH_HEADER, String.valueOf(bodyBytes.length));
        
        for (Map.Entry<String, String> header : allHeaders.entrySet()) {
            response.append(header.getKey())
                    .append(HttpConstants.HEADER_SEPARATOR)
                    .append(header.getValue())
                    .append(HttpConstants.CRLF);
        }
        
        // Empty line between headers and body
        response.append(HttpConstants.CRLF);
        
        // Write headers
        outputStream.write(response.toString().getBytes(StandardCharsets.UTF_8));
        
        // Write body
        outputStream.write(bodyBytes);
        outputStream.flush();
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public String getBody() {
        return body;
    }
    
    @Override
    public String toString() {
        return "HttpResponse{" +
                "statusCode=" + statusCode +
                ", statusMessage='" + statusMessage + '\'' +
                ", headers=" + headers.size() +
                ", bodyLength=" + body.length() +
                '}';
    }
    
    public static final class Builder {
        private int statusCode;
        private String statusMessage;
        private Map<String, String> headers = new HashMap<>();
        private String body;
        
        public Builder status(int statusCode, String statusMessage) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }
        
        public Builder addHeader(String name, String value) {
            this.headers.put(name, value);
            return this;
        }
        
        public Builder defaultHeaders() {
            this.headers.put(HttpConstants.CONTENT_TYPE_HEADER, HttpConstants.CONTENT_TYPE_TEXT_PLAIN);
            this.headers.put(HttpConstants.CONNECTION_HEADER, HttpConstants.CONNECTION_CLOSE);
            return this;
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public HttpResponse build() {
            return new HttpResponse(this);
        }
    }
}
