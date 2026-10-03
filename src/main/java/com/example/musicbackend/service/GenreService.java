package com.example.musicbackend.service;

import com.example.musicbackend.dto.request.GenreRequest;
import com.example.musicbackend.dto.response.GenreResponse;

import java.util.List;

public interface GenreService {
    List<GenreResponse> getAll();

    GenreResponse getById(Long id);

    GenreResponse create(GenreRequest request);

    GenreResponse update(Long id, GenreRequest request);

    void delete(Long id);
}