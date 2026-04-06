package com.example.bookcard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 *
 * 모든 HTTP 요청마다 한 번씩 실행되어 Authorization 헤더의 JWT 토큰을 검증하고
 * 유효한 경우 SecurityContext에 인증 정보를 저장한다.
 *
 * 처리 흐름:
 * 1. Authorization: Bearer {token} 헤더 추출
 * 2. JWT에서 이메일 파싱 → DB에서 사용자 조회
 * 3. 토큰 유효성 검증 (서명 + 만료)
 * 4. SecurityContext에 인증 객체 저장 → 이후 컨트롤러에서 principal 사용 가능
 *
 * 토큰이 없거나 유효하지 않으면 인증 없이 다음 필터로 넘어간다.
 * (공개 API는 SecurityConfig에서 permitAll 처리)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * 요청별 JWT 인증 처리
     *
     * @param request  HTTP 요청 (Authorization 헤더 포함)
     * @param response HTTP 응답
     * @param filterChain 다음 필터로 체인 전달
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Bearer 토큰이 없으면 인증 없이 다음 필터로 통과
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " 이후의 실제 토큰 문자열 추출
        String token = authHeader.substring(7);
        try {
            String email = jwtUtil.extractEmail(token);

            // 이메일이 있고 아직 인증되지 않은 경우에만 처리 (중복 인증 방지)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtUtil.isValid(token, userDetails)) {
                    // 인증 객체 생성 후 SecurityContext에 저장
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // 유효하지 않은 토큰은 인증 없이 통과 (공개 엔드포인트 허용을 위해)
            logger.debug("JWT validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
