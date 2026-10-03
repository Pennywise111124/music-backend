package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.request.ArtistRequest;
import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.entity.Artist;
import com.example.musicbackend.entity.FollowedArtistId;
import com.example.musicbackend.exception.ArtistInUseException;
import com.example.musicbackend.exception.ArtistNotFoundException;
import com.example.musicbackend.mapper.ArtistMapper;
import com.example.musicbackend.repository.AlbumRepository;
import com.example.musicbackend.repository.ArtistRepository;
import com.example.musicbackend.repository.FollowedArtistRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.ArtistService;
import com.example.musicbackend.storage.FileCategory;
import com.example.musicbackend.storage.StorageService;
import com.example.musicbackend.storage.TxCallbacks;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistServiceImpl implements ArtistService {

    private static final int MAX_TOP_LIMIT = 50;

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final SongRepository songRepository;
    private final FollowedArtistRepository followedArtistRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ArtistMapper artistMapper;
    private final StorageService storageService;

    @Override
    public List<ArtistResponse> getAll(String query) {
        List<Artist> artists;

        if (query == null || query.isBlank()) {
            artists = artistRepository.findAllByOrderByNameAscIdAsc();
        } else {
            artists = artistRepository.findByNameContainingIgnoreCaseOrderByNameAscIdAsc(query.trim());
        }

        return toResponsesWithFollowed(artists);
    }

    @Override
    public List<ArtistResponse> getTop(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_TOP_LIMIT);

        List<Artist> top = artistRepository.findAllByOrderByPopularityDescNameAscIdAsc(PageRequest.of(0, safeLimit));

        return toResponsesWithFollowed(top);
    }

    @Override
    public ArtistResponse getById(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ArtistNotFoundException(id));

        Long userId = currentUserProvider.getCurrentUser().getId();
        boolean followed = followedArtistRepository.existsById(new FollowedArtistId(userId, id));

        return artistMapper.toResponse(artist, followed);
    }

    @Override
    @Transactional
    public ArtistResponse create(ArtistRequest request) {
        Artist artist = Artist.builder()
                .name(request.getName().trim())
                .info(request.getInfo())
                .build();

        Artist savedArtist = artistRepository.save(artist);
        return artistMapper.toResponse(savedArtist, false);
    }

    @Override
    @Transactional
    public ArtistResponse update(Long id, ArtistRequest request) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ArtistNotFoundException(id));

        artist.setName(request.getName().trim());
        artist.setInfo(request.getInfo());

        return artistMapper.toResponse(artist, false);
    }

    @Override
    @Transactional
    public ArtistResponse updateImage(Long id, MultipartFile image) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ArtistNotFoundException(id));

        String oldUrl = artist.getImageUrl();
        String newUrl = storageService.store(image, FileCategory.IMAGE);

        TxCallbacks.afterRollback(() -> storageService.delete(newUrl));

        artist.setImageUrl(newUrl);

        TxCallbacks.afterCommit(() -> storageService.delete(oldUrl));

        return artistMapper.toResponse(artist, false);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ArtistNotFoundException(id));

        if (albumRepository.existsByArtistId(id) || songRepository.existsByArtistId(id)) {
            throw new ArtistInUseException(id);
        }

        String imageUrl = artist.getImageUrl();

        followedArtistRepository.deleteByArtistId(id);

        artistRepository.deleteById(id);

        TxCallbacks.afterCommit(() -> storageService.delete(imageUrl));
    }

    private List<ArtistResponse> toResponsesWithFollowed(List<Artist> artists) {
        if (artists.isEmpty()) {
            return List.of();
        }
        Long userId = currentUserProvider.getCurrentUser().getId();
        List<Long> ids = artists.stream().map(Artist::getId).toList();
        Set<Long> followedIds = new HashSet<>(followedArtistRepository.findFollowedArtistIds(userId, ids));
        return artists.stream()
                .map(a -> artistMapper.toResponse(a, followedIds.contains(a.getId())))
                .toList();
    }
}