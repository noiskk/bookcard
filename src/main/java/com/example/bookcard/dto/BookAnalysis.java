package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 1단계 프롬프트 체이닝 결과: 책 분석 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAnalysis {

    private String genre;           // 장르 (예: 소설, 에세이, 자기계발)
    private String mood;            // 분위기 (예: 따뜻한, 우울한, 희망적인)
    private List<String> themes;    // 핵심 테마들 (예: 사랑, 성장, 상실)
    private List<String> emotions;  // 감정 키워드 (예: 그리움, 위로, 설렘)
    private String visualStyle;     // 시각적 스타일 제안 (예: 수채화풍, 미니멀)
    private List<String> colors;    // 어울리는 색상들 (예: 파스텔 블루, 따뜻한 오렌지)
    private String era;             // 시대 배경 (예: 현대, 조선시대, 미래)
    private String setting;         // 배경 장소 (예: 도시, 자연, 바다)
    private String uniqueHighlight; // 이 책만의 핵심 요소 (시리즈/장르 내 다른 책과 구별되는 것)
    private String bookCategory;    // 세부 카테고리: series_fiction | standalone_fiction | essay | poetry_experimental | self_help | history_humanities | science
    private String knowledgeLevel;  // GPT의 이 책에 대한 지식 수준: high | medium | low
}
