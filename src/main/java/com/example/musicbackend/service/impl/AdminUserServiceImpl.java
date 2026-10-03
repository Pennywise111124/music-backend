package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.response.AdminUserResponse;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.entity.Role;
import com.example.musicbackend.entity.User;
import com.example.musicbackend.exception.AdminActionNotAllowedException;
import com.example.musicbackend.exception.UserNotFoundException;
import com.example.musicbackend.repository.ArtistRepository;
import com.example.musicbackend.repository.FavoriteSongRepository;
import com.example.musicbackend.repository.FollowedArtistRepository;
import com.example.musicbackend.repository.PlaylistRepository;
import com.example.musicbackend.repository.PlaylistSongRepository;
import com.example.musicbackend.repository.RecentlyPlayedRepository;
import com.example.musicbackend.repository.UserRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.AdminUserService;
import com.example.musicbackend.storage.StorageService;
import com.example.musicbackend.storage.TxCallbacks;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FollowedArtistRepository followedArtistRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteSongRepository favoriteSongRepository;
    private final RecentlyPlayedRepository recentlyPlayedRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final PlaylistRepository playlistRepository;
    private final StorageService storageService;

    @Override
    public PageResponse<AdminUserResponse> getUsers(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Sort sort = Sort.by("username").ascending().and(Sort.by("id").ascending());
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<User> userPage;
        if (query == null || query.isBlank()) {
            userPage = userRepository.findAll(pageable);
        } else {
            String q = query.trim();
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, pageable);
        }

        return PageResponse.of(userPage.map(this::toResponse));
    }

    @Override
    @Transactional
    public AdminUserResponse setBanned(Long id, boolean banned) {
        User current = currentUserProvider.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        assertManageable(target, current, "ban");

        target.setBanned(banned);

        return toResponse(target);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User current = currentUserProvider.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        assertManageable(target, current, "delete");

        artistRepository.decrementPopularityForUserFollows(id);

        followedArtistRepository.deleteByUserId(id);

        favoriteSongRepository.deleteByUserId(id);

        recentlyPlayedRepository.deleteByUserId(id);

        List<String> images = playlistRepository.findImageUrlsByOwnerId(id);

        playlistSongRepository.deleteByPlaylistOwnerId(id);

        playlistRepository.deleteByOwnerId(id);

        userRepository.deleteById(id);

        images.forEach(u -> TxCallbacks.afterCommit(() -> storageService.delete(u)));
    }

    private void assertManageable(User target, User current, String action) {
        if (target.getId().equals(current.getId())) {
            throw new AdminActionNotAllowedException("You cannot " + action + " yourself");
        }

        if (target.getRole() == Role.ADMIN) {
            throw new AdminActionNotAllowedException("You cannot " + action + " another admin");
        }
    }

    private AdminUserResponse toResponse(User u) {
        return AdminUserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .avatarUrl(u.getAvatarUrl())
                .bio(u.getBio())
                .role(u.getRole().name())
                .banned(u.isBanned())
                .createdAt(u.getCreatedAt())
                .build();
    }
}