package com.example.bookcard.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 회원가입 요청 DTO
 *
 * @Valid로 유효성 검사 후 AuthController가 처리한다.
 * 검증 실패 시 GlobalExceptionHandler가 필드별 오류 메시지를 반환한다.
 */
@Getter
public class RegisterRequest {

    /** 사용자 이메일. 이메일 형식이어야 하며 필수 입력 */
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @NotBlank(message = "이메일은 필수입니다")
    private String email;

    /** 비밀번호. 최소 6자 이상 필수 (BCrypt 암호화 후 저장) */
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
    private String password;

    /** 서비스 내 표시 이름. 2~20자 사이 필수 */
    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
    private String nickname;
}
