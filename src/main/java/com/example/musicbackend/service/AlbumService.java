package com.example.musicbackend.service;

import com.example.musicbackend.dto.request.AlbumRequest;
import com.example.musicbackend.dto.response.AlbumResponse;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface AlbumService {
    List<AlbumResponse> getAll(String query);

    List<AlbumResponse> getByArtist(Long artistId);

    AlbumResponse getById(Long id);

    AlbumResponse create(AlbumRequest request);

    AlbumResponse update(Long id, AlbumRequest request);

    AlbumResponse updateImage(Long id, MultipartFile image);

    void delete(Long id);
}