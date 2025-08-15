package l2.tools.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration class for the L2 Crash Receiver server.
 * Contains all configurable parameters with sensible defaults.
 */
public final class ServerConfiguration {

    public static final String DEFAULT_HOST = "0.0.0.0";
    public static final int DEFAULT_PORT = 80;
    public static final String DEFAULT_UPLOAD_DIR = "crashes/";
    public static final int DEFAULT_MAX_FILE_SIZE = Integer.MAX_VALUE; // 4GB
    public static final int DEFAULT_THREAD_POOL_SIZE = 10;

    private final String host;
    private final int port;
    private final Path uploadDirectory;
    private final int maxFileSize;
    private final int threadPoolSize;
    
    private ServerConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.uploadDirectory = builder.uploadDirectory;
        this.maxFileSize = builder.maxFileSize;
        this.threadPoolSize = builder.threadPoolSize;
    }

    public static Builder builder() {
        return new Builder();
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
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public static final class Builder {
        private String host = DEFAULT_HOST;
        private int port = DEFAULT_PORT;
        private Path uploadDirectory = Paths.get(DEFAULT_UPLOAD_DIR);
        private int maxFileSize = DEFAULT_MAX_FILE_SIZE;
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
                ", threadPoolSize=" + threadPoolSize +
                '}';
    }
}
