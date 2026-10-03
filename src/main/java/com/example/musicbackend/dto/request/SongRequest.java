package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class SongRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title max 255 chars")
    private String title;

    @NotNull(message = "Artist id is required")
    private Long artistId;

    private Long albumId;

    private Long genreId;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be greater than 0")
    private Long durationMs;

    private LocalDate releaseDate;
}