package com.example.bookcard.service;

import com.example.bookcard.dto.BookAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * AI 기반 북카드 콘텐츠 생성 서비스 (Spring AI + Gemini)
 *
 * 4단계 프롬프트 체이닝으로 북카드를 생성한다:
 *   1단계: 책 분석 (장르·분위기·테마·감정 JSON 추출) — GPT-4o
 *   2단계: 감성적 한글 요약 5문장 생성 — GPT-4o
 *   3단계: 분석+요약 기반 이미지 프롬프트 생성 — GPT-4o
 *   4단계: 이미지 생성 — Gemini 2.0 Flash (Image Generation)
 *
 * UserSettings 레코드로 사용자 커스텀 설정(스타일·길이·추가 지시)을 각 단계에 주입한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.image.model:gemini-2.0-flash-preview-image-generation}")
    private String geminiImageModel;

    /**
     * OpenAI ChatClient 빈이 등록되어 있으면 설정된 것으로 간주한다.
     * 헬스 체크나 초기화 확인에 사용된다.
     */
    public boolean isConfigured() {
        try {
            return chatClient != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================
    // 1단계: 책 분석 (Book Analysis)
    // ========================================

    /**
     * 책 정보를 GPT-4o로 분석하여 장르·분위기·테마·감정 등을 추출한다.
     * GPT가 해당 책을 알고 있으면 (knowledgeLevel=high) 제공된 소개 외에 자체 지식을 활용한다.
     * 파싱 실패 시 기본값(createDefaultAnalysis)을 반환하여 전체 체이닝이 중단되지 않도록 한다.
     *
     * @param title       책 제목
     * @param author      저자명
     * @param description 네이버 API에서 받은 책 소개
     * @return 분석 결과 BookAnalysis 객체
     */
    public BookAnalysis analyzeBook(String title, String author, String description) {
        try {
            String systemPrompt = """
                당신은 문학 전문가이자 책 분석가입니다.
                주어진 책 정보를 분석하여 JSON 형식으로 응답해주세요.
                반드시 아래 JSON 형식만 출력하고, 다른 텍스트는 포함하지 마세요.

                ★ 중요: 당신이 이 책을 알고 있다면 제공된 소개 외에 당신의 지식을 적극 활용하세요.
                책 소개가 빈약하거나 마케팅 문구 수준이더라도, 당신이 아는 내용으로 보완하세요.

                {
                    "genre": "장르 (예: 소설, 에세이, 자기계발, 시집, 판타지)",
                    "mood": "전체적인 분위기 (예: 따뜻한, 우울한, 희망적인, 긴장감 있는)",
                    "themes": ["핵심 테마1", "핵심 테마2", "핵심 테마3"],
                    "emotions": ["감정1", "감정2", "감정3"],
                    "visualStyle": "어울리는 시각적 스타일 (예: 수채화풍, 유화풍, 미니멀, 동양화풍)",
                    "colors": ["색상1", "색상2", "색상3"],
                    "era": "시대 배경 (예: 현대, 1980년대, 조선시대, 미래)",
                    "setting": "주요 배경 장소 (예: 도시, 시골, 바다, 산, 학교)",
                    "bookCategory": "다음 중 하나만 선택: series_fiction(시리즈 소설) | standalone_fiction(단편/독립 소설) | essay(에세이/산문) | poetry_experimental(시집/실험문학) | self_help(자기계발/경영) | history_humanities(역사/인문) | science(과학/기술)",
                    "knowledgeLevel": "당신이 이 책에 대해 알고 있는 수준: high(구체적 내용 알고 있음) | medium(대략적으로 알고 있음) | low(제공된 소개만으로 판단)",
                    "uniqueHighlight": "이 책만이 가진 핵심 요소를 구체적으로 1~2문장 서술."
                }
                """;

            String userPrompt = String.format(
                "다음 책을 분석해주세요:\n\n제목: %s\n저자: %s\n설명: %s",
                title, author, description != null ? description : "정보 없음"
            );

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            BookAnalysis analysis = parseBookAnalysis(response);
            log.info("Book analysis completed for '{}': genre={}, mood={}", title, analysis.getGenre(), analysis.getMood());
            return analysis;

        } catch (Exception e) {
            log.error("Error analyzing book: {}", e.getMessage(), e);
            return createDefaultAnalysis(title);
        }
    }

    // ========================================
    // 2단계: 한글 요약 생성 (Korean Summary)
    // ========================================

    /** 사용자 설정 없이 한글 요약을 생성한다 (기본 설정 사용). */
    public List<String> generateSummary(String title, String author, String description, BookAnalysis analysis) {
        return generateSummary(title, author, description, analysis, UserSettings.empty());
    }

    /**
     * 1단계 분석 결과와 사용자 설정을 바탕으로 감성적 한글 요약 문장들을 생성한다.
     *
     * 시스템 프롬프트는 다음 요소를 동적으로 조합한다:
     * - buildLengthInstruction: 문장 수 (3·5·7개)
     * - buildStyleInstruction: 문학적·시적·간결·스토리텔링 톤
     * - buildCategoryStrategy: 장르별 글쓰기 전략 (시리즈 소설/에세이/시집 등)
     * - buildKnowledgeInstruction: GPT 지식 수준에 따른 활용도 지시
     * - buildCustomPromptInstruction: 사용자 커스텀 지시사항
     *
     * @param userSettings 사용자 설정 (요약 스타일·길이·커스텀 프롬프트)
     */
    public List<String> generateSummary(String title, String author, String description, BookAnalysis analysis,
                                         UserSettings userSettings) {
        try {
            String categoryStrategy = buildCategoryStrategy(analysis);
            String knowledgeInstruction = buildKnowledgeInstruction(analysis);
            String styleInstruction = buildStyleInstruction(userSettings);
            String lengthInstruction = buildLengthInstruction(userSettings);
            String customPromptInstruction = buildCustomPromptInstruction(userSettings);

            String systemPrompt = String.format("""
                당신은 서점 POP, 독립서점 추천 카드, 출판사 띠지 문구를 쓰는 카피라이터입니다.
                목표: 이 글을 읽은 사람이 "이 책 당장 읽고 싶다"는 감정을 느끼게 하는 것.

                반드시 지켜야 할 규칙:
                1. 순수 JSON 배열만 출력하세요. 다른 텍스트 없이.
                2. %s
                3. 한글로 작성.

                %s

                ★ 문장 스타일 규칙 (가장 중요):
                - 문장 길이를 섞으세요. 짧은 문장(10~20자)과 긴 문장(30~60자)을 교차.
                - "A는 B다" 단언형만 반복하지 마세요. 다양한 문장 구조를 쓰세요:
                  · 질문형: "왜 우리는 ~할까?"
                  · 묘사형: "차가운 밤공기처럼 스며드는 문장들."
                  · 조건형: "~한다면, 이 책이 그 답이 될 수 있다."
                  · 여운형: "그리고 다시, 처음처럼."
                  · 구체적 장면: "새벽 3시, 마지막 페이지를 덮고 한참을 멍하니 앉아 있었다."
                - 5개 문장 중 "~다" 로 끝나는 문장은 최대 2개까지만.
                - 첫 문장은 강렬한 한 줄 훅. 마지막 문장은 여운.

                %s

                %s

                %s

                절대 하지 말 것:
                - "A는 B다. C는 D다." 같은 격언/명언 나열체
                - "~임을 알게 된다", "~을 겪는다" 같은 줄거리 요약 문체
                - "매력적이다", "흥미롭다", "감동적이다" 같은 평가 형용사
                - 어느 책에나 붙일 수 있는 뻔한 문장
                - 모든 문장이 비슷한 길이와 구조로 반복되는 것
                """, lengthInstruction, styleInstruction, categoryStrategy, knowledgeInstruction, customPromptInstruction);

            String userPrompt = String.format("""
                이 책을 읽고 싶게 만드는 5개의 문장을 JSON 배열로 작성하세요.

                제목: %s
                저자: %s
                책 소개: %s
                장르: %s / 분위기: %s
                핵심 테마: %s
                주요 감정: %s

                ★ 이 책만의 핵심 요소 (반드시 이것을 중심으로 쓰세요): %s
                """,
                title, author,
                description != null ? description : "정보 없음",
                analysis.getGenre(),
                analysis.getMood(),
                String.join(", ", analysis.getThemes()),
                String.join(", ", analysis.getEmotions()),
                analysis.getUniqueHighlight() != null ? analysis.getUniqueHighlight() : "책 소개를 참고하여 이 책만의 요소를 직접 파악하세요"
            );

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            List<String> summary = parseSummaryJson(response);
            log.info("Korean summary generated for '{}': {} sentences", title, summary.size());
            return summary;

        } catch (Exception e) {
            log.error("Error generating summary: {}", e.getMessage(), e);
            return generateMockSummary(title, author);
        }
    }

    /** 책 분석을 내부적으로 먼저 실행한 후 한글 요약을 생성하는 편의 메서드. */
    public List<String> generateSummary(String title, String author, String description) {
        BookAnalysis analysis = analyzeBook(title, author, description);
        return generateSummary(title, author, description, analysis);
    }

    // ========================================
    // 3단계: 이미지 프롬프트 생성
    // ========================================

    /**
     * 책 분석 결과와 한글 요약을 바탕으로 Gemini에 전달할 영어 이미지 프롬프트를 생성한다.
     * 한국/동아시아 미학 감수성을 반영하며, 텍스트·글자 요소 없는 순수 이미지를 요구한다.
     *
     * @param analysis 1단계 책 분석 결과
     * @param summary  2단계 한글 요약 문장들
     * @return Gemini에 전달할 영어 이미지 프롬프트
     */
    public String generateImagePrompt(String title, String author, BookAnalysis analysis, List<String> summary) {
        try {
            String systemPrompt = """
                You are an expert at creating image prompts for book covers.

                Rules:
                1. Write the prompt in English.
                2. This is a Korean book — reflect Korean/East Asian aesthetic sensibility:
                   - Prefer styles like Korean ink wash, delicate watercolor, subtle oriental motifs, or clean modern Korean design.
                   - Avoid generic Western fantasy or Hollywood-style imagery unless the genre clearly calls for it.
                3. ABSOLUTELY NO text, letters, words, characters, or writing of any kind anywhere in the image.
                   Not even partial letters, glyphs, or decorative script.
                4. Focus on mood, atmosphere, and symbolic imagery.
                5. Be specific about art style, color palette, and composition.
                6. Keep the prompt under 200 words.
                7. Output ONLY the prompt text, nothing else.
                """;

            String userPrompt = String.format("""
                Create an image prompt for this Korean book's cover:

                Title: %s / Author: %s
                Genre: %s / Mood: %s
                Themes: %s / Visual Style: %s
                Colors: %s / Era: %s / Setting: %s

                Summary: %s
                """,
                title, author,
                analysis.getGenre(), analysis.getMood(),
                String.join(", ", analysis.getThemes()), analysis.getVisualStyle(),
                String.join(", ", analysis.getColors()), analysis.getEra(), analysis.getSetting(),
                String.join(" ", summary)
            );

            String imagePrompt = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("Image prompt generated for '{}'", title);
            return imagePrompt.trim();

        } catch (Exception e) {
            log.error("Error generating image prompt: {}", e.getMessage(), e);
            // 폴백: 범용 수채화 스타일 프롬프트
            return String.format(
                "Artistic book cover illustration, dreamy watercolor style, soft pastel colors, " +
                "abstract representation of literature and emotion, no text or letters, " +
                "atmospheric and evocative, inspired by '%s' by '%s'", title, author
            );
        }
    }

    // ========================================
    // 4단계: 이미지 생성 (Gemini)
    // ========================================

    /** 이미지 생성 결과를 담는 레코드 (바이트 배열 + MIME 타입). */
    public record ImageResult(byte[] data, String mimeType) {}

    /**
     * Gemini API로 이미지를 생성하고 바이트 배열로 반환한다.
     * 안전 문구("No text, no letters...")를 프롬프트 뒤에 항상 추가한다.
     * 실패 시 null을 반환하여 이미지 없이도 북카드 저장이 가능하도록 한다.
     *
     * @param imagePrompt 3단계에서 생성된 영어 이미지 프롬프트
     * @return 이미지 바이트 + MIME 타입, 실패 시 null
     */
    public ImageResult generateImage(String imagePrompt) {
        try (Client client = Client.builder()
                .apiKey(geminiApiKey)
                .build()) {

            // 텍스트 생성 방지 안전 문구 추가
            String safePrompt = imagePrompt +
                    " No text, no letters, no words, no writing, no characters, no glyphs of any kind in the image.";
            GenerateContentResponse response = client.models.generateContent(
                    geminiImageModel, safePrompt, null);

            for (Part part : response.parts()) {
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        String mimeType = blob.mimeType().orElse("image/png");
                        log.info("Generated image via Gemini: mimeType={}", mimeType);
                        return new ImageResult(blob.data().get(), mimeType);
                    }
                }
            }
            log.warn("No image data in Gemini response");

        } catch (Exception e) {
            log.error("Error generating image with Gemini: {}", e.getMessage(), e);
        }
        return null;
    }

    // ========================================
    // 전체 체이닝 실행 (Full Chain)
    // ========================================

    /**
     * 사용자 설정값을 각 단계에 전달하기 위한 레코드.
     *
     * @param summaryStyle   요약 스타일: literary | poetic | concise | storytelling
     * @param summaryLength  요약 길이: short(3문장) | medium(5문장) | long(7문장)
     * @param defaultPrompt  사용자 커스텀 추가 지시사항
     */
    public record UserSettings(String summaryStyle, String summaryLength, String defaultPrompt) {
        /** 모든 설정이 null인 기본 설정을 반환한다. */
        public static UserSettings empty() {
            return new UserSettings(null, null, null);
        }
    }

    /** 4단계 체이닝 실행 결과를 담는 레코드. */
    public record GenerationResult(BookAnalysis analysis, List<String> summary, ImageResult imageResult) {}

    /** 콜백·설정 없이 전체 체이닝을 실행하는 편의 메서드. */
    public GenerationResult generateWithChaining(String title, String author, String description) {
        return generateWithChaining(title, author, description, null, null);
    }

    /** 사용자 설정 없이 진행 콜백만 받아 전체 체이닝을 실행하는 편의 메서드. */
    public GenerationResult generateWithChaining(String title, String author, String description,
                                                  Consumer<String> progressCallback) {
        return generateWithChaining(title, author, description, progressCallback, null);
    }

    /**
     * 4단계 프롬프트 체이닝을 순서대로 실행한다.
     * 각 단계 시작 시 progressCallback으로 SSE 이벤트를 전송한다 ("단계번호:메시지" 형식).
     * 단계별 소요 시간과 전체 소요 시간을 INFO 로그로 기록한다.
     *
     * @param progressCallback 단계별 진행 상황 콜백 (null이면 무시)
     * @param userSettings     사용자 설정값 (null이면 기본값 사용)
     */
    public GenerationResult generateWithChaining(String title, String author, String description,
                                                  Consumer<String> progressCallback,
                                                  UserSettings userSettings) {
        log.info("Starting prompt chaining for: {} by {}", title, author);
        long total = System.currentTimeMillis();

        if (progressCallback != null) progressCallback.accept("1:책의 장르와 분위기를 분석하고 있습니다...");
        long t1 = System.currentTimeMillis();
        BookAnalysis analysis = analyzeBook(title, author, description);
        log.info("[Chain 1/4] 책 분석 완료: {}ms", System.currentTimeMillis() - t1);

        if (progressCallback != null) progressCallback.accept("2:감성적인 한글 요약을 작성하고 있습니다...");
        long t2 = System.currentTimeMillis();
        UserSettings settings = userSettings != null ? userSettings : UserSettings.empty();
        List<String> summary = generateSummary(title, author, description, analysis, settings);
        log.info("[Chain 2/4] 한글 요약 완료: {}ms", System.currentTimeMillis() - t2);

        if (progressCallback != null) progressCallback.accept("3:예술적인 이미지 컨셉을 구상하고 있습니다...");
        long t3 = System.currentTimeMillis();
        String imagePrompt = generateImagePrompt(title, author, analysis, summary);
        log.info("[Chain 3/4] 이미지 프롬프트 생성 완료: {}ms", System.currentTimeMillis() - t3);

        if (progressCallback != null) progressCallback.accept("4:Gemini로 커버 이미지를 생성하고 있습니다...");
        long t4 = System.currentTimeMillis();
        ImageResult imageResult = generateImage(imagePrompt);
        log.info("[Chain 4/4] 이미지 생성 완료: {}ms", System.currentTimeMillis() - t4);

        log.info("[Chain Total] 전체 소요시간: {}ms", System.currentTimeMillis() - total);
        return new GenerationResult(analysis, summary, imageResult);
    }

    // ========================================
    // 내부 헬퍼 메서드
    // ========================================

    /**
     * GPT 응답에서 JSON 코드 블록 마커(```json ... ```)를 제거하고 BookAnalysis로 역직렬화한다.
     * 파싱 실패 시 기본값(createDefaultAnalysis)을 반환한다.
     */
    private BookAnalysis parseBookAnalysis(String content) {
        try {
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(content, BookAnalysis.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse book analysis JSON: {}", e.getMessage());
            return createDefaultAnalysis("Unknown");
        }
    }

    /**
     * GPT 응답에서 JSON 배열 형태의 요약 문장을 파싱한다.
     * JSON 파싱 실패 시 문장 부호(. ! ?)로 분리하는 폴백 로직을 적용한다.
     */
    private List<String> parseSummaryJson(String content) {
        try {
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(content, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON summary: {}", e.getMessage());
            if (content.contains(".")) {
                return Arrays.asList(content.split("(?<=[.!?])\\s+"));
            }
            return List.of(content);
        }
    }

    /**
     * 책 카테고리(bookCategory)에 따라 한글 요약 작성 전략을 지시하는 프롬프트 블록을 반환한다.
     * 시리즈 소설, 에세이, 시집, 자기계발 등 카테고리별로 차별화된 글쓰기 가이드를 제공한다.
     */
    private String buildCategoryStrategy(BookAnalysis analysis) {
        String category = analysis.getBookCategory() != null ? analysis.getBookCategory() : "";
        return switch (category) {
            case "series_fiction" -> """
                ★ 이 책은 시리즈 소설입니다. 전략:
                - 이 권만의 사건/반전/갈등에 집중. 시리즈 공통 뉘앙스 금지.
                - 좋은 예: "믿었던 사람이 처음부터 적이었다면? 그 배신의 무게가, 이번 이야기의 전부다."
                """;
            case "standalone_fiction" -> """
                ★ 이 책은 단독 소설입니다. 전략:
                - 핵심 갈등의 무게와 인물의 선택에 집중.
                - 좋은 예: "두 가지 선택지. 둘 다 누군가를 잃는다. 그래서 그녀는 세 번째 길을 만들었다."
                """;
            case "essay" -> """
                ★ 이 책은 에세이/산문입니다. 전략:
                - 저자의 구체적인 경험과 독특한 시선을 드러내세요.
                - 좋은 예: "아무도 주목하지 않는 골목에서, 이 사람은 세상을 다시 본다. 그 시선을 빌려 걸어보는 경험."
                """;
            case "poetry_experimental" -> """
                ★ 이 책은 시집/실험문학입니다. 전략:
                - 줄거리나 설명 금지. 감각과 이미지로만 승부.
                - 좋은 예: "흰 것들이 왜 그렇게 슬픈지. 이 책을 덮고 나서야, 창밖의 눈이 다르게 보였다."
                """;
            case "self_help" -> """
                ★ 이 책은 자기계발/경영서입니다. 전략:
                - 독자가 이 책을 읽기 전과 후의 구체적 변화에 집중.
                - 좋은 예: "매일 반복되는 실패에 숨겨진 하나의 패턴. 그걸 알아차리는 순간, 다음 날 아침이 달라진다."
                """;
            case "history_humanities" -> """
                ★ 이 책은 역사/인문서입니다. 전략:
                - 기존 통념을 뒤집는 관점의 충격을 전달.
                - 좋은 예: "우리가 당연하게 믿는 것들, 그것은 누군가 만든 이야기였을까? 1만 년의 역사가 그 답을 바꿔놓는다."
                """;
            case "science" -> """
                ★ 이 책은 과학/기술서입니다. 전략:
                - 놀라운 사실이나 반직관적 개념을 전면에 내세우세요.
                - 좋은 예: "지금 이 문장을 읽는 동안, 당신의 뇌는 스스로를 바꾸고 있다. 그 메커니즘을 알면 모든 게 달라진다."
                """;
            default -> """
                ★ 장르에 맞는 전략을 스스로 판단하세요:
                - 소설이면 갈등과 반전, 자기계발이면 변화, 에세이면 저자의 시선.
                """;
        };
    }

    /**
     * GPT의 책 지식 수준(knowledgeLevel)에 따라 요약 작성 시 자체 지식 활용 정도를 지시한다.
     * high: 자체 지식 적극 활용 / medium: 혼합 / low: 제공된 소개만 활용
     */
    private String buildKnowledgeInstruction(BookAnalysis analysis) {
        String level = analysis.getKnowledgeLevel() != null ? analysis.getKnowledgeLevel() : "low";
        return switch (level) {
            case "high" -> "★ 당신은 이 책을 잘 알고 있습니다. 제공된 소개에 얽매이지 말고 당신의 지식을 적극 활용해 구체적이고 정확한 문장을 쓰세요.";
            case "medium" -> "★ 당신은 이 책을 어느 정도 알고 있습니다. 제공된 소개와 당신의 지식을 함께 활용하세요.";
            default -> "★ 제공된 책 소개를 최대한 활용하되, 추상적인 표현은 피하고 소개에서 구체적인 소재를 뽑아내세요.";
        };
    }

    /**
     * 사용자가 선택한 요약 스타일(literary·poetic·concise·storytelling)에 따라
     * 프롬프트에 삽입할 문체 지시 문자열을 반환한다.
     */
    private String buildStyleInstruction(UserSettings settings) {
        if (settings == null || settings.summaryStyle() == null) return "";
        return switch (settings.summaryStyle()) {
            case "literary" -> "★ 스타일: 문학적 분석 톤으로 작성하세요. 깊이 있는 통찰과 문학적 표현을 사용하세요.";
            case "poetic" -> "★ 스타일: 시적이고 예술적인 톤으로 작성하세요. 은유와 감각적 이미지를 적극 활용하세요.";
            case "concise" -> "★ 스타일: 간결하고 직접적인 톤으로 작성하세요. 군더더기 없이 핵심만 전달하세요.";
            case "storytelling" -> "★ 스타일: 스토리텔링 톤으로 작성하세요. 마치 이야기를 들려주듯 자연스럽게 풀어가세요.";
            default -> "";
        };
    }

    /**
     * 사용자가 선택한 요약 길이(short·medium·long)에 따라
     * 생성할 문장 수를 지시하는 프롬프트 문자열을 반환한다.
     */
    private String buildLengthInstruction(UserSettings settings) {
        if (settings == null || settings.summaryLength() == null) return "5개의 문장. 전체 흐름이 하나의 짧은 글처럼 읽혀야 합니다.";
        return switch (settings.summaryLength()) {
            case "short" -> "3개의 문장. 짧고 강렬하게, 핵심만 담아 작성하세요.";
            case "long" -> "7개의 문장. 여유 있게 풀어가되, 글의 밀도를 유지하세요.";
            default -> "5개의 문장. 전체 흐름이 하나의 짧은 글처럼 읽혀야 합니다."; // medium
        };
    }

    /**
     * 사용자가 입력한 커스텀 프롬프트를 프롬프트에 삽입할 지시 문자열로 변환한다.
     * 값이 없으면 빈 문자열을 반환한다.
     */
    private String buildCustomPromptInstruction(UserSettings settings) {
        if (settings == null || settings.defaultPrompt() == null || settings.defaultPrompt().isBlank()) return "";
        return "★ 사용자 추가 지시: " + settings.defaultPrompt();
    }

    /** 책 분석 API 호출 또는 파싱에 실패했을 때 사용할 기본 BookAnalysis 객체를 생성한다. */
    private BookAnalysis createDefaultAnalysis(String title) {
        return BookAnalysis.builder()
                .genre("문학")
                .mood("감성적인")
                .themes(List.of("인생", "성장", "사랑"))
                .emotions(List.of("감동", "희망", "그리움"))
                .visualStyle("수채화풍")
                .colors(List.of("파스텔 블루", "따뜻한 베이지", "부드러운 핑크"))
                .era("현대")
                .setting("도시")
                .uniqueHighlight("이 책만의 특별한 이야기")
                .bookCategory("standalone_fiction")
                .knowledgeLevel("low")
                .build();
    }

    /** 요약 생성에 실패했을 때 반환하는 범용 폴백 요약 문장 목록. */
    private List<String> generateMockSummary(String title, String author) {
        return Arrays.asList(
            String.format("<%s>이 펼쳐내는 특별한 이야기가 시작된다.", title),
            String.format("%s 작가만의 섬세한 시선이 담겨 있다.", author),
            "한 장 한 장 넘길수록 빠져드는 매력이 있다.",
            "읽는 내내 마음 한켠이 따뜻해진다.",
            "오늘, 이 책과 함께하는 시간을 가져보세요."
        );
    }
}
