package com.example.musicbackend.exception;

public class AlbumInUseException extends RuntimeException {
    public AlbumInUseException(Long id) {
        super("Cannot delete album " + id + " because songs still reference it");
    }
}