package com.example.musicbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PlaylistDetailResponse {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Boolean isPublic;
    private Long ownerId;
    private String ownerName;
    private long songCount;
    private LocalDateTime createdAt;
    private List<SongResponse> songs;
}