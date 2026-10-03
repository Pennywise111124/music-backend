package com.example.musicbackend.controller;

import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.dto.response.PageResponse;
import com.example.musicbackend.service.FollowService;
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
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @GetMapping("/followed")
    public PageResponse<ArtistResponse> getMyFollowed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return followService.getMyFollowed(page, size);
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable Long id) {
        followService.follow(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable Long id) {
        followService.unfollow(id);
        return ResponseEntity.noContent().build();
    }
}