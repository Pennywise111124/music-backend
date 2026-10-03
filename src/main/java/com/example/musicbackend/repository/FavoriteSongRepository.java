package com.example.musicbackend.repository;

import com.example.musicbackend.entity.FavoriteSong;
import com.example.musicbackend.entity.FavoriteSongId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteSongRepository extends JpaRepository<FavoriteSong, FavoriteSongId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO favorite_songs (user_id, song_id, added_at)
            VALUES (:userId, :songId, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, song_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("songId") Long songId);

    @EntityGraph(attributePaths = { "song", "song.artist", "song.album", "song.genre" })
    @Query(value = "select f from FavoriteSong f where f.user.id = :userId", countQuery = "select count(f) from FavoriteSong f where f.user.id = :userId")
    Page<FavoriteSong> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from FavoriteSong f where f.id.userId = :userId and f.id.songId = :songId")
    int deleteByUserIdAndSongId(@Param("userId") Long userId, @Param("songId") Long songId);

    @Modifying(flushAutomatically = true)
    @Query("delete from FavoriteSong f where f.id.songId = :songId")
    int deleteBySongId(@Param("songId") Long songId);

    @Modifying(flushAutomatically = true)
    @Query("delete from FavoriteSong f where f.id.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}