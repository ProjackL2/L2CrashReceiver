package l2.tools.service;

import l2.tools.config.ServerConfig;
import l2.tools.exception.ProcessingException;
import l2.tools.exception.SecurityException;
import l2.tools.validation.FileNameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Low-level file system operations service.
 * Handles directory initialization, path resolution, and file system security.
 */
public final class FileSystemService {
    
    private static final Logger logger = Logger.getLogger(FileSystemService.class.getSimpleName());
    
    private final ServerConfig configuration;
    
    public FileSystemService(ServerConfig configuration) {
        this.configuration = configuration;
    }
    
    /**
     * Initializes the upload directory with proper permissions.
     */
    public void initializeUploadDirectory() throws ProcessingException {
        try {
            Path uploadDir = configuration.getUploadDirectory();
            
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                logger.info("Created upload directory: " + uploadDir);
            }
            
            validateUploadDirectory(uploadDir);
            
        } catch (IOException e) {
            throw new ProcessingException("Failed to initialize upload directory", e);
        }
    }
    
    /**
     * Validates upload directory properties.
     */
    private void validateUploadDirectory(Path uploadDir) throws ProcessingException {
        if (!Files.isDirectory(uploadDir)) {
            throw new ProcessingException("Upload path exists but is not a directory: " + uploadDir);
        }
        
        if (!Files.isWritable(uploadDir)) {
            throw new ProcessingException("Upload directory is not writable: " + uploadDir);
        }
    }
    
    /**
     * Resolves target path within upload directory with security validation.
     */
    public Path resolveTargetPath(String fileName) throws SecurityException {
        Path targetPath = configuration.getUploadDirectory().resolve(fileName);
        
        // Ensure the resolved path is within the upload directory
        Path normalizedTarget = targetPath.normalize();
        Path uploadDir = configuration.getUploadDirectory().normalize();
        
        if (!normalizedTarget.startsWith(uploadDir)) {
            logger.warning("Path traversal attempt detected: " + fileName);
            throw new SecurityException("Attempt to save file outside upload directory: " + normalizedTarget);
        }
        
        return normalizedTarget;
    }
    
    /**
     * Ensures filename uniqueness by appending timestamp if necessary.
     */
    public Path ensureUniqueFileName(Path targetPath) throws IOException {
        if (!Files.exists(targetPath)) {
            return targetPath;
        }
        
        String originalFileName = targetPath.getFileName().toString();
        String uniqueFileName = FileNameUtils.generateUniqueFileName(originalFileName);
        Path uniquePath = targetPath.getParent().resolve(uniqueFileName);
        
        logger.info(String.format("File already exists, using unique name: %s -> %s", 
            originalFileName, uniqueFileName));
        
        return uniquePath;
    }
}
