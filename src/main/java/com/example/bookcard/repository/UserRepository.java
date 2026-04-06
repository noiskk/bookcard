package com.example.bookcard.repository;

import com.example.bookcard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 레포지토리
 *
 * 이메일 기반으로 사용자를 조회한다.
 * 이메일은 로그인 식별자이자 JWT의 subject로 사용한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자 조회
     * AuthService의 로그인 및 UserDetailsService 구현에서 사용한다.
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 존재 여부 확인
     * 회원가입 시 중복 이메일 체크에 사용한다.
     */
    boolean existsByEmail(String email);
}
