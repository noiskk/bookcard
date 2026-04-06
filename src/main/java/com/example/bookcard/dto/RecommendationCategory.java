package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 메인 페이지 카테고리별 추천 도서 DTO
 *
 * 소설·에세이·자기계발·인문학 등 카테고리 이름과
 * 해당 카테고리에서 네이버 API로 조회한 도서 목록을 함께 담는다.
 * BookService.getRecommendations()에서 빌드하며 1시간 단위로 캐시된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCategory {
    /** 카테고리 식별자 (예: "소설", "에세이") */
    private String category;
    /** 프론트엔드에 표시할 카테고리 이름 */
    private String displayName;
    /** 해당 카테고리의 추천 도서 목록 (최대 6권) */
    private List<BookSearchResult> books;
}
