package l2.tools.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTTP response with status code, headers, and body.
 */
public final class HttpResponse {
    
    private final int statusCode;
    private final String statusMessage;
    private final Map<String, String> headers;
    private final String body;
    
    private HttpResponse(int statusCode, String statusMessage, Map<String, String> headers, String body) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = new HashMap<>(headers);
        this.body = body != null ? body : "";
    }
    
    public static HttpResponse ok() {
        return new HttpResponse(200, "OK", getDefaultHeaders(), "");
    }
    
    public static HttpResponse ok(String body) {
        return new HttpResponse(200, "OK", getDefaultHeaders(), body);
    }
    
    public static HttpResponse badRequest(String message) {
        return new HttpResponse(400, "Bad Request", getDefaultHeaders(), message);
    }
    
    public static HttpResponse payloadTooLarge(String message) {
        return new HttpResponse(413, "Payload Too Large", getDefaultHeaders(), message);
    }
    
    public static HttpResponse internalServerError(String message) {
        return new HttpResponse(500, "Internal Server Error", getDefaultHeaders(), message);
    }
    
    private static Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=utf-8");
        headers.put("Connection", "close");
        return headers;
    }
    
    /**
     * Writes this HTTP response to the given output stream.
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        
        // Status line
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        
        // Headers
        Map<String, String> allHeaders = new HashMap<>(headers);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        allHeaders.put("Content-Length", String.valueOf(bodyBytes.length));
        
        for (Map.Entry<String, String> header : allHeaders.entrySet()) {
            response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        
        // Empty line between headers and body
        response.append("\r\n");
        
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
    
    public String getBody() {
        return body;
    }
    
    @Override
    public String toString() {
        return "HttpResponse{" +
                "statusCode=" + statusCode +
                ", statusMessage='" + statusMessage + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}
