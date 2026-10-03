package com.example.musicbackend.exception;

public class GenreNotFoundException extends RuntimeException {
    public GenreNotFoundException(Long id) {
        super("Genre not found with id: " + id);
    }
}