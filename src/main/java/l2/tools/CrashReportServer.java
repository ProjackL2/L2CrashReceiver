package l2.tools;

import l2.tools.config.ServerConfig;
import l2.tools.exception.ProcessingException;
import l2.tools.http.HttpRequestHandler;
import l2.tools.monitoring.ServerMetrics;
import l2.tools.service.FileProcessor;
import l2.tools.service.FileManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server for receiving and processing L2 crash reports.
 * <p>
 * This server accepts POST requests with multipart/form-data containing:
 * - CRVersion: Version information
 * - error: Error description text
 * - dumpfile: The actual dump file
 * - gamelog: Game log file(l2.log)
 * - networklog: Network log file(Network.log)
 */

public final class CrashReportServer {
    
    private static final Logger logger = Logger.getLogger(CrashReportServer.class.getSimpleName());
    
    private final ServerConfig configuration;
    private final FileManager fileManager;
    private final FileProcessor fileProcessor;
    private final ExecutorService executorService;
    
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    
    public CrashReportServer(ServerConfig configuration) {
        this.configuration = configuration;
        this.fileManager = new FileManager(configuration);
        this.fileProcessor = new FileProcessor(fileManager);
        this.executorService = Executors.newFixedThreadPool(configuration.getThreadPoolSize());
    }
    
    /**
     * Starts the server and begins accepting connections.
     * This method blocks until the server is stopped.
     */
    public void start() throws IOException, ProcessingException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        logger.info("Configuration: " + configuration);
        
        // Initialize file manager
        fileManager.initializeUploadDirectory();
        logger.info("Upload directory initialized: " + configuration.getUploadDirectory());
        
        // Create server socket
        serverSocket = new ServerSocket(configuration.getPort(), 50, InetAddress.getByName(configuration.getHost()));

        running = true;
        
        // Start metrics tracking
        ServerMetrics.getInstance().serverStarted();
        
        logger.info("Crash Report Server started on " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort());
        
        // Accept connections
        try {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (running) {
                        HttpRequestHandler handler = new HttpRequestHandler(clientSocket, fileProcessor, configuration);
                        executorService.submit(handler);
                    } else {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.WARNING, "Error accepting client connection", e);
                    }
                }
            }
        } finally {
            cleanup();
        }
    }
    
    /**
     * Stops the server gracefully.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        logger.info("Stopping Crash Report Server...");
        running = false;
        
        // Close server socket to stop accepting new connections
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing server socket", e);
            }
        }
        
        cleanup();
    }
    
    private void cleanup() {
        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Executor service did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
                
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.severe("Executor service did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            logger.warning("Interrupted while waiting for executor service termination");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Log final metrics summary
        ServerMetrics.getInstance().logMetricsSummary();
        
        logger.info("Crash Report Server stopped");
    }
}
