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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register attempt: email={}", request.getEmail());
        AuthResponse response = authService.register(request);
        log.info("Register success: email={}", request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt: email={}", request.getEmail());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        AuthResponse response = authService.login(request.getEmail());
        log.info("Login success: email={}", request.getEmail());
        return ResponseEntity.ok(response);
    }
}
