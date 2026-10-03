package com.example.musicbackend.service;

import com.example.musicbackend.dto.request.ArtistRequest;
import com.example.musicbackend.dto.response.ArtistResponse;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ArtistService {
    List<ArtistResponse> getAll(String query);

    List<ArtistResponse> getTop(int limit);

    ArtistResponse getById(Long id);

    ArtistResponse create(ArtistRequest request);

    ArtistResponse update(Long id, ArtistRequest request);

    ArtistResponse updateImage(Long id, MultipartFile image);

    void delete(Long id);
}