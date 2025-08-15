package l2.tools;

import l2.tools.config.ServerConfiguration;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the L2 Crash Receiver application.
 * <p>
 * This application starts an HTTP server that accepts crash reports
 * from Lineage 2 clients and saves them to the local filesystem.
 * <p>
 * Usage: java l2.tools.L2CrashReceiver [host] [port] [upload_directory] [max_file_size]
 */
public final class L2CrashReceiver {
    
    private static final Logger logger = Logger.getLogger(L2CrashReceiver.class.getName());

    public static void main(String[] args) {
        try {
            ServerConfiguration configuration = parseArguments(args);
            
            logger.info("Starting L2 Crash Receiver with configuration: " + configuration);
            
            CrashReportServer server = new CrashReportServer(configuration);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server...");
                server.stop();
            }));
            
            // Start the server (this blocks until the server is stopped)
            server.start();
        } catch (IllegalArgumentException e) {
            logger.severe("Configuration error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
            System.exit(1);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error", e);
            System.exit(1);
        }
    }
    
    /**
     * Parses command line arguments and creates server configuration.
     * 
     * @param args command line arguments [port] [upload_directory]
     * @return configured ServerConfiguration
     */
    private static ServerConfiguration parseArguments(String[] args) {
        ServerConfiguration.Builder builder = ServerConfiguration.builder();

        if (args.length >= 1) {
            String host = args[0].trim();
            if (!host.isEmpty()) {
                builder.host(host);
            } else {
                throw new IllegalArgumentException("Host cannot be empty");
            }
        } else {
            builder.host(ServerConfiguration.DEFAULT_HOST);
        }

        if (args.length >= 2) {
            try {
                int port = Integer.parseInt(args[1]);
                builder.port(port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number: " + args[1]);
            }
        } else {
            builder.port(ServerConfiguration.DEFAULT_PORT);
        }
        
        if (args.length >= 3) {
            String uploadDir = args[2].trim();
            if (!uploadDir.isEmpty()) {
                builder.uploadDirectory(uploadDir);
            } else {
                throw new IllegalArgumentException("Upload directory cannot be empty");
            }
        } else {
            builder.uploadDirectory(ServerConfiguration.DEFAULT_UPLOAD_DIR);
        }

        if (args.length >= 4) {
            String maxFileSize = args[3].trim();
            if (!maxFileSize.isEmpty()) {
                builder.maxFileSize(Integer.parseInt(maxFileSize));
            } else {
                throw new IllegalArgumentException("Upload directory cannot be empty");
            }
        } else {
            builder.maxFileSize(ServerConfiguration.DEFAULT_MAX_FILE_SIZE);
        }
        
        if (args.length > 4) {
            logger.warning("Extra command line arguments ignored: " + java.util.Arrays.toString(java.util.Arrays.copyOfRange(args, 3, args.length)));
        }
        
        return builder.build();
    }
    
    private static void printUsage() {
        System.err.println("Usage: java l2.tools.L2CrashReceiver [port] [upload_directory]");
        System.err.println("  host: Host to listen on (default: " + ServerConfiguration.DEFAULT_HOST + ")");
        System.err.println("  port: Port number to listen on (default: " + ServerConfiguration.DEFAULT_PORT + ")");
        System.err.println("  upload_directory: Directory to save crash reports (default: " + ServerConfiguration.DEFAULT_UPLOAD_DIR + ")");
        System.err.println("  max_file_size: Max allowed to upload file size (default: " + ServerConfiguration.DEFAULT_MAX_FILE_SIZE + ")");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  java l2.tools.L2CrashReceiver");
        System.err.println("  java l2.tools.L2CrashReceiver 0.0.0.0 8080");
        System.err.println("  java l2.tools.L2CrashReceiver 0.0.0.0 8080 crashes/");
        System.err.println("  java l2.tools.L2CrashReceiver 0.0.0.0 8080 crashes/ 1024000");
    }
}