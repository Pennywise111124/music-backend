package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.request.PlaylistRequest;
import com.example.musicbackend.dto.request.ReorderRequest;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.PlaylistDetailResponse;
import com.example.musicbackend.dto.response.PlaylistResponse;
import com.example.musicbackend.entity.Playlist;
import com.example.musicbackend.entity.PlaylistSong;
import com.example.musicbackend.entity.PlaylistSongId;
import com.example.musicbackend.entity.Song;
import com.example.musicbackend.entity.User;
import com.example.musicbackend.exception.InvalidPlaylistReorderException;
import com.example.musicbackend.exception.PlaylistAccessDeniedException;
import com.example.musicbackend.exception.PlaylistNotFoundException;
import com.example.musicbackend.exception.SongNotFoundException;
import com.example.musicbackend.mapper.PlaylistMapper;
import com.example.musicbackend.mapper.SongMapper;
import com.example.musicbackend.repository.PlaylistRepository;
import com.example.musicbackend.repository.PlaylistSongCount;
import com.example.musicbackend.repository.PlaylistSongRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.PlaylistService;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistServiceImpl implements PlaylistService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final SongRepository songRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;
    private final StorageService storageService;

    @Override
    public PageResponse<PlaylistResponse> getMine(int page, int size) {
        User user = currentUserProvider.getCurrentUser();

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Playlist> playlistPage = playlistRepository.findByOwnerId(user.getId(), pageable);

        return toPageResponse(playlistPage);
    }

    @Override
    public PageResponse<PlaylistResponse> getPublic(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Playlist> playlistPage;
        if (query == null || query.isBlank()) {
            playlistPage = playlistRepository.findByPublicPlaylistTrue(pageable);
        } else {
            playlistPage = playlistRepository.findByPublicPlaylistTrueAndNameContainingIgnoreCase(query.trim(),
                    pageable);
        }

        return toPageResponse(playlistPage);
    }

    @Override
    public PlaylistDetailResponse getById(Long id) {
        User user = currentUserProvider.getCurrentUser();

        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new PlaylistNotFoundException(id));

        boolean isOwner = playlist.getOwner().getId().equals(user.getId());
        if (!playlist.isPublicPlaylist() && !isOwner) {
            throw new PlaylistNotFoundException(id);
        }

        var songs = playlistSongRepository.findDetailedByPlaylistId(id)
                .stream()
                .map(ps -> songMapper.toResponse(ps.getSong()))
                .toList();

        return playlistMapper.toDetailResponse(playlist, songs);
    }

    @Override
    @Transactional
    public PlaylistResponse create(PlaylistRequest request) {
        User user = currentUserProvider.getCurrentUser();

        Playlist playlist = Playlist.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .publicPlaylist(Boolean.TRUE.equals(request.getIsPublic()))
                .owner(user)
                .build();

        Playlist saved = playlistRepository.save(playlist);

        return playlistMapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public PlaylistResponse update(Long id, PlaylistRequest request) {
        User user = currentUserProvider.getCurrentUser();
        Playlist playlist = loadForOwnerAction(id, user);

        playlist.setName(request.getName().trim());
        playlist.setDescription(request.getDescription());
        playlist.setPublicPlaylist(Boolean.TRUE.equals(request.getIsPublic()));

        long songCount = playlistSongRepository.countByPlaylistId(id);

        return playlistMapper.toResponse(playlist, songCount);
    }

    @Override
    @Transactional
    public PlaylistResponse updateImage(Long id, MultipartFile image) {
        User user = currentUserProvider.getCurrentUser();
        Playlist playlist = loadForOwnerAction(id, user);

        String oldUrl = playlist.getImageUrl();
        String newUrl = storageService.store(image, FileCategory.IMAGE);

        TxCallbacks.afterRollback(() -> storageService.delete(newUrl));

        playlist.setImageUrl(newUrl);

        TxCallbacks.afterCommit(() -> storageService.delete(oldUrl));

        long songCount = playlistSongRepository.countByPlaylistId(id);

        return playlistMapper.toResponse(playlist, songCount);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = currentUserProvider.getCurrentUser();
        Playlist playlist = loadForOwnerAction(id, user);

        String imageUrl = playlist.getImageUrl();

        playlistSongRepository.deleteByPlaylistId(id);
        playlistRepository.delete(playlist);

        TxCallbacks.afterCommit(() -> storageService.delete(imageUrl));
    }

    @Override
    @Transactional
    public void addSong(Long playlistId, Long songId) {
        User user = currentUserProvider.getCurrentUser();
        Playlist playlist = loadForOwnerAction(playlistId, user);

        if (!songRepository.existsById(songId)) {
            throw new SongNotFoundException(songId);
        }

        PlaylistSongId id = new PlaylistSongId(playlistId, songId);
        if (playlistSongRepository.existsById(id)) {
            return;
        }

        Integer maxPos = playlistSongRepository.findMaxPosition(playlistId);
        int position = (maxPos == null) ? 0 : maxPos + 1;

        Song songRef = songRepository.getReferenceById(songId);

        PlaylistSong playlistSong = PlaylistSong.builder()
                .id(id)
                .playlist(playlist)
                .song(songRef)
                .position(position)
                .build();

        playlistSongRepository.save(playlistSong);
    }

    @Override
    @Transactional
    public void removeSong(Long playlistId, Long songId) {
        User user = currentUserProvider.getCurrentUser();
        loadForOwnerAction(playlistId, user);

        int deleted = playlistSongRepository.deleteByPlaylistIdAndSongId(playlistId, songId);

        if (deleted == 1) {
            List<PlaylistSong> rest = playlistSongRepository.findByPlaylistId(playlistId);
            for (int i = 0; i < rest.size(); i++) {
                rest.get(i).setPosition(i);
            }
        }
    }

    @Override
    @Transactional
    public void reorder(Long playlistId, ReorderRequest request) {
        User user = currentUserProvider.getCurrentUser();
        loadForOwnerAction(playlistId, user);

        List<PlaylistSong> current = playlistSongRepository.findByPlaylistId(playlistId);
        List<Long> ids = request.getSongIds();

        if (ids == null || ids.size() != current.size()) {
            throw new InvalidPlaylistReorderException("Song IDs list size does not match playlist size");
        }

        Set<Long> uniqueIds = new HashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new InvalidPlaylistReorderException("Song IDs list contains duplicate entries");
        }

        Set<Long> currentIds = current.stream()
                .map(ps -> ps.getId().getSongId())
                .collect(Collectors.toSet());

        if (!currentIds.equals(uniqueIds)) {
            throw new InvalidPlaylistReorderException("Song IDs list does not match current songs in playlist");
        }

        Map<Long, PlaylistSong> bySongId = current.stream()
                .collect(Collectors.toMap(ps -> ps.getId().getSongId(), Function.identity()));

        for (int i = 0; i < ids.size(); i++) {
            bySongId.get(ids.get(i)).setPosition(i);
        }
    }

    private Playlist loadForOwnerAction(Long id, User user) {
        Playlist playlist = playlistRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PlaylistNotFoundException(id));

        if (playlist.getOwner().getId().equals(user.getId())) {
            return playlist;
        }

        if (playlist.isPublicPlaylist()) {
            throw new PlaylistAccessDeniedException(id);
        } else {
            throw new PlaylistNotFoundException(id);
        }
    }

    private PageResponse<PlaylistResponse> toPageResponse(Page<Playlist> page) {
        List<Long> ids = page.getContent().stream().map(Playlist::getId).toList();
        Map<Long, Long> counts = ids.isEmpty()
                ? Map.of()
                : playlistSongRepository.countByPlaylistIds(ids).stream()
                        .collect(Collectors.toMap(PlaylistSongCount::getPlaylistId, PlaylistSongCount::getSongCount));
        return PageResponse.of(page.map(p -> playlistMapper.toResponse(p, counts.getOrDefault(p.getId(), 0L))));
    }
}