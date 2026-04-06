package com.example.bookcard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 북카드 좋아요 엔티티
 *
 * 사용자와 북카드 간의 좋아요 관계를 저장한다.
 * (book_id, user_id) 복합 유니크 제약으로 동일 사용자의 중복 좋아요를 방지한다.
 *
 * 좋아요 토글 흐름:
 * - 레코드 없음 → 삽입 (좋아요 추가)
 * - 레코드 있음 → 삭제 (좋아요 취소)
 * 토글 후 book_likes 테이블의 실제 count를 Book.likeCount에 동기화한다.
 */
@Entity
@Table(name = "book_likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"book_id", "user_id"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 좋아요 대상 북카드. LAZY 로딩으로 불필요한 Book 조회를 방지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** 좋아요를 누른 사용자. LAZY 로딩으로 불필요한 User 조회를 방지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 좋아요 생성 일시. 삽입 시 자동 설정, 이후 변경 불가 */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
