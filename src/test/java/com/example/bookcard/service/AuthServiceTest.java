package com.example.bookcard.service;

import com.example.bookcard.config.JwtUtil;
import com.example.bookcard.dto.auth.AuthResponse;
import com.example.bookcard.dto.auth.RegisterRequest;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    // ========================================
    // register() 테스트
    // ========================================

    @Test
    @DisplayName("register(): 정상적인 회원가입 시 토큰과 사용자 정보를 반환한다")
    void register_success() {
        // given
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "test@example.com");
        setField(request, "password", "password123");
        setField(request, "nickname", "테스터");

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtUtil.generateToken("test@example.com")).willReturn("mock.jwt.token");

        // when
        AuthResponse response = authService.register(request);

        // then
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getNickname()).isEqualTo("테스터");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register(): 이미 사용 중인 이메일로 가입 시도 시 IllegalArgumentException이 발생한다")
    void register_duplicateEmail_throwsException() {
        // given
        RegisterRequest request = new RegisterRequest();
        setField(request, "email", "duplicate@example.com");
        setField(request, "password", "password123");
        setField(request, "nickname", "테스터");

        given(userRepository.existsByEmail("duplicate@example.com")).willReturn(true);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );
        assertThat(exception.getMessage()).contains("이미 사용 중인 이메일입니다");
    }

    // ========================================
    // login() 테스트
    // ========================================

    @Test
    @DisplayName("login(): 이메일로 로그인 시 토큰과 사용자 정보를 반환한다")
    void login_success() {
        // given
        String email = "test@example.com";
        User user = User.builder()
                .email(email)
                .password("encodedPassword")
                .nickname("테스터")
                .build();

        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(jwtUtil.generateToken(email)).willReturn("mock.jwt.token");

        // when
        AuthResponse response = authService.login(email);

        // then
        assertThat(response.getToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getNickname()).isEqualTo("테스터");
    }

    /**
     * Lombok @Getter 전용 DTO는 setter가 없으므로 reflection으로 필드 값을 주입한다.
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("필드 설정 실패: " + fieldName, e);
        }
    }
}
