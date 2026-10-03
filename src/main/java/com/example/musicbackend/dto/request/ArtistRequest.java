package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ArtistRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name max 255 chars")
    private String name;

    private String info;
}