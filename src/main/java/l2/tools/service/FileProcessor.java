package l2.tools.service;

import l2.tools.constant.Constants;
import l2.tools.exception.ProcessingException;
import l2.tools.exception.SecurityException;
import l2.tools.exception.ValidationException;
import l2.tools.http.HttpResponse;
import l2.tools.http.MultipartParser;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Processes crash reports and coordinates file saving operations.
 * Handles the business logic for crash report processing.
 */
public final class FileProcessor {
    
    private static final Logger logger = Logger.getLogger(FileProcessor.class.getSimpleName());
    
    private final FileManager fileManager;
    
    public FileProcessor(FileManager fileManager) {
        this.fileManager = fileManager;
    }
    
    /**
     * Processes a complete crash report from multipart data.
     */
    public HttpResponse processCrashReport(MultipartParser.MultipartData multipartData) {
        try {
            CrashReportData reportData = extractCrashReportData(multipartData);

            Path savedFile = savePrimaryFile(reportData);
            if (savedFile == null) {
                return HttpResponse.badRequest("No crash data received");
            }
            
            String baseFileName = savedFile.getFileName().toString();
            saveAdditionalFiles(reportData, baseFileName);
            
            return HttpResponse.ok("Crash report received successfully");
        } catch (SecurityException e) {
            logger.warning("Security violation during crash report processing: " + e.getMessage());
            return HttpResponse.badRequest("Security violation: " + e.getMessage());
        } catch (ProcessingException e) {
            logger.severe("File processing error: " + e.getMessage());
            return HttpResponse.internalServerError("Failed to process crash report");
        } catch (Exception e) {
            logger.severe("Unexpected error processing crash report: " + e.getMessage());
            return HttpResponse.internalServerError("Internal processing error");
        }
    }
    
    /**
     * Extracts structured data from multipart form data.
     */
    private CrashReportData extractCrashReportData(MultipartParser.MultipartData data) {
        CrashReportData.Builder builder = CrashReportData.builder();
        data.getField(Constants.VERSION_FIELD).ifPresent(builder::version);
        data.getField(Constants.ERROR_FIELD).ifPresent(builder::errorDescription);
        data.getFile(Constants.DUMP_FILE_FIELD).ifPresent(builder::dumpFile);
        data.getFile(Constants.GAME_LOG_FIELD).ifPresent(builder::gameLogFile);
        data.getFile(Constants.NETWORK_LOG_FIELD).ifPresent(builder::networkLogFile);
        return builder.build();
    }
    
    /**
     * Saves the primary dump file.
     */
    private Path savePrimaryFile(CrashReportData reportData) throws ProcessingException, SecurityException, ValidationException {
        if (!reportData.getDumpFile().isPresent()) {
            return null;
        }
        
        MultipartParser.FileData data = reportData.getDumpFile().get();

        Path savedFile = fileManager.saveFile(data.getData(), data.getFileName());
        logger.info("Dump file saved to: " + savedFile);
        
        return savedFile;
    }
    
    /**
     * Saves additional files and descriptions.
     */
    private void saveAdditionalFiles(CrashReportData reportData, String baseFileName) throws ProcessingException, SecurityException, ValidationException {
        // Save error description
        if (reportData.getErrorDescription().isPresent()) {
            Path descriptionFile = fileManager.saveDescription(reportData.getErrorDescription().get(), baseFileName);
            logger.info("Description saved to: " + descriptionFile);
        }
        
        // Save game log file
        if (reportData.getGameLogFile().isPresent()) {
            MultipartParser.FileData gameLogData = reportData.getGameLogFile().get();
            logger.info(String.format("Saving game log file: %s (%d bytes)", gameLogData.getFileName(), gameLogData.getSize()));
            
            Path gameLogFile = fileManager.saveFileWithPostfix(gameLogData.getData(), baseFileName, Constants.GAME_LOG_POSTFIX);
            logger.info("Game log file saved to: " + gameLogFile);
        }
        
        // Save network log file
        if (reportData.getNetworkLogFile().isPresent()) {
            MultipartParser.FileData networkLogData = reportData.getNetworkLogFile().get();
            logger.info(String.format("Saving network log file: %s (%d bytes)", networkLogData.getFileName(), networkLogData.getSize()));
            
            Path networkLogFile = fileManager.saveFileWithPostfix(networkLogData.getData(), baseFileName, Constants.NETWORK_LOG_POSTFIX);
            logger.info("Network log file saved to: " + networkLogFile);
        }
    }
    
    /**
     * Data structure to hold crash report components.
     */
    private static final class CrashReportData {
        private final Optional<String> version;
        private final Optional<String> errorDescription;
        private final Optional<MultipartParser.FileData> dumpFile;
        private final Optional<MultipartParser.FileData> gameLogFile;
        private final Optional<MultipartParser.FileData> networkLogFile;
        
        private CrashReportData(Builder builder) {
            this.version = Optional.ofNullable(builder.version);
            this.errorDescription = Optional.ofNullable(builder.errorDescription);
            this.dumpFile = Optional.ofNullable(builder.dumpFile);
            this.gameLogFile = Optional.ofNullable(builder.gameLogFile);
            this.networkLogFile = Optional.ofNullable(builder.networkLogFile);
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public Optional<String> getVersion() { return version; }
        public Optional<String> getErrorDescription() { return errorDescription; }
        public Optional<MultipartParser.FileData> getDumpFile() { return dumpFile; }
        public Optional<MultipartParser.FileData> getGameLogFile() { return gameLogFile; }
        public Optional<MultipartParser.FileData> getNetworkLogFile() { return networkLogFile; }
        
        private static final class Builder {
            private String version;
            private String errorDescription;
            private MultipartParser.FileData dumpFile;
            private MultipartParser.FileData gameLogFile;
            private MultipartParser.FileData networkLogFile;
            
            public Builder version(String version) {
                this.version = version;
                return this;
            }
            
            public Builder errorDescription(String errorDescription) {
                this.errorDescription = errorDescription;
                return this;
            }
            
            public Builder dumpFile(MultipartParser.FileData dumpFile) {
                this.dumpFile = dumpFile;
                return this;
            }
            
            public Builder gameLogFile(MultipartParser.FileData gameLogFile) {
                this.gameLogFile = gameLogFile;
                return this;
            }
            
            public Builder networkLogFile(MultipartParser.FileData networkLogFile) {
                this.networkLogFile = networkLogFile;
                return this;
            }
            
            public CrashReportData build() {
                return new CrashReportData(this);
            }
        }
    }
}
