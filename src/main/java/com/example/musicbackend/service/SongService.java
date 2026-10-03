package com.example.musicbackend.service;

import com.example.musicbackend.dto.request.SongRequest;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SongService {
    PageResponse<SongResponse> search(String query, int page, int size);

    List<SongResponse> getByArtist(Long artistId);

    List<SongResponse> getByAlbum(Long albumId);

    SongResponse getById(Long id);

    SongResponse create(SongRequest request, MultipartFile audio, MultipartFile cover);

    SongResponse update(Long id, SongRequest request);

    SongResponse replaceAudio(Long id, MultipartFile audio);

    SongResponse replaceCover(Long id, MultipartFile cover);

    void delete(Long id);
}