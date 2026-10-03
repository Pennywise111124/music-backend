package com.example.musicbackend.service;

import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;

public interface FavoriteService {
    PageResponse<SongResponse> getMyFavorites(int page, int size);

    void add(Long songId);

    void remove(Long songId);
}