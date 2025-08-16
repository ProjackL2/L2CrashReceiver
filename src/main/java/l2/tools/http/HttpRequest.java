package l2.tools.http;

import l2.tools.constant.HttpConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable representation of an HTTP request.
 * Contains request line, method, headers, and body data.
 */
public final class HttpRequest {
    
    private final String requestLine;
    private final String method;
    private final String path;
    private final String version;
    private final Map<String, String> headers;
    
    private HttpRequest(Builder builder) {
        this.requestLine = builder.requestLine;
        this.method = builder.method;
        this.path = builder.path;
        this.version = builder.version;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getRequestLine() {
        return requestLine;
    }
    
    public String getMethod() {
        return method;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getVersion() {
        return version;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public String getHeader(String name) {
        return headers.get(name);
    }
    
    public int getContentLength() {
        String contentLength = getHeader(HttpConstants.CONTENT_LENGTH_HEADER);
        if (contentLength != null) {
            try {
                return Integer.parseInt(contentLength);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    public String getContentType() {
        return getHeader(HttpConstants.CONTENT_TYPE_HEADER);
    }
    
    public boolean isPost() {
        return HttpConstants.POST_METHOD.equals(method);
    }
    
    public boolean isMultipartFormData() {
        String contentType = getContentType();
        return contentType != null && contentType.contains(HttpConstants.CONTENT_TYPE_MULTIPART_FORM_DATA);
    }
    
    @Override
    public String toString() {
        return "HttpRequest{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", headers=" + headers.size() +
                '}';
    }
    
    public static final class Builder {
        private String requestLine;
        private String method;
        private String path;
        private String version;
        private Map<String, String> headers = new HashMap<>();
        
        public Builder requestLine(String requestLine) {
            this.requestLine = requestLine;
            parseRequestLine(requestLine);
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }
        
        public HttpRequest build() {
            return new HttpRequest(this);
        }
        
        private void parseRequestLine(String requestLine) {
            if (requestLine != null && !requestLine.trim().isEmpty()) {
                String[] parts = requestLine.split("\\s+");
                if (parts.length >= 1) {
                    this.method = parts[0];
                }
                if (parts.length >= 2) {
                    this.path = parts[1];
                }
                if (parts.length >= 3) {
                    this.version = parts[2];
                }
            }
        }
    }
}
