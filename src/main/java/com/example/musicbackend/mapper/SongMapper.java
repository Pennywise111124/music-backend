package com.example.musicbackend.mapper;

import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.entity.Album;
import com.example.musicbackend.entity.Artist;
import com.example.musicbackend.entity.Genre;
import com.example.musicbackend.entity.Song;
import org.springframework.stereotype.Component;

@Component
public class SongMapper {

    public SongResponse toResponse(Song song) {
        Album album = song.getAlbum();
        Genre genre = song.getGenre();
        Artist artist = song.getArtist();
        return SongResponse.builder()
                .id(song.getId())
                .title(song.getTitle())
                .artistId(artist.getId())
                .artistName(artist.getName())
                .albumId(album != null ? album.getId() : null)
                .albumTitle(album != null ? album.getTitle() : null)
                .genreId(genre != null ? genre.getId() : null)
                .genreName(genre != null ? genre.getName() : null)
                .durationMs(song.getDurationMs())
                .audioUrl(song.getAudioUrl())
                .imageUrl(song.getImageUrl())
                .releaseDate(song.getReleaseDate())
                .createdAt(song.getCreatedAt())
                .build();
    }
}