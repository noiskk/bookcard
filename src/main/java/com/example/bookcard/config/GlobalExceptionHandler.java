package com.example.bookcard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 *
 * 컨트롤러에서 발생한 예외를 한 곳에서 처리해 일관된 에러 응답 형식을 보장한다.
 * 모든 응답은 {"message": "..."}  또는 {"필드명": "메시지"} 형태의 JSON으로 반환한다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Bean Validation 실패 처리 (400 Bad Request)
     * @Valid 어노테이션으로 검증 시 실패한 필드별 메시지를 Map으로 반환한다.
     * 예: {"email": "올바른 이메일 형식이 아닙니다", "password": "비밀번호는 6자 이상이어야 합니다"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * DB 유니크 제약 위반 처리 (400 Bad Request)
     * ISBN 중복 삽입 등 데이터 무결성 위반 시 호출된다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("message", "이미 존재하는 북카드입니다."));
    }

    /**
     * 잘못된 인자 예외 처리 (400 Bad Request)
     * 서비스 계층에서 비즈니스 규칙 위반 시 throw하는 예외를 처리한다.
     * 예: "이미 사용 중인 이메일", "이미 생성된 북카드가 있습니다 (ID: 123)"
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    /**
     * 접근 권한 없음 처리 (403 Forbidden)
     * 본인이 만들지 않은 북카드를 삭제하려 할 때 등 인가 실패 시 호출된다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    /**
     * 로그인 자격증명 오류 처리 (401 Unauthorized)
     * AuthenticationManager가 이메일/비밀번호 불일치 시 던지는 예외를 처리한다.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다"));
    }

    /**
     * 그 외 처리되지 않은 예외 (500 Internal Server Error)
     * 예상치 못한 서버 오류를 클라이언트에 노출하지 않고 로그만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "서버 오류가 발생했습니다"));
    }
}
