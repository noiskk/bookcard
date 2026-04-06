package com.example.bookcard.service;

import com.example.bookcard.config.JwtUtil;
import com.example.bookcard.dto.auth.AuthResponse;
import com.example.bookcard.dto.auth.RegisterRequest;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 인증 서비스
 *
 * 회원가입, 로그인, Spring Security의 UserDetailsService를 구현한다.
 * 비밀번호는 BCrypt로 암호화해 저장하고, 인증 성공 시 JWT 토큰을 발급한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 회원가입 처리
     *
     * 이메일 중복 여부를 확인하고 비밀번호를 BCrypt로 암호화한 후 저장한다.
     * 저장 성공 시 JWT 토큰을 즉시 발급해 별도 로그인 없이 사용할 수 있게 한다.
     *
     * @param request 이메일, 비밀번호, 닉네임
     * @return JWT 토큰 + 이메일 + 닉네임
     * @throws IllegalArgumentException 이미 사용 중인 이메일인 경우
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))  // 평문 비밀번호 → BCrypt 해시
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getNickname());
    }

    /**
     * 로그인 처리
     *
     * AuthController에서 AuthenticationManager가 자격증명을 먼저 검증한 후 호출된다.
     * 이 메서드는 검증 완료된 사용자의 JWT 토큰만 발급한다.
     *
     * @param email 검증이 완료된 사용자 이메일
     * @return JWT 토큰 + 이메일 + 닉네임
     */
    public AuthResponse login(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getNickname());
    }

    /**
     * Spring Security UserDetailsService 구현
     *
     * JwtAuthFilter에서 JWT의 이메일로 사용자를 조회할 때 호출된다.
     * User 엔티티가 UserDetails를 구현하므로 그대로 반환한다.
     *
     * @param email JWT의 subject (사용자 이메일)
     * @return UserDetails (User 엔티티)
     * @throws UsernameNotFoundException 해당 이메일의 사용자가 없는 경우
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }
}
