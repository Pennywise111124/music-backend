package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.service.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    @GetMapping
    public PageResponse<SongResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return songService.search(query, page, size);
    }

    @GetMapping("/by-artist/{artistId}")
    public List<SongResponse> getByArtist(@PathVariable Long artistId) {
        return songService.getByArtist(artistId);
    }

    @GetMapping("/by-album/{albumId}")
    public List<SongResponse> getByAlbum(@PathVariable Long albumId) {
        return songService.getByAlbum(albumId);
    }

    @GetMapping("/{id}")
    public SongResponse getById(@PathVariable Long id) {
        return songService.getById(id);
    }
}