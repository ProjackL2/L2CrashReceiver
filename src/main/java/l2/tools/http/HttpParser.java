package l2.tools.http;

import l2.tools.constant.FileConstants;
import l2.tools.constant.HttpConstants;
import l2.tools.exception.ValidationException;
import l2.tools.validation.HttpValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses HTTP requests from input streams.
 * Provides validation and security checks during parsing.
 */
public final class HttpParser {
    
    private static final Logger logger = Logger.getLogger(HttpParser.class.getSimpleName());
    
    /**
     * Parses an HTTP request from a BufferedReader.
     */
    public static HttpRequest parseRequest(BufferedReader reader, int maxHeaderSize) throws IOException, ValidationException {
        String requestLine = reader.readLine();
        HttpValidator.validateRequestLine(requestLine);
        
        Map<String, String> headers = parseHeaders(reader, maxHeaderSize);
        
        return HttpRequest.builder()
            .requestLine(requestLine)
            .headers(headers)
            .build();
    }
    
    /**
     * Parses HTTP headers with security validation.
     */
    private static Map<String, String> parseHeaders(BufferedReader reader, int maxHeaderSize) throws IOException, ValidationException {
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        int totalHeaderSize = 0;
        int headerCount = 0;

        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            totalHeaderSize += headerLine.length() + 2; // +2 for \r\n
            
            HttpValidator.validateHeaderSize(totalHeaderSize, maxHeaderSize);
            HttpValidator.validateHeaderCount(++headerCount);

            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String key = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();

                HttpValidator.validateHeader(key, value);
                headers.put(key, value);
            }
        }
        
        return headers;
    }
    
    /**
     * Reads request body with size validation.
     */
    public static byte[] readRequestBody(BufferedReader reader, HttpRequest request, 
                                       int maxRequestSize, String clientAddress) throws IOException, ValidationException {
        int contentLength = HttpValidator.validateContentLength(request.getHeader(HttpConstants.CONTENT_LENGTH_HEADER));
        HttpValidator.validateRequestSize(contentLength, maxRequestSize, clientAddress);

        // Use streaming approach for large requests
        if (contentLength > FileConstants.STREAMING_THRESHOLD) {
            return readRequestBodyStreaming(reader, contentLength, maxRequestSize);
        } else {
            return readRequestBodyBuffered(reader, contentLength);
        }
    }
    
    /**
     * Reads request body using a buffered approach for smaller requests.
     */
    private static byte[] readRequestBodyBuffered(BufferedReader reader, int contentLength) throws IOException {
        char[] buffer = new char[contentLength];
        int totalRead = 0;
        
        while (totalRead < contentLength) {
            int bytesRead = reader.read(buffer, totalRead, contentLength - totalRead);
            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream while reading request body");
            }
            totalRead += bytesRead;
        }
        
        return new String(buffer, 0, totalRead).getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Reads request body using a streaming approach for larger requests.
     */
    private static byte[] readRequestBodyStreaming(BufferedReader reader, int contentLength, int maxRequestSize) throws IOException {
        char[] tempBuffer = new char[FileConstants.STREAMING_BUFFER_SIZE];
        StringBuilder bodyBuilder = new StringBuilder(contentLength);
        int totalRead = 0;

        while (totalRead < contentLength) {
            int toRead = Math.min(FileConstants.STREAMING_BUFFER_SIZE, contentLength - totalRead);
            int bytesRead = reader.read(tempBuffer, 0, toRead);

            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream while reading request body");
            }

            bodyBuilder.append(tempBuffer, 0, bytesRead);
            totalRead += bytesRead;

            // Additional safety check during streaming
            if (totalRead > maxRequestSize) {
                throw new IOException("Request body exceeded maximum size during streaming");
            }
        }

        return bodyBuilder.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
    
    private HttpParser() {
        // Utility class - prevent instantiation
    }
}
