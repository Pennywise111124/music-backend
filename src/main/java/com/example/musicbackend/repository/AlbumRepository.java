package com.example.musicbackend.repository;

import com.example.musicbackend.entity.Album;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    // EntityGraph: nạp luôn artist trong 1 câu JOIN, tránh N+1 query
    // (mỗi album lại thêm 1 query lấy artist) khi map sang response.
    @Override
    @EntityGraph(attributePaths = "artist")
    Optional<Album> findById(Long id);

    @EntityGraph(attributePaths = "artist")
    List<Album> findAllByOrderByTitleAscIdAsc();

    @EntityGraph(attributePaths = "artist")
    List<Album> findByTitleContainingIgnoreCaseOrderByTitleAscIdAsc(String title);

    // Mới nhất trước; album chưa có ngày phát hành xếp cuối
    @Query("""
            select a from Album a join fetch a.artist
            where a.artist.id = :artistId
            order by a.releaseDate desc nulls last, a.id desc
            """)
    List<Album> findByArtistIdOrdered(@Param("artistId") Long artistId);

    boolean existsByArtistId(Long artistId);
}