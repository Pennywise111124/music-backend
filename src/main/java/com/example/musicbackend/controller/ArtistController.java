package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.service.ArtistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @GetMapping
    public List<ArtistResponse> getAll(@RequestParam(required = false) String query) {
        return artistService.getAll(query);
    }

    @GetMapping("/top")
    public List<ArtistResponse> getTop(@RequestParam(defaultValue = "10") int limit) {
        return artistService.getTop(limit);
    }

    @GetMapping("/{id}")
    public ArtistResponse getById(@PathVariable Long id) {
        return artistService.getById(id);
    }
}