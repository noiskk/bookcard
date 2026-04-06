package com.example.bookcard.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 로그인 요청 DTO
 *
 * @Valid로 유효성 검사 후 AuthController가 처리한다.
 */
@Getter
public class LoginRequest {

    /** 사용자 이메일 (이메일 형식 필수) */
    @Email
    @NotBlank
    private String email;

    /** 사용자 비밀번호 (필수) */
    @NotBlank
    private String password;
}
