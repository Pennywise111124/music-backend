package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.request.AlbumRequest;
import com.example.musicbackend.dto.response.AlbumResponse;
import com.example.musicbackend.entity.Album;
import com.example.musicbackend.entity.Artist;
import com.example.musicbackend.exception.AlbumArtistChangeNotAllowedException;
import com.example.musicbackend.exception.AlbumInUseException;
import com.example.musicbackend.exception.AlbumNotFoundException;
import com.example.musicbackend.exception.ArtistNotFoundException;
import com.example.musicbackend.repository.AlbumRepository;
import com.example.musicbackend.repository.ArtistRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.service.AlbumService;
import com.example.musicbackend.storage.FileCategory;
import com.example.musicbackend.storage.StorageService;
import com.example.musicbackend.storage.TxCallbacks;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumServiceImpl implements AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final StorageService storageService;

    @Override
    public List<AlbumResponse> getAll(String query) {
        List<Album> albums;

        if (query == null || query.isBlank()) {
            albums = albumRepository.findAllByOrderByTitleAscIdAsc();
        } else {
            albums = albumRepository.findByTitleContainingIgnoreCaseOrderByTitleAscIdAsc(query.trim());
        }

        return albums.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<AlbumResponse> getByArtist(Long artistId) {
        if (!artistRepository.existsById(artistId)) {
            throw new ArtistNotFoundException(artistId);
        }

        return albumRepository.findByArtistIdOrdered(artistId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AlbumResponse getById(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new AlbumNotFoundException(id));
        return toResponse(album);
    }

    @Override
    @Transactional
    public AlbumResponse create(AlbumRequest request) {
        Artist artist = artistRepository.findById(request.getArtistId())
                .orElseThrow(() -> new ArtistNotFoundException(request.getArtistId()));

        Album album = Album.builder()
                .title(request.getTitle().trim())
                .artist(artist)
                .releaseDate(request.getReleaseDate())
                .build();

        Album savedAlbum = albumRepository.save(album);
        return toResponse(savedAlbum);
    }

    @Override
    @Transactional
    public AlbumResponse update(Long id, AlbumRequest request) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new AlbumNotFoundException(id));

        Artist artist = artistRepository.findById(request.getArtistId())
                .orElseThrow(() -> new ArtistNotFoundException(request.getArtistId()));

        if (!album.getArtist().getId().equals(artist.getId())
                && songRepository.existsByAlbumId(id)) {
            throw new AlbumArtistChangeNotAllowedException(id);
        }

        album.setTitle(request.getTitle().trim());
        album.setArtist(artist);
        album.setReleaseDate(request.getReleaseDate());

        return toResponse(album);
    }

    @Override
    @Transactional
    public AlbumResponse updateImage(Long id, MultipartFile image) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new AlbumNotFoundException(id));

        String oldUrl = album.getImageUrl();
        String newUrl = storageService.store(image, FileCategory.IMAGE);

        TxCallbacks.afterRollback(() -> storageService.delete(newUrl));

        album.setImageUrl(newUrl);

        TxCallbacks.afterCommit(() -> storageService.delete(oldUrl));

        return toResponse(album);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new AlbumNotFoundException(id));

        if (songRepository.existsByAlbumId(id)) {
            throw new AlbumInUseException(id);
        }

        String imageUrl = album.getImageUrl();

        albumRepository.deleteById(id);

        TxCallbacks.afterCommit(() -> storageService.delete(imageUrl));
    }

    private AlbumResponse toResponse(Album album) {
        return AlbumResponse.builder()
                .id(album.getId())
                .title(album.getTitle())
                .artistId(album.getArtist().getId())
                .artistName(album.getArtist().getName())
                .releaseDate(album.getReleaseDate())
                .imageUrl(album.getImageUrl())
                .build();
    }
}