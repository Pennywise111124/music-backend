package com.example.musicbackend.exception;

public class PlaylistAccessDeniedException extends RuntimeException {
    public PlaylistAccessDeniedException(Long id) {
        super("Only the owner can modify playlist " + id);
    }
}