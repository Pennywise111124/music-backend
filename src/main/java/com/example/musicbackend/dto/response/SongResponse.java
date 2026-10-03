package com.example.musicbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class SongResponse {
    private Long id;
    private String title;
    private Long artistId;
    private String artistName;
    private Long albumId;
    private String albumTitle;
    private Long genreId;
    private String genreName;
    private long durationMs;
    private String audioUrl;
    private String imageUrl;
    private LocalDate releaseDate;
    private LocalDateTime createdAt;
}