package com.example.bookcard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 인메모리 캐시 설정
 *
 * 네이버 도서 검색 API 결과를 캐싱해 중복 API 호출을 방지한다.
 * 동일한 검색어가 반복 요청될 때 네이버 API 대신 메모리에서 즉시 반환해
 * 응답 속도를 높이고 외부 API 할당량을 절약한다.
 *
 * @see NaverSearchService 캐시 적용 대상 메서드
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine 기반 CacheManager 빈 등록
     *
     * 캐시명: "naverSearch"
     * - expireAfterWrite 30분: 마지막 쓰기 후 30분이 지나면 항목 자동 만료
     * - maximumSize 200: 최대 200개 검색어 조합을 메모리에 보관
     *   (200개 초과 시 가장 오래된 항목부터 제거 — LRU 방식)
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("naverSearch");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(200));
        return manager;
    }
}
