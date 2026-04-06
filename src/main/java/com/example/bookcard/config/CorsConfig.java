package com.example.bookcard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * CORS(Cross-Origin Resource Sharing) 설정
 *
 * 프론트엔드(React, :5173)에서 백엔드(:8080)로의 API 요청을 허용한다.
 * SecurityConfig보다 먼저 적용되어 Preflight(OPTIONS) 요청을 처리한다.
 */
@Configuration
public class CorsConfig {

    /**
     * CORS 필터 빈 등록
     *
     * 허용 출처: localhost:3000 (CRA), localhost:5173 (Vite)
     * 허용 메서드: GET, POST, PUT, DELETE, OPTIONS
     * 허용 헤더: 전체 (Authorization 포함)
     * 인증 정보 포함 허용: JWT 쿠키/헤더 전송을 위해 true 설정
     * 적용 경로: /api/**, /images/**
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 프론트엔드 개발 서버 허용
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173"
        ));

        // 모든 HTTP 메서드 허용
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Authorization 헤더를 포함한 모든 요청 헤더 허용
        config.setAllowedHeaders(Arrays.asList("*"));

        // JWT 인증 헤더 전송을 위해 자격 증명 허용
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/images/**", config);

        return new CorsFilter(source);
    }
}
