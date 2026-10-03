package com.example.musicbackend.service;

import org.springframework.web.multipart.MultipartFile;

import com.example.musicbackend.dto.request.PlaylistRequest;
import com.example.musicbackend.dto.request.ReorderRequest;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.PlaylistDetailResponse;
import com.example.musicbackend.dto.response.PlaylistResponse;

public interface PlaylistService {
    PageResponse<PlaylistResponse> getMine(int page, int size);

    PageResponse<PlaylistResponse> getPublic(String query, int page, int size);

    PlaylistDetailResponse getById(Long id);

    PlaylistResponse create(PlaylistRequest request);

    PlaylistResponse update(Long id, PlaylistRequest request);

    PlaylistResponse updateImage(Long id, MultipartFile image);

    void delete(Long id);

    void addSong(Long playlistId, Long songId);

    void removeSong(Long playlistId, Long songId);

    void reorder(Long playlistId, ReorderRequest request);
}