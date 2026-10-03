package com.example.musicbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ArtistResponse {
    private Long id;
    private String name;
    private String imageUrl;
    private String info;
    private int popularity;
    private boolean followed;
}