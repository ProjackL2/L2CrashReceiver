package l2.tools.http;

import l2.tools.config.ServerConfig;
import l2.tools.constant.HttpConstants;
import l2.tools.exception.ValidationException;
import l2.tools.monitoring.ServerMetrics;
import l2.tools.service.FileProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles individual HTTP requests for crash report uploads.
 * Provides secure request processing with comprehensive validation.
 */
public final class HttpRequestHandler implements Runnable {
    
    private static final Logger logger = Logger.getLogger(HttpRequestHandler.class.getSimpleName());
    
    private final Socket clientSocket;
    private final FileProcessor fileProcessor;
    private final ServerConfig configuration;
    
    public HttpRequestHandler(Socket clientSocket, FileProcessor fileProcessor, ServerConfig configuration) {
        this.clientSocket = clientSocket;
        this.fileProcessor = fileProcessor;
        this.configuration = configuration;
    }
    
    @Override
    public void run() {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        logger.info("Processing request from: " + clientAddress);
        
        // Record request metrics
        ServerMetrics.getInstance().recordRequest();
        
        try {
            // Set socket timeout for request processing
            clientSocket.setSoTimeout(configuration.getRequestTimeout());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to set socket timeout for " + clientAddress, e);
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            HttpRequest request = HttpParser.parseRequest(reader, configuration.getMaxHeaderSize());
            HttpResponse response = processRequest(request, reader, clientAddress);
            
            response.writeTo(outputStream);
            
            // Record success/failure metrics
            if (response.getStatusCode() == HttpConstants.STATUS_OK) {
                ServerMetrics.getInstance().recordSuccess();
            } else {
                ServerMetrics.getInstance().recordFailure();
            }
            
            logger.info("Request from " + clientAddress + " completed with status: " + response.getStatusCode());
            
        } catch (SocketTimeoutException e) {
            logger.log(Level.WARNING, "Request timeout from " + clientAddress, e);
            try (OutputStream outputStream = clientSocket.getOutputStream()) {
                HttpResponse.requestTimeout("Request timeout").writeTo(outputStream);
            } catch (IOException ioException) {
                logger.log(Level.SEVERE, "Failed to send timeout response", ioException);
            }
        } catch (ValidationException e) {
            logger.log(Level.WARNING, "Validation error from " + clientAddress, e);
            ServerMetrics.getInstance().recordValidationError();
            ServerMetrics.getInstance().recordFailure();
            try (OutputStream outputStream = clientSocket.getOutputStream()) {
                HttpResponse.badRequest(e.getMessage()).writeTo(outputStream);
            } catch (IOException ioException) {
                logger.log(Level.SEVERE, "Failed to send validation error response", ioException);
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
    
    private HttpResponse processRequest(HttpRequest request, BufferedReader reader, String clientAddress) {
        try {
            // Validate request method
            if (!request.isPost()) {
                return HttpResponse.badRequest("Only POST method is allowed");
            }
            
            // Validate content type
            if (!request.isMultipartFormData()) {
                return HttpResponse.badRequest("Content-Type must be multipart/form-data");
            }
            
            // Extract boundary
            String boundary = MultipartParser.extractBoundary(request.getContentType());
            if (boundary == null) {
                return HttpResponse.badRequest("Invalid or missing boundary in Content-Type");
            }
            
            // Read and validate request body
            byte[] body = HttpParser.readRequestBody(reader, request, configuration.getMaxRequestSize(), clientAddress);
            if (body.length == 0) {
                return HttpResponse.badRequest("Empty request body");
            }
            
            // Parse multipart data
            MultipartParser.MultipartData data = MultipartParser.parse(body, boundary);
            
            // Process crash report
            return fileProcessor.processCrashReport(data);
            
        } catch (ValidationException e) {
            logger.log(Level.WARNING, "Request validation failed", e);
            return HttpResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing crash report", e);
            return HttpResponse.internalServerError("Failed to process crash report");
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
}
