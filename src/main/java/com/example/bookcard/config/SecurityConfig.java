package com.example.bookcard.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정
 *
 * JWT 기반 Stateless 인증을 구성한다.
 * 세션을 사용하지 않아 서버 메모리에 상태를 저장하지 않으며,
 * 모든 요청은 JWT 토큰으로만 인증된다.
 *
 * 인증 필터 순서: CorsFilter → JwtAuthFilter → 엔드포인트 인가 규칙
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * HTTP 보안 필터 체인 구성
     *
     * 엔드포인트별 접근 규칙 (위에서 아래로 우선순위 순서):
     * - ASYNC dispatch: SSE 비동기 완료 시 Tomcat 내부 재실행 허용
     * - OPTIONS: CORS Preflight 요청 전체 허용
     * - /api/auth/**: 회원가입·로그인은 인증 없이 허용
     * - GET /api/books/my: 인증 필요 (아래 books/** permitAll보다 먼저 매칭)
     * - GET /api/books, /api/books/**: 북카드 조회·검색은 비회원도 허용
     * - POST /api/books/generate*: 북카드 생성은 인증 필요
     * - DELETE /api/books/**: 삭제는 인증 필요
     * - POST /api/books/{id}/like: 좋아요는 인증 필요
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)  // REST API는 CSRF 불필요
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 세션 미사용
                .authorizeHttpRequests(auth -> auth
                        // SSE async dispatch 허용 (Tomcat이 async 완료 시 재실행하는 dispatch)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // CORS preflight 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 인증 없이 허용
                        .requestMatchers("/api/auth/**").permitAll()
                        // 내 북카드 조회는 인증 필요 (GET /api/books/** permitAll보다 먼저 매칭)
                        .requestMatchers(HttpMethod.GET, "/api/books/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/books", "/api/books/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 북카드 생성/삭제/좋아요는 인증 필요
                        .requestMatchers(HttpMethod.POST, "/api/books/generate", "/api/books/generate/stream").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/books/*/like").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)  // JWT 필터를 기본 인증 필터 앞에 삽입
                .build();
    }

    /**
     * DB 기반 인증 프로바이더 설정
     * UserDetailsService로 사용자를 조회하고 BCrypt로 비밀번호를 검증한다.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * AuthenticationManager 빈 등록
     * AuthController에서 로그인 시 자격증명 검증에 사용한다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}
