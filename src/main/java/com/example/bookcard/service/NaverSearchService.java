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
                    .queryParam("sort", "sim")
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

    private BookSearchResult toBookSearchResult(NaverBookItem item) {
        return BookSearchResult.builder()
                .title(item.getCleanTitle())
                .author(item.getCleanAuthor())
                .publisher(item.getPublisher())
                .image(item.getImage())
                .isbn(item.getIsbn())
                .description(item.getDescription() != null ?
                        item.getDescription().replaceAll("<[^>]*>", "") : null)
                .build();
    }
}
