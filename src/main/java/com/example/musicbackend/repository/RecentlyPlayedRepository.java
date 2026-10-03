package com.example.musicbackend.repository;

import com.example.musicbackend.entity.RecentlyPlayed;
import com.example.musicbackend.entity.RecentlyPlayedId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecentlyPlayedRepository extends JpaRepository<RecentlyPlayed, RecentlyPlayedId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO recently_played (user_id, song_id, played_at)
            VALUES (:userId, :songId, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, song_id) DO UPDATE SET played_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("songId") Long songId);

    @EntityGraph(attributePaths = { "song", "song.artist", "song.album", "song.genre" })
    @Query("select r from RecentlyPlayed r where r.id.userId = :userId")
    List<RecentlyPlayed> findRecent(@Param("userId") Long userId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecentlyPlayed r where r.id.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from RecentlyPlayed r where r.id.songId = :songId")
    int deleteBySongId(@Param("songId") Long songId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM recently_played
            WHERE (user_id, song_id) IN (
                SELECT user_id, song_id FROM (
                    SELECT user_id, song_id,
                           ROW_NUMBER() OVER (PARTITION BY user_id
                                              ORDER BY played_at DESC, song_id DESC) AS rn
                    FROM recently_played
                ) ranked
                WHERE ranked.rn > :max
            )
            """, nativeQuery = true)
    int trimToMax(@Param("max") int max);
}