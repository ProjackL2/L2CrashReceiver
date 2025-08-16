package l2.tools.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration class for the L2 Crash Receiver server.
 * Contains all configurable parameters with sensible defaults.
 */
public final class ServerConfiguration {

    private static final Logger logger = Logger.getLogger(ServerConfiguration.class.getName());
    
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 80;
    public static final String DEFAULT_UPLOAD_DIR = "crashes/";
    public static final int DEFAULT_MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB - reasonable for crash dumps
    public static final int DEFAULT_MAX_REQUEST_SIZE = 60 * 1024 * 1024; // 60MB - slightly larger than max file size
    public static final int DEFAULT_MAX_HEADER_SIZE = 8 * 1024; // 8KB for headers
    public static final int DEFAULT_REQUEST_TIMEOUT = 30 * 1000; // 30 seconds
    public static final int DEFAULT_THREAD_POOL_SIZE = 10;

    private final String host;
    private final int port;
    private final Path uploadDirectory;
    private final int maxFileSize;
    private final int maxRequestSize;
    private final int maxHeaderSize;
    private final int requestTimeout;
    private final int threadPoolSize;
    
    private ServerConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.uploadDirectory = builder.uploadDirectory;
        this.maxFileSize = builder.maxFileSize;
        this.maxRequestSize = builder.maxRequestSize;
        this.maxHeaderSize = builder.maxHeaderSize;
        this.requestTimeout = builder.requestTimeout;
        this.threadPoolSize = builder.threadPoolSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a ServerConfiguration from a properties file located in the classpath.
     * 
     * @param propertiesFileName the name of the properties file (e.g., "server.properties")
     * @return configured ServerConfiguration with properties loaded from file
     * @throws IOException if the properties file cannot be loaded
     */
    public static ServerConfiguration fromProperties(String propertiesFileName) throws IOException {
        Properties properties = loadProperties(propertiesFileName);
        return fromProperties(properties);
    }

    /**
     * Creates a ServerConfiguration from a Properties object.
     * Missing properties will use default values.
     * 
     * @param properties the Properties object containing configuration
     * @return configured ServerConfiguration
     */
    public static ServerConfiguration fromProperties(Properties properties) {
        Builder builder = builder();
        
        // Load host
        String host = properties.getProperty("server.host", DEFAULT_HOST);
        if (!host.trim().isEmpty()) {
            builder.host(host.trim());
        }
        
        // Load port
        String portStr = properties.getProperty("server.port", String.valueOf(DEFAULT_PORT));
        try {
            int port = Integer.parseInt(portStr.trim());
            builder.port(port);
        } catch (NumberFormatException e) {
            logger.warning("Invalid port value in properties: " + portStr + ", using default: " + DEFAULT_PORT);
            builder.port(DEFAULT_PORT);
        }
        
        // Load upload directory
        String uploadDir = properties.getProperty("server.upload.directory", DEFAULT_UPLOAD_DIR);
        if (!uploadDir.trim().isEmpty()) {
            builder.uploadDirectory(uploadDir.trim());
        }
        
        // Load max file size
        String maxFileSizeStr = properties.getProperty("server.upload.max.file.size", String.valueOf(DEFAULT_MAX_FILE_SIZE));
        try {
            int maxFileSize = Integer.parseInt(maxFileSizeStr.trim());
            builder.maxFileSize(maxFileSize);
        } catch (NumberFormatException e) {
            logger.warning("Invalid max file size value in properties: " + maxFileSizeStr + ", using default: " + DEFAULT_MAX_FILE_SIZE);
            builder.maxFileSize(DEFAULT_MAX_FILE_SIZE);
        }
        
        // Load max request size
        String maxRequestSizeStr = properties.getProperty("server.upload.max.request.size", String.valueOf(DEFAULT_MAX_REQUEST_SIZE));
        try {
            int maxRequestSize = Integer.parseInt(maxRequestSizeStr.trim());
            builder.maxRequestSize(maxRequestSize);
        } catch (NumberFormatException e) {
            logger.warning("Invalid max request size value in properties: " + maxRequestSizeStr + ", using default: " + DEFAULT_MAX_REQUEST_SIZE);
            builder.maxRequestSize(DEFAULT_MAX_REQUEST_SIZE);
        }
        
        // Load max header size
        String maxHeaderSizeStr = properties.getProperty("server.http.max.header.size", String.valueOf(DEFAULT_MAX_HEADER_SIZE));
        try {
            int maxHeaderSize = Integer.parseInt(maxHeaderSizeStr.trim());
            builder.maxHeaderSize(maxHeaderSize);
        } catch (NumberFormatException e) {
            logger.warning("Invalid max header size value in properties: " + maxHeaderSizeStr + ", using default: " + DEFAULT_MAX_HEADER_SIZE);
            builder.maxHeaderSize(DEFAULT_MAX_HEADER_SIZE);
        }
        
        // Load request timeout
        String requestTimeoutStr = properties.getProperty("server.http.request.timeout", String.valueOf(DEFAULT_REQUEST_TIMEOUT));
        try {
            int requestTimeout = Integer.parseInt(requestTimeoutStr.trim());
            builder.requestTimeout(requestTimeout);
        } catch (NumberFormatException e) {
            logger.warning("Invalid request timeout value in properties: " + requestTimeoutStr + ", using default: " + DEFAULT_REQUEST_TIMEOUT);
            builder.requestTimeout(DEFAULT_REQUEST_TIMEOUT);
        }
        
        // Load thread pool size
        String threadPoolSizeStr = properties.getProperty("server.thread.pool.size", String.valueOf(DEFAULT_THREAD_POOL_SIZE));
        try {
            int threadPoolSize = Integer.parseInt(threadPoolSizeStr.trim());
            builder.threadPoolSize(threadPoolSize);
        } catch (NumberFormatException e) {
            logger.warning("Invalid thread pool size value in properties: " + threadPoolSizeStr + ", using default: " + DEFAULT_THREAD_POOL_SIZE);
            builder.threadPoolSize(DEFAULT_THREAD_POOL_SIZE);
        }
        
        return builder.build();
    }

