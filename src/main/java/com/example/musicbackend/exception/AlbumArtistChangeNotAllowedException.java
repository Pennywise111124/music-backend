package com.example.musicbackend.exception;

public class AlbumArtistChangeNotAllowedException extends RuntimeException {
    public AlbumArtistChangeNotAllowedException(Long albumId) {
        super("Cannot change artist of album " + albumId + " because it still contains songs");
    }
}