package com.example.musicbackend.dto.response;

import com.example.musicbackend.entity.Role;
import com.example.musicbackend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private Role role;

    // Chủ động KHÔNG có field password/passwordHash ở đây.
    // DTO tách biệt Entity chính là để tránh lộ field nhạy cảm ra ngoài API
    // (bài học DTO ở Phase 5 - Layered Architecture).

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .role(user.getRole())
                .build();
    }
}
