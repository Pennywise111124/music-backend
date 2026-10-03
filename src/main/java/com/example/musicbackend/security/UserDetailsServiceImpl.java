package com.example.musicbackend.security;

import com.example.musicbackend.entity.User;
import com.example.musicbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security cần 1 UserDetailsService để biết "user trong hệ thống của tôi
 * là ai".
 * Đây là lớp "dịch" từ User entity (của mình) sang UserDetails (Spring Security
 * hiểu).
 *
 * Lưu ý: "username" tham số ở đây là giá trị người dùng nhập lúc login,
 * có thể là username HOẶC email (giữ đúng hành vi app cũ) - xử lý ở
 * UserRepository.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Không tìm thấy user: " + usernameOrEmail));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(user.isBanned()) // user bị ban -> Spring Security tự chặn login
                .build();
    }
}
