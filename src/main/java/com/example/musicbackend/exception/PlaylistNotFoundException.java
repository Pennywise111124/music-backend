package com.example.musicbackend.exception;

public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(Long id) {
        super("Playlist not found with id: " + id);
    }
}