package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.AlbumResponse;
import com.example.musicbackend.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    @GetMapping
    public List<AlbumResponse> getAll(@RequestParam(required = false) String query) {
        return albumService.getAll(query);
    }

    @GetMapping("/by-artist/{artistId}")
    public List<AlbumResponse> getByArtist(@PathVariable Long artistId) {
        return albumService.getByArtist(artistId);
    }

    @GetMapping("/{id}")
    public AlbumResponse getById(@PathVariable Long id) {
        return albumService.getById(id);
    }
}