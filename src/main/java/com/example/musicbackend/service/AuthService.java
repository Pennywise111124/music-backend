package com.example.musicbackend.service;

import com.example.musicbackend.dto.request.LoginRequest;
import com.example.musicbackend.dto.request.RefreshRequest;
import com.example.musicbackend.dto.request.RegisterRequest;
import com.example.musicbackend.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);
}
