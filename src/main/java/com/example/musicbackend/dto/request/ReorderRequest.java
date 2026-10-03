package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ReorderRequest {

    @NotNull(message = "Song ids are required")
    private List<@NotNull(message = "Song id must not be null") Long> songIds;
}