    /**
     * Loads properties from a classpath resource.
     * 
     * @param propertiesFileName the name of the properties file
     * @return Properties object loaded from the file
     * @throws IOException if the file cannot be loaded
     */
    private static Properties loadProperties(String propertiesFileName) throws IOException {
        Properties properties = new Properties();
        
        try (InputStream inputStream = ServerConfiguration.class.getClassLoader()
                .getResourceAsStream(propertiesFileName)) {
            
            if (inputStream == null) {
                throw new IOException("Properties file not found in classpath: " + propertiesFileName);
            }
            
            properties.load(inputStream);
            logger.info("Successfully loaded configuration from: " + propertiesFileName);
        }
        
        return properties;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
    
    public Path getUploadDirectory() {
        return uploadDirectory;
    }
    
    public int getMaxFileSize() {
        return maxFileSize;
    }
    
    public int getMaxRequestSize() {
        return maxRequestSize;
    }
    
    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }
    
    public int getRequestTimeout() {
        return requestTimeout;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public static final class Builder {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private Path uploadDirectory = Paths.get(DEFAULT_UPLOAD_DIR);
        private int maxFileSize = DEFAULT_MAX_FILE_SIZE;
        private int maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
        private int maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
        private int requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

        public Builder host(String host) {
            if (host.isEmpty()) {
                throw new IllegalArgumentException("Host must not empty");
            }
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }
        
        public Builder uploadDirectory(String uploadDirectory) {
            if (uploadDirectory == null || uploadDirectory.trim().isEmpty()) {
                throw new IllegalArgumentException("Upload directory cannot be null or empty");
            }
            this.uploadDirectory = Paths.get(uploadDirectory);
            return this;
        }
        
        public Builder maxFileSize(int maxFileSize) {
            if (maxFileSize <= 0) {
                throw new IllegalArgumentException("Max file size must be positive");
            }
            this.maxFileSize = maxFileSize;
            return this;
        }
        
        public Builder maxRequestSize(int maxRequestSize) {
            if (maxRequestSize <= 0) {
                throw new IllegalArgumentException("Max request size must be positive");
            }
            this.maxRequestSize = maxRequestSize;
            return this;
        }
        
        public Builder maxHeaderSize(int maxHeaderSize) {
            if (maxHeaderSize <= 0) {
                throw new IllegalArgumentException("Max header size must be positive");
            }
            this.maxHeaderSize = maxHeaderSize;
            return this;
        }
        
        public Builder requestTimeout(int requestTimeout) {
            if (requestTimeout <= 0) {
                throw new IllegalArgumentException("Request timeout must be positive");
            }
            this.requestTimeout = requestTimeout;
            return this;
        }
        
        public Builder threadPoolSize(int threadPoolSize) {
            if (threadPoolSize <= 0) {
                throw new IllegalArgumentException("Thread pool size must be positive");
            }
            this.threadPoolSize = threadPoolSize;
            return this;
        }
        
        public ServerConfiguration build() {
            return new ServerConfiguration(this);
        }
    }
    
    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "host=" + host +
                ", port=" + port +
                ", uploadDirectory=" + uploadDirectory +
                ", maxFileSize=" + maxFileSize +
                ", maxRequestSize=" + maxRequestSize +
                ", maxHeaderSize=" + maxHeaderSize +
                ", requestTimeout=" + requestTimeout +
                ", threadPoolSize=" + threadPoolSize +
                '}';
    }
}
