package l2.tools.service;

import l2.tools.config.ServerConfig;
import l2.tools.constant.Constants;
import l2.tools.exception.ProcessingException;
import l2.tools.exception.SecurityException;
import l2.tools.exception.ValidationException;
import l2.tools.monitoring.ServerMetrics;
import l2.tools.validation.FileNameUtils;
import l2.tools.validation.FileValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * High-level file management service for crash reports.
 * Coordinates file validation, naming, and storage operations.
 */
public final class FileManager {
    
    private static final Logger logger = Logger.getLogger(FileManager.class.getSimpleName());
    
    private final ServerConfig configuration;
    private final FileSystemService fileSystemService;
    
    public FileManager(ServerConfig configuration) {
        this.configuration = configuration;
        this.fileSystemService = new FileSystemService(configuration);
    }
    
    /**
     * Initializes the upload directory, creating it if it doesn't exist.
     */
    public void initializeUploadDirectory() throws ProcessingException {
        fileSystemService.initializeUploadDirectory();
    }
    
    /**
     * Saves file data with comprehensive validation and security checks.
     */
    public Path saveFile(byte[] data, String originalFileName) throws ProcessingException, SecurityException, ValidationException {
        try {
            // Validate file data and type
            FileValidator.validateFileData(data, configuration.getMaxFileSize());
            FileValidator.validateFileType(data, originalFileName);
            
            // Sanitize and resolve filename
            String sanitizedFileName = FileNameUtils.sanitizeFileName(originalFileName);
            Path targetPath = fileSystemService.resolveTargetPath(sanitizedFileName);
            
            // Ensure unique filename and save
            Path finalPath = fileSystemService.ensureUniqueFileName(targetPath);
            
            Files.write(finalPath, data, StandardOpenOption.CREATE_NEW);
            
            // Record file upload metrics
            ServerMetrics.getInstance().recordFileUpload(data.length);
            
            logger.info(String.format("File saved successfully: %s (%d bytes)", finalPath, data.length));
            return finalPath;
            
        } catch (IOException e) {
            throw new ProcessingException("Failed to save file: " + originalFileName, e);
        }
    }
    
    /**
     * Saves a file with a specific postfix in the filename.
     */
    public Path saveFileWithPostfix(byte[] data, String baseFileName, String postfix) throws ProcessingException, SecurityException, ValidationException {
        String modifiedFileName = FileNameUtils.replaceExtension(baseFileName, postfix);
        return saveFile(data, modifiedFileName);
    }
    
    /**
     * Saves description/error text as a UTF-8 text file.
     */
    public Path saveDescription(String description, String baseFileName) throws ProcessingException, SecurityException, ValidationException {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        
        String textFileName = FileNameUtils.replaceExtension(baseFileName, Constants.DESCRIPTION_POSTFIX);
        byte[] data = description.getBytes(StandardCharsets.UTF_8);
        
        return saveFile(data, textFileName);
    }
}
