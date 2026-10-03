package com.example.musicbackend.exception;

public class GenreInUseException extends RuntimeException {
    public GenreInUseException(Long id) {
        super("Cannot delete genre id = " + id + " because it is still in use by some songs");
    }
}