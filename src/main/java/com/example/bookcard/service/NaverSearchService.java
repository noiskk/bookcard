package com.example.bookcard.service;

import com.example.bookcard.dto.BookSearchResult;
import com.example.bookcard.dto.NaverBookItem;
import com.example.bookcard.dto.NaverSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 네이버 도서 검색 API 연동 서비스
 *
 * 네이버 Open API의 도서 검색 엔드포인트를 호출해 책 정보를 조회한다.
 * Caffeine 캐시를 적용해 동일 검색어 반복 요청 시 API를 재호출하지 않는다.
 *
 * API 문서: https://developers.naver.com/docs/serviceapi/search/book/book.md
 * 인증: X-Naver-Client-Id / X-Naver-Client-Secret 헤더
 */
@Service
@Slf4j
public class NaverSearchService {

    private static final String NAVER_BOOK_API_URL = "https://openapi.naver.com/v1/search/book.json";

    @Value("${naver.client.id:}")
    private String clientId;

    @Value("${naver.client.secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    public NaverSearchService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 네이버 도서 API로 책 검색
     *
     * 검색 결과는 (query + display + start) 조합을 키로 Caffeine 캐시에 30분간 저장된다.
     * 같은 검색어로 반복 요청이 오면 캐시에서 즉시 반환해 API 할당량을 절약한다.
     *
     * @param query   검색어 (책 제목 또는 저자명)
     * @param display 한 번에 가져올 결과 수 (최대 100)
     * @param start   검색 시작 위치 (1부터 시작, 페이지네이션용)
     * @return 검색 결과 목록. API 키 미설정 또는 오류 시 빈 리스트 반환
     */
    @Cacheable(value = "naverSearch", key = "#query + '_' + #display + '_' + #start")
    public List<BookSearchResult> searchBooks(String query, int display, int start) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            log.warn("Naver API credentials not configured. Returning empty results.");
            return Collections.emptyList();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(NAVER_BOOK_API_URL)
                    .queryParam("query", query)
                    .queryParam("display", display)
                    .queryParam("start", start)
                    .queryParam("sort", "sim")  // 정확도 순 정렬
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Naver-Client-Id", clientId);
            headers.set("X-Naver-Client-Secret", clientSecret);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Searching Naver Books API for: {} (display={}, start={})", query, display, start);

            ResponseEntity<NaverSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    NaverSearchResponse.class
            );

            NaverSearchResponse body = response.getBody();
            if (body == null || body.getItems() == null) {
                return Collections.emptyList();
            }

            log.info("Found {} books from Naver API", body.getItems().size());

            return body.getItems().stream()
                    .map(this::toBookSearchResult)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching Naver Books API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 네이버 API 응답 항목을 내부 DTO로 변환
     *
     * 네이버 API는 제목·저자에 HTML 태그(<b>)가 포함될 수 있으므로 제거한다.
     *
     * @param item 네이버 API 응답의 개별 도서 항목
     * @return 내부 검색 결과 DTO
     */
    private BookSearchResult toBookSearchResult(NaverBookItem item) {
        return BookSearchResult.builder()
                .title(item.getCleanTitle())    // HTML 태그 제거
                .author(item.getCleanAuthor())  // HTML 태그 제거
                .publisher(item.getPublisher())
                .image(item.getImage())
                .isbn(item.getIsbn())
                .description(item.getDescription() != null ?
                        item.getDescription().replaceAll("<[^>]*>", "") : null)  // 설명의 HTML 태그도 제거
                .build();
    }
}
