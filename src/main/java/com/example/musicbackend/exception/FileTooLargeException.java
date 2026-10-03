package com.example.musicbackend.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long maxBytes) {
        super("File exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB");
    }
}