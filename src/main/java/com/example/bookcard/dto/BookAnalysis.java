package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 프롬프트 체이닝 1단계 결과: 책 분석 정보 DTO
 *
 * GPT-4o가 책 제목·저자·소개를 바탕으로 장르·분위기·테마·감정 등을 분석한 결과를 담는다.
 * 이 결과는 2단계(한글 요약 생성)와 3단계(이미지 프롬프트 생성) 입력값으로 연쇄 사용된다.
 *
 * bookCategory와 knowledgeLevel 값에 따라 이후 단계의 프롬프트 전략이 달라진다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAnalysis {

    /** 장르 (예: 소설, 에세이, 자기계발, 시집) */
    private String genre;
    /** 전체적인 분위기 (예: 따뜻한, 우울한, 희망적인) */
    private String mood;
    /** 핵심 테마 목록 (예: 사랑, 성장, 상실) */
    private List<String> themes;
    /** 감정 키워드 목록 (예: 그리움, 위로, 설렘) */
    private List<String> emotions;
    /** 어울리는 시각적 스타일 제안 (예: 수채화풍, 미니멀, 동양화풍) */
    private String visualStyle;
    /** 어울리는 색상 팔레트 (예: 파스텔 블루, 따뜻한 오렌지) */
    private List<String> colors;
    /** 시대 배경 (예: 현대, 1980년대, 조선시대, 미래) */
    private String era;
    /** 주요 배경 장소 (예: 도시, 시골, 바다) */
    private String setting;
    /**
     * 이 책만의 핵심 요소 (1~2문장).
     * 2단계 요약 생성 시 다른 책과 구별되는 고유 포인트를 중심으로 문장을 작성하도록 지시한다.
     */
    private String uniqueHighlight;
    /**
     * 세부 카테고리: series_fiction | standalone_fiction | essay |
     * poetry_experimental | self_help | history_humanities | science
     * 2단계에서 카테고리별 글쓰기 전략 선택에 사용된다.
     */
    private String bookCategory;
    /**
     * GPT의 이 책에 대한 지식 수준: high | medium | low
     * high면 GPT 자체 지식을 적극 활용, low면 제공된 소개에만 의존한다.
     */
    private String knowledgeLevel;
}
