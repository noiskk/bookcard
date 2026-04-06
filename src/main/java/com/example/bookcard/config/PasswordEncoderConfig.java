package com.example.bookcard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더 설정
 *
 * SecurityConfig에서 PasswordEncoder를 직접 정의하면
 * SecurityConfig → AuthService → PasswordEncoder → SecurityConfig 순환 참조가 발생한다.
 * 이를 방지하기 위해 별도 설정 클래스로 분리해 의존 사이클을 끊었다.
 *
 * BCrypt: 단방향 해시 + 자동 솔트 생성으로 레인보우 테이블 공격을 방어한다.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt 비밀번호 인코더 빈 등록
     * 회원가입 시 비밀번호 암호화, 로그인 시 비밀번호 검증에 사용된다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
