# L2 Crash Receiver

A robust HTTP server for receiving and processing Lineage 2 crash reports. This application accepts multipart/form-data POST requests containing crash dump files and error descriptions, then saves them to the local filesystem for analysis.

## Features

- **Security**: Input validation, path traversal protection, and file size limits
- **Configurability**: Command-line configuration for port and upload directory
- **Thread Safety**: Concurrent request handling with configurable thread pool
- **Proper Logging**: Comprehensive logging using Java logging framework
- **Graceful Shutdown**: Clean resource cleanup on application termination
- **Java 8 Compatible**: Uses only Java 8 features and standard library

## Architecture

The application is organized into several components:

- **`L2CrashReceiver`** - Main entry point with command-line argument parsing
- **`CrashReportServer`** - HTTP server managing connections and lifecycle
- **`HttpRequestHandler`** - Individual request processing logic
- **`MultipartParser`** - Utility for parsing multipart/form-data
- **`FileService`** - File operations with validation and security checks
- **`HttpResponse`** - HTTP response model with proper formatting
- **`ServerConfiguration`** - Configuration management with builder pattern

## Usage

### Basic Usage
```bash
# Start server on default port 80 with default upload directory 'crashes/'
java l2.tools.L2CrashReceiver

# Start server on custom host, port
java l2.tools.L2CrashReceiver 0.0.0.0 80

# Start server with custom host, port and upload directory
java l2.tools.L2CrashReceiver 0.0.0.0 8080 crashes/
```

### Building
```bash
# Build the application
./gradlew build

# Run the application
./gradlew run

# Create a JAR file
./gradlew jar
```

## API

The server accepts POST requests to any path with the following requirements:

### Request Format based on L2CrashSeder application
- **Method**: POST
- **Content-Type**: multipart/form-data
- **Fields**:
  - `CRVersion` : Version information
  - `error` : Error description text
  - `dumpfile` : Binary dump file
  - `gamelog` (optional): Game log file(l2.log)
  - `networklog` (optional): Network log file(Network.log)

### Response Codes
- **200 OK**: Crash report processed successfully
- **400 Bad Request**: Invalid request format or missing required fields
- **413 Payload Too Large**: File size exceeds configured maximum
- **500 Internal Server Error**: Server-side processing error

### Example Request
```http
POST / HTTP/1.1
Content-Type: multipart/form-data; boundary=MULTIPART-DATA-BOUNDARY

--MULTIPART-DATA-BOUNDARY
Content-Disposition: form-data; name="CRVersion"

1.0.0
--MULTIPART-DATA-BOUNDARY
Content-Disposition: form-data; name="error"

Exception occurred in game engine...
--MULTIPART-DATA-BOUNDARY
Content-Disposition: form-data; name="upload_file_minidump"; filename="crash.dmp"
Content-Type: application/octet-stream

[binary dump data]
--MULTIPART-DATA-BOUNDARY--
```

## Configuration

Default configuration values:
- **Host**: 0.0.0.0
- **Port**: 80
- **Upload Directory**: `crashes/`
- **Max File Size**: Integer.Max
- **Thread Pool Size**: 10 threads

## Security Features

1. **Path Traversal Protection**: Prevents files from being saved outside the upload directory
2. **Filename Sanitization**: Removes dangerous characters from uploaded filenames
3. **File Size Limits**: Configurable maximum file size to prevent DoS attacks
4. **Input Validation**: Comprehensive validation of all input parameters
5. **Resource Management**: Proper cleanup of resources to prevent memory leaks

## File Handling

- Crash dump files are saved with their original names (sanitized)
- Error descriptions are saved as text files with `.txt` extension
- Duplicate filenames are handled by appending timestamps
- All files are saved in the configured upload directory

## Logging

The application uses Java's built-in logging framework with the following levels:
- **INFO**: Server startup, configuration, and successful operations
- **WARNING**: Non-fatal errors and unusual conditions
- **SEVERE**: Fatal errors and exceptions

## Requirements

- Java 8 or higher
- Write permissions to the upload directory
- Network permissions to bind to the specified host, port
