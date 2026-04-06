package com.example.bookcard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BookCard 애플리케이션 진입점
 *
 * AI 기반 북카드 생성 서비스.
 * 책 제목을 검색하면 GPT-4o가 분위기를 분석하고,
 * Gemini가 커버 이미지를 생성해 하나의 북카드로 저장한다.
 */
@SpringBootApplication
public class BookcardApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookcardApplication.class, args);
	}

}
