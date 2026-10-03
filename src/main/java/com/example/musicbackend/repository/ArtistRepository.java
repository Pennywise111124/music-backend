package com.example.musicbackend.repository;

import com.example.musicbackend.entity.Artist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

  List<Artist> findAllByOrderByNameAscIdAsc();

  List<Artist> findByNameContainingIgnoreCaseOrderByNameAscIdAsc(String name);

  List<Artist> findAllByOrderByPopularityDescNameAscIdAsc(Pageable pageable);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Artist a set a.popularity = a.popularity + 1 where a.id = :id")
  int incrementPopularity(@Param("id") Long id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Artist a set a.popularity = a.popularity - 1 where a.id = :id and a.popularity > 0")
  int decrementPopularity(@Param("id") Long id);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update Artist a set a.popularity = a.popularity - 1
      where a.popularity > 0
        and a.id in (select f.id.artistId from FollowedArtist f where f.id.userId = :userId)
      """)
  int decrementPopularityForUserFollows(@Param("userId") Long userId);
}