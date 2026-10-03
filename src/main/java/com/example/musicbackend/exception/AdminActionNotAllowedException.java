package com.example.musicbackend.exception;

public class AdminActionNotAllowedException extends RuntimeException {
    public AdminActionNotAllowedException(String message) {
        super(message);
    }
}