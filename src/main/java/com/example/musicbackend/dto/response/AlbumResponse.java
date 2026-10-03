package com.example.musicbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class AlbumResponse {
    private Long id;
    private String title;
    private Long artistId;
    private String artistName;
    private LocalDate releaseDate;
    private String imageUrl;
}