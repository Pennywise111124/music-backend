package com.example.musicbackend.exception;

public class ArtistNotFoundException extends RuntimeException {
    public ArtistNotFoundException(Long id) {
        super("Artist not found with id: " + id);
    }
}