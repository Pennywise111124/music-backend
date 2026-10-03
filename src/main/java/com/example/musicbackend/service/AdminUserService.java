package com.example.musicbackend.service;

import com.example.musicbackend.dto.response.AdminUserResponse;
import com.example.musicbackend.dto.response.PageResponse;

public interface AdminUserService {
    PageResponse<AdminUserResponse> getUsers(String query, int page, int size);

    AdminUserResponse setBanned(Long id, boolean banned);

    void delete(Long id);
}