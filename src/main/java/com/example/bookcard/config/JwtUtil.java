package com.example.bookcard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT(JSON Web Token) 생성·검증 유틸리티
 *
 * HMAC SHA256 알고리즘으로 서명된 JWT를 생성하고 검증한다.
 * - Subject(sub): 사용자 이메일
 * - 유효 기간: 기본 24시간 (86400000ms), 환경변수로 조정 가능
 * - 서명 키: jwt.secret 환경변수 (32자 이상 권장)
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * 사용자 이메일을 subject로 담은 JWT 토큰 생성
     *
     * @param email 로그인한 사용자의 이메일
     * @return 서명된 JWT 문자열
     */
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 이메일(subject) 추출
     *
     * @param token JWT 문자열
     * @return 토큰의 subject (사용자 이메일)
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * JWT 토큰의 유효성 검증
     * 이메일 일치 여부와 만료 여부를 함께 확인한다.
     *
     * @param token       검증할 JWT 문자열
     * @param userDetails DB에서 조회한 사용자 정보
     * @return 유효하면 true, 만료되었거나 이메일 불일치 시 false
     */
    public boolean isValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isExpired(token);
    }

    /**
     * JWT 토큰 만료 여부 확인
     *
     * @param token JWT 문자열
     * @return 만료되었으면 true
     */
    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    /**
     * JWT 서명 검증 후 Claims(페이로드) 파싱
     * 서명이 위조되었거나 포맷이 잘못된 경우 예외를 던진다.
     *
     * @param token JWT 문자열
     * @return 파싱된 Claims 객체
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
