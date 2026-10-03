package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.entity.FavoriteSong;
import com.example.musicbackend.entity.User;
import com.example.musicbackend.exception.SongNotFoundException;
import com.example.musicbackend.mapper.SongMapper;
import com.example.musicbackend.repository.FavoriteSongRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteServiceImpl implements FavoriteService {

    private static final int MAX_PAGE_SIZE = 50;

    private final FavoriteSongRepository favoriteSongRepository;
    private final SongRepository songRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SongMapper songMapper;

    @Override
    public PageResponse<SongResponse> getMyFavorites(int page, int size) {
        User user = currentUserProvider.getCurrentUser();

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by(
                Sort.Order.desc("addedAt"),
                Sort.Order.desc("id.songId"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<FavoriteSong> favoritePage = favoriteSongRepository.findByUserId(user.getId(), pageable);

        Page<SongResponse> responsePage = favoritePage.map(f -> songMapper.toResponse(f.getSong()));

        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional
    public void add(Long songId) {
        User user = currentUserProvider.getCurrentUser();

        if (!songRepository.existsById(songId)) {
            throw new SongNotFoundException(songId);
        }

        favoriteSongRepository.insertIfAbsent(user.getId(), songId);
    }

    @Override
    @Transactional
    public void remove(Long songId) {
        User user = currentUserProvider.getCurrentUser();
        favoriteSongRepository.deleteByUserIdAndSongId(user.getId(), songId);
    }
}