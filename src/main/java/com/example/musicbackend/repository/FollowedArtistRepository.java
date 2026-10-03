package com.example.musicbackend.repository;

import com.example.musicbackend.entity.FollowedArtist;
import com.example.musicbackend.entity.FollowedArtistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FollowedArtistRepository extends JpaRepository<FollowedArtist, FollowedArtistId> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO followed_artists (user_id, artist_id, followed_at)
            VALUES (:userId, :artistId, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, artist_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("artistId") Long artistId);

    @Modifying(flushAutomatically = true)
    @Query("delete from FollowedArtist f where f.id.userId = :userId and f.id.artistId = :artistId")
    int deleteByUserIdAndArtistId(@Param("userId") Long userId, @Param("artistId") Long artistId);

    @Modifying(flushAutomatically = true)
    @Query("delete from FollowedArtist f where f.id.artistId = :artistId")
    int deleteByArtistId(@Param("artistId") Long artistId);

    @EntityGraph(attributePaths = "artist")
    @Query(value = "select f from FollowedArtist f where f.id.userId = :userId", countQuery = "select count(f) from FollowedArtist f where f.id.userId = :userId")
    Page<FollowedArtist> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("select f.id.artistId from FollowedArtist f where f.id.userId = :userId and f.id.artistId in :artistIds")
    List<Long> findFollowedArtistIds(@Param("userId") Long userId, @Param("artistIds") Collection<Long> artistIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from FollowedArtist f where f.id.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}