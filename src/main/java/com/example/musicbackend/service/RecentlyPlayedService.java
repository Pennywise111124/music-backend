package com.example.musicbackend.service;

import com.example.musicbackend.dto.response.SongResponse;

import java.util.List;

public interface RecentlyPlayedService {
    List<SongResponse> getMyHistory();

    void record(Long songId);

    void clear();

    void trimHistory();
}