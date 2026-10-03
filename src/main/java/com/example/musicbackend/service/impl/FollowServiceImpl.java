package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.entity.FollowedArtist;
import com.example.musicbackend.entity.User;
import com.example.musicbackend.exception.ArtistNotFoundException;
import com.example.musicbackend.mapper.ArtistMapper;
import com.example.musicbackend.repository.ArtistRepository;
import com.example.musicbackend.repository.FollowedArtistRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.FollowService;
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
public class FollowServiceImpl implements FollowService {

    private static final int MAX_PAGE_SIZE = 50;

    private final FollowedArtistRepository followedArtistRepository;
    private final ArtistRepository artistRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ArtistMapper artistMapper;

    @Override
    public PageResponse<ArtistResponse> getMyFollowed(int page, int size) {
        User user = currentUserProvider.getCurrentUser();

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by(
                Sort.Order.desc("followedAt"),
                Sort.Order.desc("id.artistId"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<FollowedArtist> followedPage = followedArtistRepository.findByUserId(user.getId(), pageable);

        Page<ArtistResponse> responsePage = followedPage.map(f -> artistMapper.toResponse(f.getArtist(), true));

        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional
    public void follow(Long artistId) {
        User user = currentUserProvider.getCurrentUser();

        if (!artistRepository.existsById(artistId)) {
            throw new ArtistNotFoundException(artistId);
        }

        int inserted = followedArtistRepository.insertIfAbsent(user.getId(), artistId);

        if (inserted == 1) {
            artistRepository.incrementPopularity(artistId);
        }
    }

    @Override
    @Transactional
    public void unfollow(Long artistId) {
        User user = currentUserProvider.getCurrentUser();

        int deleted = followedArtistRepository.deleteByUserIdAndArtistId(user.getId(), artistId);

        if (deleted == 1) {
            artistRepository.decrementPopularity(artistId);
        }
    }
}