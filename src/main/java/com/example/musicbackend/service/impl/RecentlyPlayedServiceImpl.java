package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.entity.RecentlyPlayed;
import com.example.musicbackend.entity.User;
import com.example.musicbackend.exception.SongNotFoundException;
import com.example.musicbackend.mapper.SongMapper;
import com.example.musicbackend.repository.RecentlyPlayedRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.security.CurrentUserProvider;
import com.example.musicbackend.service.RecentlyPlayedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentlyPlayedServiceImpl implements RecentlyPlayedService {

    @Value("${app.recently-played.max-history:30}")
    private int maxHistory;

    private final RecentlyPlayedRepository recentlyPlayedRepository;
    private final SongRepository songRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SongMapper songMapper;

    @Override
    public List<SongResponse> getMyHistory() {
        User user = currentUserProvider.getCurrentUser();

        Sort sort = Sort.by(
                Sort.Order.desc("playedAt"),
                Sort.Order.desc("id.songId"));
        Pageable pageable = PageRequest.of(0, maxHistory, sort);

        List<RecentlyPlayed> recentList = recentlyPlayedRepository.findRecent(user.getId(), pageable);

        return recentList.stream()
                .map(r -> songMapper.toResponse(r.getSong()))
                .toList();
    }

    @Override
    @Transactional
    public void record(Long songId) {
        User user = currentUserProvider.getCurrentUser();

        if (!songRepository.existsById(songId)) {
            throw new SongNotFoundException(songId);
        }

        recentlyPlayedRepository.upsert(user.getId(), songId);
    }

    @Override
    @Transactional
    public void clear() {
        User user = currentUserProvider.getCurrentUser();

        recentlyPlayedRepository.deleteByUserId(user.getId());
    }

    @Override
    @Scheduled(cron = "${app.recently-played.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void trimHistory() {
        int removed = recentlyPlayedRepository.trimToMax(maxHistory);
        log.info("Trimmed {} old recently-played rows", removed);
    }
}