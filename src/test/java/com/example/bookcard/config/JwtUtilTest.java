package com.example.bookcard.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-unit-testing-purposes-only";
    private static final long EXPIRATION_MS = 86400000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // given: 실제 JwtUtil 인스턴스 사용 (Spring 컨텍스트 불필요)
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    // ========================================
    // generateToken() 테스트
    // ========================================

    @Test
    @DisplayName("generateToken(): 이메일로 JWT 토큰을 생성하면 null이 아닌 값을 반환한다")
    void generateToken_returnsNonNullToken() {
        // given
        String email = "user@example.com";

        // when
        String token = jwtUtil.generateToken(email);

        // then
        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        // JWT 형식: header.payload.signature (점 2개)
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("generateToken(): 동일한 이메일로 생성된 토큰은 시간 차이로 인해 서로 다를 수 있다")
    void generateToken_sameEmail_producesValidToken() {
        // given
        String email = "user@example.com";

        // when
        String token = jwtUtil.generateToken(email);

        // then
        assertThat(token).isNotEmpty();
        // 생성된 토큰에서 이메일을 다시 추출했을 때 일치해야 한다
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }

    // ========================================
    // extractEmail() 테스트
    // ========================================

    @Test
    @DisplayName("extractEmail(): 토큰에서 이메일(subject)을 정확히 추출한다")
    void extractEmail_returnsCorrectEmail() {
        // given
        String email = "extract@example.com";
        String token = jwtUtil.generateToken(email);

        // when
        String extractedEmail = jwtUtil.extractEmail(token);

        // then
        assertThat(extractedEmail).isEqualTo(email);
    }

    @Test
    @DisplayName("extractEmail(): 서로 다른 이메일로 생성된 토큰은 각각 올바른 이메일을 반환한다")
    void extractEmail_differentEmails_returnsEach() {
        // given
        String email1 = "alice@example.com";
        String email2 = "bob@example.com";
        String token1 = jwtUtil.generateToken(email1);
        String token2 = jwtUtil.generateToken(email2);

        // when & then
        assertThat(jwtUtil.extractEmail(token1)).isEqualTo(email1);
        assertThat(jwtUtil.extractEmail(token2)).isEqualTo(email2);
    }

    // ========================================
    // isValid() 테스트
    // ========================================

    @Test
    @DisplayName("isValid(): 토큰의 이메일과 UserDetails의 username이 일치하면 true를 반환한다")
    void isValid_matchingEmail_returnsTrue() {
        // given
        String email = "valid@example.com";
        String token = jwtUtil.generateToken(email);
        UserDetails userDetails = buildUserDetails(email);

        // when
        boolean result = jwtUtil.isValid(token, userDetails);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid(): 토큰의 이메일과 UserDetails의 username이 다르면 false를 반환한다")
    void isValid_differentEmail_returnsFalse() {
        // given
        String tokenEmail = "original@example.com";
        String otherEmail = "other@example.com";
        String token = jwtUtil.generateToken(tokenEmail);
        UserDetails userDetails = buildUserDetails(otherEmail);

        // when
        boolean result = jwtUtil.isValid(token, userDetails);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid(): 만료된 토큰이면 false를 반환한다")
    void isValid_expiredToken_returnsFalse() {
        // given: 만료 시간 -1ms로 즉시 만료되는 JwtUtil 생성
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -1L);
        String email = "expired@example.com";
        String expiredToken = expiredJwtUtil.generateToken(email);
        UserDetails userDetails = buildUserDetails(email);

        // when & then: 만료된 토큰은 파싱 시 예외 또는 isValid가 false여야 한다
        try {
            boolean result = jwtUtil.isValid(expiredToken, userDetails);
            assertThat(result).isFalse();
        } catch (Exception e) {
            // 만료된 토큰은 JwtException 계열의 예외가 발생할 수 있으며, 이 역시 유효하지 않음을 의미한다
            assertThat(e).isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }

    // ========================================
    // 헬퍼 메서드
    // ========================================

    private UserDetails buildUserDetails(String email) {
        return User.builder()
                .username(email)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }
}
