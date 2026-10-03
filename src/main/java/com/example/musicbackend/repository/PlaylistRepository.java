package com.example.musicbackend.repository;

import com.example.musicbackend.entity.Playlist;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Playlist> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Playlist p where p.id = :id")
    Optional<Playlist> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "owner")
    Page<Playlist> findByOwnerId(Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Playlist> findByPublicPlaylistTrue(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Playlist> findByPublicPlaylistTrueAndNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("select p.imageUrl from Playlist p where p.owner.id = :ownerId and p.imageUrl is not null")
    List<String> findImageUrlsByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Playlist p where p.owner.id = :ownerId")
    int deleteByOwnerId(@Param("ownerId") Long ownerId);
}