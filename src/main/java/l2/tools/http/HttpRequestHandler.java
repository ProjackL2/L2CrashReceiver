package l2.tools.http;

import l2.tools.config.ServerConfiguration;
import l2.tools.service.FileService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles individual HTTP requests for crash report uploads.
 */
public final class HttpRequestHandler implements Runnable {
    
    private static final Logger logger = Logger.getLogger(HttpRequestHandler.class.getName());
    
    private static final String POST_METHOD = "POST";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    
    // Expected form field names based on original implementation
    private static final String VERSION_FIELD = "CRVersion";
    private static final String ERROR_FIELD = "error";
    private static final String FILE_FIELD = "dumpfile";
    private static final String FILE_GAME_LOG = "gamelog";
    private static final String FILE_MEMORY_LOG = "memorylog";
    private static final String FILE_NETWORK_LOG = "networklog";
    
    private final Socket clientSocket;
    private final FileService fileService;
    private final ServerConfiguration configuration;
    
    public HttpRequestHandler(Socket clientSocket, FileService fileService, ServerConfiguration configuration) {
        this.clientSocket = clientSocket;
        this.fileService = fileService;
        this.configuration = configuration;
    }
    
    @Override
    public void run() {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        logger.info("Processing request from: " + clientAddress);
        
        try {
            // Set socket timeout for request processing
            clientSocket.setSoTimeout(configuration.getRequestTimeout());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to set socket timeout for " + clientAddress, e);
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            HttpRequest request = parseRequest(reader);
            HttpResponse response = processRequest(request, reader);
            
            response.writeTo(outputStream);
            
            logger.info("Request from " + clientAddress + " completed with status: " + response.getStatusCode());
            
        } catch (SocketTimeoutException e) {
            logger.log(Level.WARNING, "Request timeout from " + clientAddress, e);
            try (OutputStream outputStream = clientSocket.getOutputStream()) {
                HttpResponse.requestTimeout("Request timeout").writeTo(outputStream);
            } catch (IOException ioException) {
                logger.log(Level.SEVERE, "Failed to send timeout response", ioException);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing request from " + clientAddress, e);
            try (OutputStream outputStream = clientSocket.getOutputStream()) {
                HttpResponse.internalServerError("Internal server error").writeTo(outputStream);
            } catch (IOException ioException) {
                logger.log(Level.SEVERE, "Failed to send error response", ioException);
            }
        } finally {
            closeSocket();
        }
    }
    
    private HttpRequest parseRequest(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty request line");
        }

        // Validate request line length to prevent buffer overflow
        if (requestLine.length() > 2048) { // RFC 7230 suggests 8KB but we're more restrictive
            logger.warning("Request line too long: " + requestLine.length() + " bytes");
            throw new IllegalArgumentException("Request line too long: " + requestLine.length() + " bytes");
        }

        Map<String, String> headers = parseHeaders(reader);

        return new HttpRequest(requestLine, headers);
    }
    
    private Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        int totalHeaderSize = 0;
        int headerCount = 0;

        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            totalHeaderSize += headerLine.length() + 2; // +2 for \r\n
            // Check total header size
            if (totalHeaderSize > configuration.getMaxHeaderSize()) {
                logger.warning(String.format("Request headers too large: %d bytes (max: %d)", totalHeaderSize, configuration.getMaxHeaderSize()));
                throw new IllegalArgumentException(
                    String.format("Request headers too large: %d bytes (max: %d)",
                        totalHeaderSize, configuration.getMaxHeaderSize())
                );
            }

            // Check header count to prevent header bombing
            if (++headerCount > 50) { // Reasonable limit on number of headers
                logger.warning("Too many headers: " + headerCount);
                throw new IllegalArgumentException("Too many headers: " + headerCount);
            }

            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String key = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();

                // Validate header name and value
                if (key.isEmpty()) {
                    throw new IllegalArgumentException("Empty header name");
                }

                // Check for suspicious headers
                if (containsSuspiciousHeaderContent(key, value)) {
                    logger.warning("Suspicious header detected: " + key + ": " + value);
                    throw new IllegalArgumentException("Suspicious header content");
                }

