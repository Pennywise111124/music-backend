package com.example.musicbackend.mapper;

import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.entity.Artist;
import org.springframework.stereotype.Component;

@Component
public class ArtistMapper {

    public ArtistResponse toResponse(Artist artist, boolean followed) {
        return ArtistResponse.builder()
                .id(artist.getId())
                .name(artist.getName())
                .imageUrl(artist.getImageUrl())
                .info(artist.getInfo())
                .popularity(artist.getPopularity())
                .followed(followed)
                .build();
    }
}