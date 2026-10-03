package com.example.musicbackend.repository;

import com.example.musicbackend.entity.PlaylistSong;
import com.example.musicbackend.entity.PlaylistSongId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, PlaylistSongId> {

        @EntityGraph(attributePaths = { "song", "song.artist", "song.album", "song.genre" })
        @Query("select ps from PlaylistSong ps where ps.id.playlistId = :playlistId "
                        + "order by ps.position asc, ps.id.songId asc")
        List<PlaylistSong> findDetailedByPlaylistId(@Param("playlistId") Long playlistId);

        @Query("select ps from PlaylistSong ps where ps.id.playlistId = :playlistId "
                        + "order by ps.position asc, ps.id.songId asc")
        List<PlaylistSong> findByPlaylistId(@Param("playlistId") Long playlistId);

        @Query("select coalesce(max(ps.position), -1) from PlaylistSong ps where ps.id.playlistId = :playlistId")
        int findMaxPosition(@Param("playlistId") Long playlistId);

        @Query("select count(ps) from PlaylistSong ps where ps.id.playlistId = :playlistId")
        long countByPlaylistId(@Param("playlistId") Long playlistId);

        @Query("select ps.id.playlistId as playlistId, count(ps) as songCount from PlaylistSong ps "
                        + "where ps.id.playlistId in :playlistIds group by ps.id.playlistId")
        List<PlaylistSongCount> countByPlaylistIds(@Param("playlistIds") Collection<Long> playlistIds);

        @Modifying(flushAutomatically = true)
        @Query("delete from PlaylistSong ps where ps.id.playlistId = :playlistId and ps.id.songId = :songId")
        int deleteByPlaylistIdAndSongId(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

        @Modifying(flushAutomatically = true)
        @Query("delete from PlaylistSong ps where ps.id.playlistId = :playlistId")
        int deleteByPlaylistId(@Param("playlistId") Long playlistId);

        @Modifying(flushAutomatically = true)
        @Query("delete from PlaylistSong ps where ps.id.songId = :songId")
        int deleteBySongId(@Param("songId") Long songId);

        @Modifying(flushAutomatically = true)
        @Query("delete from PlaylistSong ps where ps.id.playlistId in "
                        + "(select p.id from Playlist p where p.owner.id = :ownerId)")
        int deleteByPlaylistOwnerId(@Param("ownerId") Long ownerId);
}