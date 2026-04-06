package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 네이버 책 검색 결과를 프론트엔드로 전달하는 DTO
 *
 * NaverBookItem에서 필요한 필드만 추려서 반환한다.
 * 검색 바에서 사용자가 책을 선택할 때 이 데이터를 기반으로 북카드 생성 요청을 보낸다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchResult {
    private String title;       // 책 제목 (HTML 태그 제거 후 순수 텍스트)
    private String author;      // 저자명
    private String publisher;   // 출판사
    /** 네이버 CDN 표지 이미지 URL */
    private String image;
    /** ISBN (13자리). 중복 생성 방지 및 DB 유니크 키로 사용 */
    private String isbn;
    /** 책 소개 (AI 요약 프롬프트 입력값으로 활용) */
    private String description;
}
