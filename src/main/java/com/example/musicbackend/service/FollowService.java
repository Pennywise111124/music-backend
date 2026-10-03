package com.example.musicbackend.service;

import com.example.musicbackend.dto.response.ArtistResponse;
import com.example.musicbackend.dto.response.PageResponse;

public interface FollowService {
    PageResponse<ArtistResponse> getMyFollowed(int page, int size);

    void follow(Long artistId);

    void unfollow(Long artistId);
}