package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BanRequest {

    @NotNull(message = "Banned flag is required")
    private Boolean banned;
}