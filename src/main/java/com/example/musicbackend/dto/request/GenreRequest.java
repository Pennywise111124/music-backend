package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GenreRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name max 100 chars")
    private String name;
}