package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.request.SongRequest;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.entity.Album;
import com.example.musicbackend.entity.Artist;
import com.example.musicbackend.entity.Genre;
import com.example.musicbackend.entity.Song;
import com.example.musicbackend.exception.AlbumNotFoundException;
import com.example.musicbackend.exception.ArtistNotFoundException;
import com.example.musicbackend.exception.GenreNotFoundException;
import com.example.musicbackend.exception.InvalidSongRelationException;
import com.example.musicbackend.exception.SongNotFoundException;
import com.example.musicbackend.mapper.SongMapper;
import com.example.musicbackend.repository.AlbumRepository;
import com.example.musicbackend.repository.ArtistRepository;
import com.example.musicbackend.repository.FavoriteSongRepository;
import com.example.musicbackend.repository.GenreRepository;
import com.example.musicbackend.repository.PlaylistSongRepository;
import com.example.musicbackend.repository.RecentlyPlayedRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.service.SongService;
import com.example.musicbackend.storage.FileCategory;
import com.example.musicbackend.storage.StorageService;
import com.example.musicbackend.storage.TxCallbacks;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongServiceImpl implements SongService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final GenreRepository genreRepository;
    private final SongMapper songMapper;
    private final FavoriteSongRepository favoriteSongRepository;
    private final RecentlyPlayedRepository recentlyPlayedRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final StorageService storageService;

    @Override
    public PageResponse<SongResponse> search(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by("title").ascending().and(Sort.by("id").ascending());
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Song> songPage;
        if (query == null || query.isBlank()) {
            songPage = songRepository.findAll(pageable);
        } else {
            songPage = songRepository.findByTitleContainingIgnoreCase(query.trim(), pageable);
        }

        Page<SongResponse> responsePage = songPage.map(songMapper::toResponse);
        return PageResponse.of(responsePage);
    }

    @Override
    public List<SongResponse> getByArtist(Long artistId) {
        if (!artistRepository.existsById(artistId)) {
            throw new ArtistNotFoundException(artistId);
        }

        return songRepository.findByArtistIdOrderByTitleAscIdAsc(artistId)
                .stream()
                .map(songMapper::toResponse)
                .toList();
    }

    @Override
    public List<SongResponse> getByAlbum(Long albumId) {
        if (!albumRepository.existsById(albumId)) {
            throw new AlbumNotFoundException(albumId);
        }

        return songRepository.findByAlbumIdOrderByTitleAscIdAsc(albumId)
                .stream()
                .map(songMapper::toResponse)
                .toList();
    }

    @Override
    public SongResponse getById(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public SongResponse create(SongRequest request, MultipartFile audio, MultipartFile cover) {
        Song song = new Song();
        applyRequest(song, request); // kiểm tra artist/album/genre TRƯỚC khi đụng tới đĩa

        String audioUrl = storageService.store(audio, FileCategory.AUDIO);
        TxCallbacks.afterRollback(() -> storageService.delete(audioUrl));
        song.setAudioUrl(audioUrl);

        if (cover != null && !cover.isEmpty()) {
            String coverUrl = storageService.store(cover, FileCategory.IMAGE);
            TxCallbacks.afterRollback(() -> storageService.delete(coverUrl));
            song.setImageUrl(coverUrl);
        }

        Song saved = songRepository.save(song);
        return songMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SongResponse update(Long id, SongRequest request) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));

        applyRequest(song, request);

        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public SongResponse replaceAudio(Long id, MultipartFile audio) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));

        String oldUrl = song.getAudioUrl();
        String newUrl = storageService.store(audio, FileCategory.AUDIO);
        TxCallbacks.afterRollback(() -> storageService.delete(newUrl));

        song.setAudioUrl(newUrl);
        TxCallbacks.afterCommit(() -> storageService.delete(oldUrl));

        return songMapper.toResponse(song);
    }

    @Override
    @Transactional
    public SongResponse replaceCover(Long id, MultipartFile cover) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));

        String oldUrl = song.getImageUrl();
        String newUrl = storageService.store(cover, FileCategory.IMAGE);
        TxCallbacks.afterRollback(() -> storageService.delete(newUrl));

        song.setImageUrl(newUrl);
        TxCallbacks.afterCommit(() -> storageService.delete(oldUrl));

        return songMapper.toResponse(song);
    }

    private void applyRequest(Song song, SongRequest request) {
        Artist artist = artistRepository.findById(request.getArtistId())
                .orElseThrow(() -> new ArtistNotFoundException(request.getArtistId()));

        Album album = null;
        if (request.getAlbumId() != null) {
            album = albumRepository.findById(request.getAlbumId())
                    .orElseThrow(() -> new AlbumNotFoundException(request.getAlbumId()));

            if (!album.getArtist().getId().equals(artist.getId())) {
                throw new InvalidSongRelationException("Album " + request.getAlbumId()
                        + " does not belong to artist " + artist.getId());
            }
        }

        Genre genre = null;
        if (request.getGenreId() != null) {
            genre = genreRepository.findById(request.getGenreId())
                    .orElseThrow(() -> new GenreNotFoundException(request.getGenreId()));
        }

        song.setTitle(request.getTitle().trim());
        song.setArtist(artist);
        song.setAlbum(album);
        song.setGenre(genre);
        song.setDurationMs(request.getDurationMs());
        song.setReleaseDate(request.getReleaseDate());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));

        String audioUrl = song.getAudioUrl();
        String coverUrl = song.getImageUrl();

        favoriteSongRepository.deleteBySongId(id);
        recentlyPlayedRepository.deleteBySongId(id);
        playlistSongRepository.deleteBySongId(id);
        songRepository.deleteById(id);

        TxCallbacks.afterCommit(() -> {
            storageService.delete(audioUrl);
            storageService.delete(coverUrl);
        });
    }
}