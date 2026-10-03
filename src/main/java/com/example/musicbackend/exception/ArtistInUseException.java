package com.example.musicbackend.exception;

public class ArtistInUseException extends RuntimeException {
    public ArtistInUseException(Long id) {
        super("Cannot delete artist " + id + " because albums or songs still reference it");
    }
}