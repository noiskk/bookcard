package com.example.bookcard.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원가입/로그인 성공 응답 DTO
 *
 * 클라이언트는 token을 localStorage에 저장하고
 * 이후 요청의 Authorization: Bearer {token} 헤더에 포함해 전송한다.
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    /** 발급된 JWT 토큰 (유효기간 24시간) */
    private String token;
    /** 로그인한 사용자의 이메일 */
    private String email;
    /** 서비스 내 표시 닉네임 */
    private String nickname;
}
