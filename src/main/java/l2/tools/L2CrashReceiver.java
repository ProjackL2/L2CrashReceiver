package l2.tools;

import l2.tools.config.ServerConfig;
import l2.tools.exception.ProcessingException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * Main entry point for the L2 Crash Receiver application.
 * <p>
 * This application starts an HTTP server that accepts crash reports
 * from Lineage 2 clients and saves them to the local filesystem.
 * <p>
 * Configuration is loaded from server.properties file in the classpath.
 * If the properties file is not found, default values will be used.
 * <p>
 * Usage: java l2.tools.L2CrashReceiver
 */
public final class L2CrashReceiver {
    
    private static final Logger logger = Logger.getLogger(L2CrashReceiver.class.getSimpleName());

    public static void main(String[] args) {
        initializeLogging();
        
        try {
            logger.info("Starting L2 Crash Receiver\n");

            ServerConfig configuration = loadConfiguration();
            logger.info("Configuration: " + configuration);

            CrashReportServer server = new CrashReportServer(configuration);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server...");
                server.stop();
            }));
            server.start();
        } catch (IllegalArgumentException e) {
            logger.severe("Configuration error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
            System.exit(1);
        } catch (ProcessingException e) {
            logger.log(Level.SEVERE, "File system initialization failed", e);
            System.exit(1);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error", e);
            System.exit(1);
        }
    }
    
    /**
     * Initializes logging configuration by loading logging.properties from classpath.
     */
    private static void initializeLogging() {
        try {
            Path logDir = Paths.get("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            
            InputStream configStream = L2CrashReceiver.class.getClassLoader()
                .getResourceAsStream("logging.properties");
            
            if (configStream != null) {
                LogManager.getLogManager().readConfiguration(configStream);
                configStream.close();
            } else {
                System.err.println("Warning: Could not find logging.properties in classpath, using default logging configuration");
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load logging configuration: " + e.getMessage());
        }
    }
    
    /**
     * Loads server configuration from properties file with fallback to defaults.
     * 
     * @return configured ServerConfiguration
     * @throws IOException if configuration loading fails completely
     */
    private static ServerConfig loadConfiguration() throws IOException {
        try {
            return ServerConfig.fromProperties("server.properties");
        } catch (IOException e) {
            logger.warning("Could not load server.properties from classpath: " + e.getMessage());
            logger.info("Using default configuration values");

            return ServerConfig.builder().build();
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: L2CrashReceiver");
        System.err.println();
        System.err.println("Configuration is loaded from server.properties file in the classpath.");
        System.err.println("If server.properties is not found, the following default values are used:");
        System.err.println("  server.host: " + ServerConfig.DEFAULT_HOST);
        System.err.println("  server.port: " + ServerConfig.DEFAULT_PORT);
        System.err.println("  server.upload.directory: " + ServerConfig.DEFAULT_UPLOAD_DIR);
        System.err.println("  server.upload.max.file.size: " + ServerConfig.DEFAULT_MAX_FILE_SIZE + " bytes");
        System.err.println("  server.upload.max.request.size: " + ServerConfig.DEFAULT_MAX_REQUEST_SIZE + " bytes");
        System.err.println("  server.http.max.header.size: " + ServerConfig.DEFAULT_MAX_HEADER_SIZE + " bytes");
        System.err.println("  server.http.request.timeout: " + ServerConfig.DEFAULT_REQUEST_TIMEOUT + " ms");
        System.err.println("  server.thread.pool.size: " + ServerConfig.DEFAULT_THREAD_POOL_SIZE);
        System.err.println();
        System.err.println("Create a server.properties file to customize these values.");
    }
}