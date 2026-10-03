package com.example.musicbackend.config;

import com.example.musicbackend.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Đây là bản làm sẵn (boilerplate config, nhiều annotation ít dùng nên dễ gõ
 * sai).
 * Đọc kỹ comment để hiểu bản chất từng phần - phần bạn thật sự cần TỰ VIẾT là
 * JwtUtil và JwtAuthFilter (logic nghiệp vụ), không phải file wiring này.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    // BCrypt: mỗi lần hash cùng 1 password sẽ ra hash KHÁC NHAU (do salt ngẫu
    // nhiên),
    // nhưng matches() vẫn verify đúng. Đây chính là điểm khác biệt với so sánh
    // plaintext trực tiếp như code Android cũ.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Provider nói cho Spring Security biết: "lấy user bằng userDetailsService này,
    // và verify password bằng passwordEncoder này".
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // AuthenticationManager là cái AuthServiceImpl sẽ gọi để verify login
    // (thay vì tự so sánh password thủ công).
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API thuần, không dùng session cookie -> tắt CSRF (CSRF chủ yếu để bảo vệ
                // form-based session auth, không áp dụng cho JWT stateless API - bài 15 đã
                // học).
                .csrf(AbstractHttpConfigurer::disable)

                // Không tạo HttpSession - mỗi request tự chứng minh danh tính qua JWT,
                // server không lưu trạng thái đăng nhập (khác hẳn "isLoggedIn" flag trong DB
                // cũ).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public - không cần token
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/files/**").permitAll() // serve audio/ảnh tĩnh
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Còn lại: chỉ cần đã login (có token hợp lệ)
                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                org.springframework.http.HttpStatus.UNAUTHORIZED)))

                .authenticationProvider(authenticationProvider())

                // Chèn JwtAuthFilter chạy TRƯỚC UsernamePasswordAuthenticationFilter mặc định
                // của Spring Security, vì mình xác thực bằng JWT chứ không phải form login.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
