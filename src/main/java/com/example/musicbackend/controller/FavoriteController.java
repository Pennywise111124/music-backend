package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public PageResponse<SongResponse> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return favoriteService.getMyFavorites(page, size);
    }

    @PostMapping("/{songId}")
    public ResponseEntity<Void> add(@PathVariable Long songId) {
        favoriteService.add(songId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{songId}")
    public ResponseEntity<Void> remove(@PathVariable Long songId) {
        favoriteService.remove(songId);
        return ResponseEntity.noContent().build();
    }
}