package com.example.musicbackend.exception;

public class GenreAlreadyExistsException extends RuntimeException {
    public GenreAlreadyExistsException(String name) {
        super("Genre already exists: " + name);
    }
}