                headers.put(key, value);
            }
        }
        
        return headers;
    }

    /**
     * Checks for suspicious content in HTTP headers that might indicate attacks.
     */
    private boolean containsSuspiciousHeaderContent(String key, String value) {
        String lowerKey = key.toLowerCase();
        String lowerValue = value.toLowerCase();

        // Check for script injection attempts
        String[] suspiciousPatterns = {
            "<script", "javascript:", "data:", "vbscript:", "onload=", "onerror=",
            "eval(", "expression(", "\\x", "\\u", "%3c", "%3e", "%22", "%27"
        };

        for (String pattern : suspiciousPatterns) {
            if (lowerValue.contains(pattern)) {
                return true;
            }
        }

        // Check for path traversal in certain headers
        if ((lowerKey.contains("file") || lowerKey.contains("path")) &&
            (value.contains("../") || value.contains("..\\"))) {
            return true;
        }

        return false;
    }
    
    private HttpResponse processRequest(HttpRequest request, BufferedReader reader) {
        try {
            // Validate request method
            if (!request.getMethod().equals(POST_METHOD)) {
                return HttpResponse.badRequest("Only POST method is allowed");
            }
            
            // Validate content type
            String contentType = request.getHeaders().get(CONTENT_TYPE_HEADER);
            if (contentType == null || !contentType.contains(MULTIPART_FORM_DATA)) {
                return HttpResponse.badRequest("Content-Type must be multipart/form-data");
            }
            
            // Extract boundary
            String boundary = MultipartParser.extractBoundary(contentType);
            if (boundary == null) {
                return HttpResponse.badRequest("Invalid or missing boundary in Content-Type");
            }
            
            // Validate and read request body with size limits
            byte[] body = readRequestBody(reader, request.getHeaders());
            if (body.length == 0) {
                return HttpResponse.badRequest("Empty request body");
            }
            
            // Parse multipart data
            MultipartParser.MultipartData multipartData = MultipartParser.parse(body, boundary);
            
            // Process crash report
            return processCrashReport(multipartData);
            
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Invalid request", e);
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing crash report", e);
            return HttpResponse.internalServerError("Failed to process crash report");
        }
    }
    
    private byte[] readRequestBody(BufferedReader reader, Map<String, String> headers) throws IOException {
        String contentLengthHeader = headers.get(CONTENT_LENGTH_HEADER);
        if (contentLengthHeader == null) {
            throw new IllegalArgumentException("Content-Length header is required");
        }
        
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthHeader);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Content-Length: " + contentLengthHeader);
        }
        
        if (contentLength <= 0) {
            throw new IllegalArgumentException("Content-Length must be positive");
        }

        // Validate request size against configuration
        if (contentLength > configuration.getMaxRequestSize()) {
            logger.warning(String.format("Request too large: %d bytes (max: %d) from %s",
                    contentLength, configuration.getMaxRequestSize(),
                    clientSocket.getInetAddress().getHostAddress()));
            throw new IllegalArgumentException(
                String.format("Request body too large: %d bytes (max: %d)",
                    contentLength, configuration.getMaxRequestSize())
            );
        }

        // Use a streaming approach for large requests to avoid memory issues
        if (contentLength > 1024 * 1024) { // 1MB threshold for streaming
            return readRequestBodyStreaming(reader, contentLength);
        } else {
            return readRequestBodyBuffered(reader, contentLength);
        }
    }

    /**
     * Reads request body using a buffered approach for smaller requests.
     */
    private byte[] readRequestBodyBuffered(BufferedReader reader, int contentLength) throws IOException {
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
    private byte[] readRequestBodyStreaming(BufferedReader reader, int contentLength) throws IOException {
        // Use a smaller buffer for streaming to control memory usage
        final int BUFFER_SIZE = 8192; // 8KB buffer
        char[] tempBuffer = new char[BUFFER_SIZE];
        StringBuilder bodyBuilder = new StringBuilder(contentLength);
        int totalRead = 0;

        while (totalRead < contentLength) {
            int toRead = Math.min(BUFFER_SIZE, contentLength - totalRead);
            int bytesRead = reader.read(tempBuffer, 0, toRead);

            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream while reading request body");
            }

            bodyBuilder.append(tempBuffer, 0, bytesRead);
            totalRead += bytesRead;

            // Additional safety check during streaming
            if (totalRead > configuration.getMaxRequestSize()) {
                throw new IllegalArgumentException("Request body exceeded maximum size during streaming");
            }
        }

        return bodyBuilder.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
    
    private HttpResponse processCrashReport(MultipartParser.MultipartData multipartData) {
        try {
            // Extract crash report components
            Optional<String> version = multipartData.getField(VERSION_FIELD);
            Optional<String> errorDescription = multipartData.getField(ERROR_FIELD);
            Optional<MultipartParser.FileData> dumpFile = multipartData.getFile(FILE_FIELD);
            Optional<MultipartParser.FileData> gameLogFile = multipartData.getFile(FILE_GAME_LOG);
            Optional<MultipartParser.FileData> memoryLogFile = multipartData.getFile(FILE_MEMORY_LOG);
            Optional<MultipartParser.FileData> networkLogFile = multipartData.getFile(FILE_NETWORK_LOG);
            
            // Log received data
            version.ifPresent(s -> logger.info("Crash report version: " + s));
            errorDescription.ifPresent(s -> logger.info("Error description received (" + s.length() + " characters)"));
            
            // Save dump file if present
            Path savedFile = null;
            if (dumpFile.isPresent()) {
                MultipartParser.FileData fileData = dumpFile.get();
                logger.info("Saving dump file: " + fileData.getFileName() + " (" + fileData.getSize() + " bytes)");
                savedFile = fileService.saveFile(fileData.getData(), fileData.getFileName());
                logger.info("Dump file saved to: " + savedFile);
            }

            if (savedFile == null) {
                return HttpResponse.badRequest("No crash data received");
            }

            String baseFileName = savedFile.getFileName().toString();
            
            // Save error description as text file if present and dump file was saved
            if (errorDescription.isPresent()) {
                Path descriptionFile = fileService.saveDescription(errorDescription.get(), baseFileName);
                logger.info("Description saved to: " + descriptionFile);
            }

            if (gameLogFile.isPresent()) {
                MultipartParser.FileData fileData = gameLogFile.get();
                logger.info("Saving dump file: " + fileData.getFileName() + " (" + fileData.getSize() + " bytes)");
                savedFile = fileService.saveFileWithPostfix(fileData.getData(), baseFileName, "_game.log");
                logger.info("Game log file saved to: " + savedFile);
            }

            if (memoryLogFile.isPresent()) {
                MultipartParser.FileData fileData = memoryLogFile.get();
                logger.info("Saving dump file: " + fileData.getFileName() + " (" + fileData.getSize() + " bytes)");
                savedFile = fileService.saveFileWithPostfix(fileData.getData(), baseFileName, "_mem.log");
                logger.info("Memory log file saved to: " + savedFile);
            }

            if (networkLogFile.isPresent()) {
                MultipartParser.FileData fileData = networkLogFile.get();
                logger.info("Saving dump file: " + fileData.getFileName() + " (" + fileData.getSize() + " bytes)");
                savedFile = fileService.saveFileWithPostfix(fileData.getData(), baseFileName, "_net.log");
                logger.info("Network log file saved to: " + savedFile);
            }
            
            return HttpResponse.ok("Crash report received successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save crash report", e);
            throw new RuntimeException("Failed to save crash report", e);
        }
    }
    
    private void closeSocket() {
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error closing client socket", e);
        }
    }
    
    /**
     * Simple HTTP request representation.
     */
    private static final class HttpRequest {
        private final String requestLine;
        private final String method;
        private final Map<String, String> headers;
        
        public HttpRequest(String requestLine, Map<String, String> headers) {
            this.requestLine = requestLine;
            this.headers = headers;
            this.method = parseMethod(requestLine);
        }
        
        private String parseMethod(String requestLine) {
            String[] parts = requestLine.split("\\s+");
            return parts.length > 0 ? parts[0] : "";
        }
        
        public String getMethod() {
            return method;
        }
        
        public Map<String, String> getHeaders() {
            return headers;
        }
        
        @Override
        public String toString() {
            return "HttpRequest{method='" + method + "', headers=" + headers.size() + "}";
        }
    }
}
