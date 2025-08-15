package l2.tools.http;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for parsing multipart/form-data HTTP requests.
 */
public final class MultipartParser {
    
    private static final String BOUNDARY_PREFIX = "--";
    private static final byte[] HEADER_END_PATTERN = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] LINE_END_PATTERN = "\r\n".getBytes(StandardCharsets.ISO_8859_1);
    
    private MultipartParser() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Extracts the boundary from a Content-Type header.
     *
     * @param contentType the Content-Type header value
     * @return the boundary string with -- prefix, or null if not found
     */
    public static String extractBoundary(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("boundary=")) {
                String boundary = trimmedPart.substring("boundary=".length());
                // Remove quotes if present
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return BOUNDARY_PREFIX + boundary;
            }
        }
        return null;
    }
    
    /**
     * Parses multipart data and extracts form fields and files.
     *
     * @param body the request body as byte array
     * @param boundary the multipart boundary (with -- prefix)
     * @return a MultipartData object containing parsed fields and files
     */
    public static MultipartData parse(byte[] body, String boundary) {
        if (body == null || boundary == null) {
            return new MultipartData();
        }
        
        MultipartData result = new MultipartData();
        byte[] boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);
        
        int position = 0;
        while (position < body.length) {
            int boundaryPosition = indexOf(body, boundaryBytes, position);
            if (boundaryPosition == -1) {
                break;
            }
            
            int partStart = boundaryPosition + boundaryBytes.length;
            if (partStart >= body.length) {
                break;
            }
            
            // Skip \r\n after boundary
            if (partStart + 1 < body.length && body[partStart] == '\r' && body[partStart + 1] == '\n') {
                partStart += 2;
            }
            
            int headerEndPosition = indexOf(body, HEADER_END_PATTERN, partStart);
            if (headerEndPosition == -1) {
                break;
            }
            
            String headers = new String(Arrays.copyOfRange(body, partStart, headerEndPosition), StandardCharsets.ISO_8859_1);
            
            int dataStart = headerEndPosition + HEADER_END_PATTERN.length;
            int nextBoundaryPosition = indexOf(body, boundaryBytes, dataStart);
            if (nextBoundaryPosition == -1) {
                nextBoundaryPosition = body.length;
            }
            
            // Remove trailing \r\n before next boundary
            int dataEnd = nextBoundaryPosition;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }
            
            int dataLength = dataEnd - dataStart;
            if (dataLength > 0) {
                byte[] data = Arrays.copyOfRange(body, dataStart, dataEnd);
                processPart(headers, data, result);
            }
            
            position = boundaryPosition + boundaryBytes.length;
        }
        
        return result;
    }
    
    private static void processPart(String headers, byte[] data, MultipartData result) {
        Map<String, String> headerMap = parseHeaders(headers);
        String contentDisposition = headerMap.get("content-disposition");
        
        if (contentDisposition == null) {
            return;
        }
        
        Optional<String> fieldName = extractFieldName(contentDisposition);
        Optional<String> fileName = extractFileName(contentDisposition);
        
        if (fileName.isPresent() && fieldName.isPresent()) {
            // This is a file upload
            result.addFile(fieldName.get(), fileName.get(), data);
        } else if (fieldName.isPresent()) {
            // This is a form field
            String value = new String(data, StandardCharsets.UTF_8);
            result.addField(fieldName.get(), value);
        }
    }
    
    private static Map<String, String> parseHeaders(String headers) {
        Map<String, String> headerMap = new HashMap<>();
        String[] lines = headers.split("\r\n");
        
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                headerMap.put(key, value);
            }
        }
        
        return headerMap;
    }
    
    private static Optional<String> extractFieldName(String contentDisposition) {
        return extractQuotedValue(contentDisposition, "name");
    }
    
    private static Optional<String> extractFileName(String contentDisposition) {
        return extractQuotedValue(contentDisposition, "filename");
    }
    
    private static Optional<String> extractQuotedValue(String input, String parameter) {
        String searchPattern = parameter + "=\"";
        int startIndex = input.indexOf(searchPattern);
        if (startIndex == -1) {
            return Optional.empty();
        }
        
        startIndex += searchPattern.length();
        int endIndex = input.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return Optional.empty();
        }
        
        return Optional.of(input.substring(startIndex, endIndex));
    }
    
    /**
     * Finds the index of a byte pattern within a byte array, starting from a given position.
     */
    private static int indexOf(byte[] array, byte[] target, int fromIndex) {
        if (target.length == 0) {
            return fromIndex;
        }
        
        outer:
        for (int i = fromIndex; i <= array.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
    
    /**
     * Container for parsed multipart data.
     */
    public static final class MultipartData {
        private final Map<String, String> fields = new HashMap<>();
        private final Map<String, FileData> files = new HashMap<>();
        
        public void addField(String name, String value) {
            fields.put(name, value);
        }
        
        public void addFile(String fieldName, String fileName, byte[] data) {
            files.put(fieldName, new FileData(fileName, data));
        }
        
        public Optional<String> getField(String name) {
            return Optional.ofNullable(fields.get(name));
        }
        
        public Optional<FileData> getFile(String fieldName) {
            return Optional.ofNullable(files.get(fieldName));
        }
        
        public Map<String, String> getAllFields() {
            return new HashMap<>(fields);
        }
        
        public Map<String, FileData> getAllFiles() {
            return new HashMap<>(files);
        }
    }
    
    /**
     * Container for file data from multipart upload.
     */
    public static final class FileData {
        private final String fileName;
        private final byte[] data;
        
        public FileData(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = Arrays.copyOf(data, data.length);
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public byte[] getData() {
            return Arrays.copyOf(data, data.length);
        }
        
        public int getSize() {
            return data.length;
        }
        
        @Override
        public String toString() {
            return "FileData{" +
                    "fileName='" + fileName + '\'' +
                    ", size=" + data.length +
                    '}';
        }
    }
}
