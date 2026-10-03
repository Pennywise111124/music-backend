package com.example.musicbackend.exception;

public class InvalidPlaylistReorderException extends RuntimeException {
    public InvalidPlaylistReorderException(String message) {
        super(message);
    }
}