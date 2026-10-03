package com.example.musicbackend.mapper;

import com.example.musicbackend.dto.response.PlaylistDetailResponse;
import com.example.musicbackend.dto.response.PlaylistResponse;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.entity.Playlist;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlaylistMapper {

    public PlaylistResponse toResponse(Playlist p, long songCount) {
        return PlaylistResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .imageUrl(p.getImageUrl())
                .isPublic(p.isPublicPlaylist())
                .ownerId(p.getOwner().getId())
                .ownerName(p.getOwner().getUsername())
                .songCount(songCount)
                .createdAt(p.getCreatedAt())
                .build();
    }

    public PlaylistDetailResponse toDetailResponse(Playlist p, List<SongResponse> songs) {
        return PlaylistDetailResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .imageUrl(p.getImageUrl())
                .isPublic(p.isPublicPlaylist())
                .ownerId(p.getOwner().getId())
                .ownerName(p.getOwner().getUsername())
                .songCount(songs.size())
                .createdAt(p.getCreatedAt())
                .songs(songs)
                .build();
    }
}