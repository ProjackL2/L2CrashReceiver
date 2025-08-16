package l2.tools.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * File-related constants for validation and security.
 * Centralizes file type definitions and security parameters.
 */
public final class FileConstants {
    
    // File size limits
    public static final int MAX_FILENAME_LENGTH = 255;
    public static final double HIGH_ENTROPY_THRESHOLD = 7.5;
    
    // Buffer sizes
    public static final int STREAMING_BUFFER_SIZE = 8192; // 8KB
    public static final int STREAMING_THRESHOLD = 1024 * 1024; // 1MB
    
    // File extensions
    public static final String EXT_DUMP = ".dmp";
    public static final String EXT_TEXT = ".txt";
    public static final String EXT_LOG = ".log";
    
    // Allowed file extensions
    public static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(EXT_DUMP, EXT_TEXT, EXT_LOG))
    );
    
    // Dangerous file extensions
    public static final Set<String> DANGEROUS_EXTENSIONS = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            ".exe", ".bat", ".cmd", ".com", ".pif", ".scr", ".msi", ".dll",
            ".sh", ".bash", ".zsh", ".csh", ".fish",
            ".ps1", ".ps2", ".psm1", ".psd1",
            ".vbs", ".vbe", ".js", ".jse", ".wsf", ".wsh",
            ".php", ".asp", ".aspx", ".jsp", ".py", ".rb", ".pl",
            ".jar", ".war", ".ear",
            ".app", ".dmg", ".pkg",
            ".deb", ".rpm", ".snap",
            ".iso", ".img", ".bin"
        ))
    );
    
    // Dangerous filenames
    public static final Set<String> DANGEROUS_FILENAMES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "autorun.inf", "desktop.ini", "thumbs.db", ".htaccess", ".htpasswd",
            "web.config", "app.config", "machine.config",
            "hosts", "passwd", "shadow", "sudoers"
        ))
    );
    
    // File signatures (magic bytes)
    public static final byte[] DUMP_FILE_SIGNATURE = {0x4D, 0x44, 0x4D, 0x50}; // "MDMP"
    public static final byte[] PE_EXECUTABLE_SIGNATURE = {0x4D, 0x5A}; // "MZ"
    public static final byte[] ELF_EXECUTABLE_SIGNATURE = {0x7F, 0x45, 0x4C, 0x46}; // "\x7fELF"
    
    // Suspicious content patterns
    public static final String[] SUSPICIOUS_SCRIPT_PATTERNS = {
        "<script", "javascript:", "eval(", "system(", "exec(", "shell_exec(",
        "passthru(", "file_get_contents", "file_put_contents", "fopen(", "fwrite(",
        "<?php", "<%", "<jsp:", "import os", "import subprocess", "__import__"
    };
    
    // Suspicious header patterns
    public static final String[] SUSPICIOUS_HEADER_PATTERNS = {
        "<script", "javascript:", "data:", "vbscript:", "onload=", "onerror=",
        "eval(", "expression(", "\\x", "\\u", "%3c", "%3e", "%22", "%27"
    };
    
    private FileConstants() {
        // Utility class - prevent instantiation
    }
}
