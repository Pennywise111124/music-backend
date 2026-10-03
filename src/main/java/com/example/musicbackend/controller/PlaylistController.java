package com.example.musicbackend.controller;

import com.example.musicbackend.dto.request.PlaylistRequest;
import com.example.musicbackend.dto.request.ReorderRequest;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.dto.response.PlaylistDetailResponse;
import com.example.musicbackend.dto.response.PlaylistResponse;
import com.example.musicbackend.service.PlaylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    @GetMapping("/me")
    public PageResponse<PlaylistResponse> getMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return playlistService.getMine(page, size);
    }

    @GetMapping("/public")
    public PageResponse<PlaylistResponse> getPublic(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return playlistService.getPublic(query, page, size);
    }

    @GetMapping("/{id}")
    public PlaylistDetailResponse getById(@PathVariable Long id) {
        return playlistService.getById(id);
    }

    @PostMapping
    public ResponseEntity<PlaylistResponse> create(@Valid @RequestBody PlaylistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistService.create(request));
    }

    @PutMapping("/{id}")
    public PlaylistResponse update(@PathVariable Long id, @Valid @RequestBody PlaylistRequest request) {
        return playlistService.update(id, request);
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlaylistResponse updateImage(@PathVariable Long id, @RequestPart("image") MultipartFile image) {
        return playlistService.updateImage(id, image);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        playlistService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/songs/{songId}")
    public ResponseEntity<Void> addSong(@PathVariable Long id, @PathVariable Long songId) {
        playlistService.addSong(id, songId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public ResponseEntity<Void> removeSong(@PathVariable Long id, @PathVariable Long songId) {
        playlistService.removeSong(id, songId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reorder")
    public ResponseEntity<Void> reorder(@PathVariable Long id, @Valid @RequestBody ReorderRequest request) {
        playlistService.reorder(id, request);
        return ResponseEntity.noContent().build();
    }
}