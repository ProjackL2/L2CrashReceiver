package l2.tools.monitoring;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Simple metrics collection for monitoring server performance.
 * Thread-safe implementation using atomic operations.
 */
public final class ServerMetrics {
    
    private static final Logger logger = Logger.getLogger(ServerMetrics.class.getSimpleName());
    
    private static final ServerMetrics INSTANCE = new ServerMetrics();
    
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong bytesUploaded = new AtomicLong(0);
    private final AtomicLong filesUploaded = new AtomicLong(0);
    private final AtomicLong securityViolations = new AtomicLong(0);
    private final AtomicLong validationErrors = new AtomicLong(0);
    
    private volatile LocalDateTime serverStartTime;
    
    private ServerMetrics() {
        // Singleton pattern
    }
    
    public static ServerMetrics getInstance() {
        return INSTANCE;
    }
    
    /**
     * Marks server start time.
     */
    public void serverStarted() {
        this.serverStartTime = LocalDateTime.now();
    }
    
    /**
     * Records a new request.
     */
    public void recordRequest() {
        totalRequests.incrementAndGet();
    }
    
    /**
     * Records a successful request.
     */
    public void recordSuccess() {
        successfulRequests.incrementAndGet();
    }
    
    /**
     * Records a failed request.
     */
    public void recordFailure() {
        failedRequests.incrementAndGet();
    }
    
    /**
     * Records file upload.
     */
    public void recordFileUpload(long fileSize) {
        filesUploaded.incrementAndGet();
        bytesUploaded.addAndGet(fileSize);
    }
    
    /**
     * Records security violation.
     */
    public void recordSecurityViolation() {
        securityViolations.incrementAndGet();
    }
    
    /**
     * Records validation error.
     */
    public void recordValidationError() {
        validationErrors.incrementAndGet();
    }
    
    /**
     * Gets total number of requests.
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    /**
     * Gets number of successful requests.
     */
    public long getSuccessfulRequests() {
        return successfulRequests.get();
    }
    
    /**
     * Gets number of failed requests.
     */
    public long getFailedRequests() {
        return failedRequests.get();
    }
    
    /**
     * Gets total bytes uploaded.
     */
    public long getBytesUploaded() {
        return bytesUploaded.get();
    }
    
    /**
     * Gets total files uploaded.
     */
    public long getFilesUploaded() {
        return filesUploaded.get();
    }
    
    /**
     * Gets security violations count.
     */
    public long getSecurityViolations() {
        return securityViolations.get();
    }
    
    /**
     * Gets validation errors count.
     */
    public long getValidationErrors() {
        return validationErrors.get();
    }
    
    /**
     * Gets server uptime since start.
     */
    public LocalDateTime getServerStartTime() {
        return serverStartTime;
    }
    
    /**
     * Calculates success rate as a percentage.
     */
    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successfulRequests.get() / total * 100.0;
    }
    
    /**
     * Logs current metrics summary.
     */
    public void logMetricsSummary() {
        logger.info(String.format(
            "Metrics Summary - Total Requests: %d, Successful: %d (%.1f%%), Failed: %d, " +
            "Files Uploaded: %d, Bytes Uploaded: %d, Security Violations: %d, Validation Errors: %d",
            getTotalRequests(), getSuccessfulRequests(), getSuccessRate(), getFailedRequests(),
            getFilesUploaded(), getBytesUploaded(), getSecurityViolations(), getValidationErrors()
        ));
    }
    
    /**
     * Resets all metrics (for testing purposes).
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        bytesUploaded.set(0);
        filesUploaded.set(0);
        securityViolations.set(0);
        validationErrors.set(0);
        serverStartTime = null;
    }
}
