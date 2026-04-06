package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 네이버 도서 검색 API의 최상위 응답 DTO
 *
 * 네이버 API가 반환하는 JSON을 그대로 매핑한다.
 * items 리스트의 각 항목은 NaverBookItem으로 역직렬화된다.
 * NaverSearchService에서 RestClient로 응답을 받아 파싱할 때 사용한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NaverSearchResponse {
    /** 검색 결과 생성 일시 (RFC 822 형식) */
    private String lastBuildDate;
    /** 전체 검색 결과 수 */
    private int total;
    /** 검색 시작 위치 */
    private int start;
    /** 한 번에 반환된 결과 수 */
    private int display;
    /** 개별 도서 항목 목록 */
    private List<NaverBookItem> items;
}
