package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.service.RecentlyPlayedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recently-played")
@RequiredArgsConstructor
public class RecentlyPlayedController {

    private final RecentlyPlayedService recentlyPlayedService;

    @GetMapping
    public List<SongResponse> getMyHistory() {
        return recentlyPlayedService.getMyHistory();
    }

    @PostMapping("/{songId}")
    public ResponseEntity<Void> record(@PathVariable Long songId) {
        recentlyPlayedService.record(songId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        recentlyPlayedService.clear();
        return ResponseEntity.noContent().build();
    }
}