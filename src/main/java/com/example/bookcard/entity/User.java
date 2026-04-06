package com.example.bookcard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 사용자 엔티티
 *
 * Spring Security의 UserDetails를 구현해 인증 주체로 직접 사용한다.
 * 별도의 UserDetails 래퍼 클래스 없이 엔티티 자체가 인증 객체 역할을 한다.
 *
 * 비밀번호는 BCrypt 해시로만 저장하며, 이메일은 유니크 제약으로 중복을 방지한다.
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 식별자 겸 JWT subject. 유니크 제약으로 중복 방지 */
    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt 해시로 저장된 비밀번호 (평문 저장 금지) */
    @Column(nullable = false)
    private String password;

    /** 서비스 내 표시 이름. 헤더와 북카드 생성자 표시에 사용 */
    @Column(nullable = false)
    private String nickname;

    /** 가입 일시. 삽입 시 자동 설정, 이후 변경 불가 */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ========================================
    // UserDetails 구현 — Spring Security 인증에 필요한 메서드
    // ========================================

    /** 역할(권한) 목록. 이 서비스는 별도 역할 구분 없이 빈 목록 반환 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /** Spring Security가 사용자를 식별하는 username. 이 서비스는 이메일을 사용 */
    @Override
    public String getUsername() {
        return email;
    }

    /** 계정 만료 여부. 만료 기능 미사용 → 항상 유효 */
    @Override
    public boolean isAccountNonExpired() { return true; }

    /** 계정 잠금 여부. 잠금 기능 미사용 → 항상 해제 */
    @Override
    public boolean isAccountNonLocked() { return true; }

    /** 자격증명 만료 여부. 미사용 → 항상 유효 */
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /** 계정 활성화 여부. 미사용 → 항상 활성 */
    @Override
    public boolean isEnabled() { return true; }
}
