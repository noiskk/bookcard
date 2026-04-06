package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 네이버 도서 API 응답의 개별 도서 항목 DTO
 *
 * 네이버 API는 제목·저자 필드에 검색어 강조를 위한 HTML 태그(<b>)를 포함한다.
 * getCleanTitle(), getCleanAuthor()로 태그를 제거한 순수 텍스트를 가져온다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaverBookItem {
    private String title;
    private String author;
    private String publisher;
    /** 표지 이미지 URL */
    private String image;
    private String isbn;
    private String description;
    /** 상세 페이지 링크 */
    private String link;
    /** 출판일 (yyyyMMdd 형식) */
    private String pubdate;

    /**
     * HTML 태그를 제거한 순수 책 제목 반환
     * 예: "&lt;b&gt;채식주의자&lt;/b&gt;" → "채식주의자"
     */
    public String getCleanTitle() {
        return title != null ? title.replaceAll("<[^>]*>", "") : null;
    }

    /**
     * HTML 태그를 제거한 순수 저자명 반환
     * 예: "&lt;b&gt;한강&lt;/b&gt;" → "한강"
     */
    public String getCleanAuthor() {
        return author != null ? author.replaceAll("<[^>]*>", "") : null;
    }
}
