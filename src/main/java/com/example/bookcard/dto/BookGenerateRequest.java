package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 북카드 생성 요청 DTO
 *
 * 프론트엔드의 책 검색 결과에서 선택한 책 정보와
 * Settings 페이지에서 설정한 AI 생성 옵션을 함께 전달한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookGenerateRequest {
    /** 책 ISBN. 중복 생성 방지와 DB 유니크 제약에 사용 */
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    /** 네이버 API에서 가져온 원본 표지 이미지 URL */
    private String originalImage;
    /** 네이버 API에서 가져온 책 소개 (AI 분석 프롬프트 입력에 활용) */
    private String description;

    // 사용자 설정값 (Settings 페이지)
    /** 요약 스타일: literary(문학적) | poetic(시적) | concise(간결) | storytelling(스토리텔링) */
    private String summaryStyle;
    /** 요약 길이: short(3문장) | medium(5문장) | long(7문장) */
    private String summaryLength;
    /** 사용자 커스텀 프롬프트 (추가 지시사항) */
    private String defaultPrompt;
}
