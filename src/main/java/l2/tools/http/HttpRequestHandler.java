package l2.tools.http;

import l2.tools.service.FileService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
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
    
    private final Socket clientSocket;
    private final FileService fileService;
    
    public HttpRequestHandler(Socket clientSocket, FileService fileService) {
        this.clientSocket = clientSocket;
        this.fileService = fileService;
    }
    
    @Override
    public void run() {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        logger.info("Processing request from: " + clientAddress);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            HttpRequest request = parseRequest(reader);
            HttpResponse response = processRequest(request, reader);
            
            response.writeTo(outputStream);
            
            logger.info("Request from " + clientAddress + " completed with status: " + response.getStatusCode());
            
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
        
        Map<String, String> headers = parseHeaders(reader);
        
        return new HttpRequest(requestLine, headers);
    }
    
    private Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String headerLine;
        
        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String key = headerLine.substring(0, colonIndex).trim();
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        
        return headers;
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
            
            // Read request body
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
        
        char[] buffer = new char[contentLength];
        int totalRead = 0;
        
        while (totalRead < contentLength) {
            int bytesRead = reader.read(buffer, totalRead, contentLength - totalRead);
            if (bytesRead == -1) {
                break;
            }
            totalRead += bytesRead;
        }
        
        return new String(buffer, 0, totalRead).getBytes(StandardCharsets.ISO_8859_1);
    }
    
    private HttpResponse processCrashReport(MultipartParser.MultipartData multipartData) {
        try {
            // Extract crash report components
            Optional<String> version = multipartData.getField(VERSION_FIELD);
            Optional<String> errorDescription = multipartData.getField(ERROR_FIELD);
            Optional<MultipartParser.FileData> dumpFile = multipartData.getFile(FILE_FIELD);
            
            // Log received data
            version.ifPresent(s -> logger.info("Crash report version: " + s));
            errorDescription.ifPresent(s -> logger.info("Error description received (" + s.length() + " characters)"));
            
            // Save dump file if present
            Path savedDumpFile = null;
            if (dumpFile.isPresent()) {
                MultipartParser.FileData fileData = dumpFile.get();
                logger.info("Saving dump file: " + fileData.getFileName() + " (" + fileData.getSize() + " bytes)");
                savedDumpFile = fileService.saveFile(fileData.getData(), fileData.getFileName());
                logger.info("Dump file saved to: " + savedDumpFile);
            }
            
            // Save error description as text file if present and dump file was saved
            if (errorDescription.isPresent() && savedDumpFile != null) {
                String baseFileName = savedDumpFile.getFileName().toString();
                Path descriptionFile = fileService.saveDescription(errorDescription.get(), baseFileName);
                logger.info("Description saved to: " + descriptionFile);
            }
            
            if (savedDumpFile == null && !errorDescription.isPresent()) {
                return HttpResponse.badRequest("No crash data received");
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
