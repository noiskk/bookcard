package com.example.bookcard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @Column(unique = true)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String publisher;

    @Column(length = 1000)
    private String originalImage;

    @Column(length = 1000)
    private String generatedImage;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "book_summaries", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "summary_line", length = 1000)
    @OrderColumn(name = "line_order")
    private List<String> summary;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User creator;

    // creator 이메일만 JSON에 포함 (User 전체 직렬화 방지)
    public String getCreatedBy() {
        return creator != null ? creator.getEmail() : null;
    }

    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public String getCoverImage() {
        return generatedImage != null ? generatedImage : originalImage;
    }
}
