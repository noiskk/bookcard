package com.example.bookcard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 북카드 엔티티
 *
 * 사용자가 생성한 AI 북카드를 저장한다.
 * ISBN이 있는 책은 유니크 제약으로 중복 생성을 방지한다.
 * 요약문은 순서가 중요한 값 타입 컬렉션이므로 @ElementCollection으로 관리한다.
 */
@Entity
@Table(name = "books")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISBN (있는 경우 유니크). 같은 ISBN의 북카드 중복 생성을 DB 레벨에서 방지 */
    @Column(unique = true)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String publisher;

    /** 네이버 API에서 가져온 원본 표지 이미지 URL */
    @Column(length = 1000)
    private String originalImage;

    /** Gemini가 생성한 AI 커버 이미지 로컬 경로 (예: /images/uuid.png) */
    @Column(length = 1000)
    private String generatedImage;

    /** 네이버 API에서 가져온 책 소개문 (AI 프롬프트 입력에 사용) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * GPT-4o가 생성한 한글 요약 문장 목록 (보통 5줄)
     * Book 없이 독립적으로 존재할 수 없는 값 타입이므로 @ElementCollection 사용.
     * book_summaries 테이블에 저장되며 line_order 컬럼으로 순서를 유지한다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "book_summaries", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "summary_line", length = 1000)
    @OrderColumn(name = "line_order")
    private List<String> summary;

    /**
     * 북카드를 생성한 사용자
     * JSON 직렬화 시 User 전체 정보 노출을 막기 위해 @JsonIgnore 적용.
     * 이메일만 노출하려면 getCreatedBy()를 사용한다.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User creator;

    /**
     * creator의 이메일만 JSON에 포함해 반환
     * 프론트엔드에서 "내 북카드" 여부를 판별하는 데 사용한다.
     */
    public String getCreatedBy() {
        return creator != null ? creator.getEmail() : null;
    }

    /** 좋아요 수. book_likes 테이블의 실제 레코드 수와 동기화한다 */
    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount = 0;

    /** 북카드 생성 일시. 삽입 시 자동 설정, 이후 변경 불가 */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 대표 이미지 URL 반환
     * AI 생성 이미지가 있으면 우선 반환, 없으면 원본 표지 이미지 반환
     */
    public String getCoverImage() {
        return generatedImage != null ? generatedImage : originalImage;
    }
}
