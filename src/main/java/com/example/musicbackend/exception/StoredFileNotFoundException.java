package com.example.musicbackend.exception;

public class StoredFileNotFoundException extends RuntimeException {
    public StoredFileNotFoundException() {
        super("File not found");
    }
}