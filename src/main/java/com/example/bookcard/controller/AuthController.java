package com.example.bookcard.controller;

import com.example.bookcard.dto.auth.AuthResponse;
import com.example.bookcard.dto.auth.LoginRequest;
import com.example.bookcard.dto.auth.RegisterRequest;
import com.example.bookcard.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 컨트롤러
 *
 * 회원가입과 로그인 엔드포인트를 제공한다.
 * 성공 시 JWT 토큰, 이메일, 닉네임을 응답으로 반환한다.
 *
 * 경로: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    /**
     * 회원가입 (POST /api/auth/register)
     *
     * 이메일 중복 여부를 확인하고 BCrypt로 비밀번호를 암호화한 후 저장한다.
     * 성공 시 JWT 토큰을 발급해 즉시 로그인 상태로 응답한다.
     *
     * @param request 이메일, 비밀번호(6자 이상), 닉네임(2~20자)
     * @return JWT 토큰 + 이메일 + 닉네임
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register attempt: email={}", request.getEmail());
        AuthResponse response = authService.register(request);
        log.info("Register success: email={}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * 로그인 (POST /api/auth/login)
     *
     * AuthenticationManager로 이메일/비밀번호를 검증한다.
     * 자격증명이 맞으면 새 JWT 토큰을 발급한다.
     * 자격증명이 틀리면 GlobalExceptionHandler가 401을 반환한다.
     *
     * @param request 이메일, 비밀번호
     * @return JWT 토큰 + 이메일 + 닉네임
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt: email={}", request.getEmail());
        // 자격증명 검증 (실패 시 BadCredentialsException → 401)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        AuthResponse response = authService.login(request.getEmail());
        log.info("Login success: email={}", request.getEmail());
        return ResponseEntity.ok(response);
    }
}
