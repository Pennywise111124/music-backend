package com.example.musicbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    // Nhận username HOẶC email, giống hành vi login của app Android cũ
    @NotBlank(message = "Vui lòng nhập username hoặc email")
    private String usernameOrEmail;

    @NotBlank(message = "Password không được để trống")
    private String password;
}
