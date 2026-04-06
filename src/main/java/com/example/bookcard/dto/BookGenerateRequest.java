package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookGenerateRequest {
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String originalImage;
    private String description;

    // 사용자 설정값 (Settings 페이지)
    private String summaryStyle;    // literary, poetic, concise, storytelling
    private String summaryLength;   // short, medium, long
    private String defaultPrompt;   // 사용자 커스텀 프롬프트
}
