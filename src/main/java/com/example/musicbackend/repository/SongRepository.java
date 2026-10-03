package com.example.musicbackend.repository;

import com.example.musicbackend.entity.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SongRepository extends JpaRepository<Song, Long> {

    @Override
    @EntityGraph(attributePaths = { "artist", "album", "genre" })
    Optional<Song> findById(Long id);

    @Override
    @EntityGraph(attributePaths = { "artist", "album", "genre" })
    Page<Song> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "artist", "album", "genre" })
    Page<Song> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @EntityGraph(attributePaths = { "artist", "album", "genre" })
    List<Song> findByArtistIdOrderByTitleAscIdAsc(Long artistId);

    @EntityGraph(attributePaths = { "artist", "album", "genre" })
    List<Song> findByAlbumIdOrderByTitleAscIdAsc(Long albumId);

    boolean existsByArtistId(Long artistId);

    boolean existsByAlbumId(Long albumId);

    boolean existsByGenreId(Long genreId);
}