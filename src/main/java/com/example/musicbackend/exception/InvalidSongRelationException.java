package com.example.musicbackend.exception;

public class InvalidSongRelationException extends RuntimeException {
    public InvalidSongRelationException(String message) {
        super(message);
    }
}