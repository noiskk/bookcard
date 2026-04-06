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
 * OpenAI API 서비스 (Spring AI 기반) - 프롬프트 체이닝 적용
 *
 * 1단계: 책 분석 (장르, 분위기, 테마, 감정 추출)
 * 2단계: 분석 기반 한글 요약 생성
 * 3단계: 분석 + 요약 기반 이미지 프롬프트 생성 → DALL-E 호출
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

    public boolean isConfigured() {
        try {
            // ChatClient 빈이 등록되어 있으면 설정된 것으로 간주
            return chatClient != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================
    // 1단계: 책 분석 (Book Analysis)
    // ========================================

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

    public List<String> generateSummary(String title, String author, String description, BookAnalysis analysis) {
        return generateSummary(title, author, description, analysis, UserSettings.empty());
    }

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

    public List<String> generateSummary(String title, String author, String description) {
        BookAnalysis analysis = analyzeBook(title, author, description);
        return generateSummary(title, author, description, analysis);
    }

    // ========================================
    // 3단계: 이미지 프롬프트 생성
    // ========================================

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
            return String.format(
                "Artistic book cover illustration, dreamy watercolor style, soft pastel colors, " +
                "abstract representation of literature and emotion, no text or letters, " +
                "atmospheric and evocative, inspired by '%s' by '%s'", title, author
            );
        }
    }

    // ========================================
    // 4단계: 이미지 생성 (Gemini - Nano Banana)
    // ========================================

    public record ImageResult(byte[] data, String mimeType) {}

    public ImageResult generateImage(String imagePrompt) {
        try (Client client = Client.builder()
                .apiKey(geminiApiKey)
                .build()) {

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

    // 사용자 설정값을 전달하기 위한 레코드
    public record UserSettings(String summaryStyle, String summaryLength, String defaultPrompt) {
        public static UserSettings empty() {
            return new UserSettings(null, null, null);
        }
    }

    public record GenerationResult(BookAnalysis analysis, List<String> summary, ImageResult imageResult) {}

    public GenerationResult generateWithChaining(String title, String author, String description) {
        return generateWithChaining(title, author, description, null, null);
    }

    public GenerationResult generateWithChaining(String title, String author, String description,
                                                  Consumer<String> progressCallback) {
        return generateWithChaining(title, author, description, progressCallback, null);
    }

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
    // Helper Methods
    // ========================================

    private BookAnalysis parseBookAnalysis(String content) {
        try {
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(content, BookAnalysis.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse book analysis JSON: {}", e.getMessage());
            return createDefaultAnalysis("Unknown");
        }
    }

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

    private String buildKnowledgeInstruction(BookAnalysis analysis) {
        String level = analysis.getKnowledgeLevel() != null ? analysis.getKnowledgeLevel() : "low";
        return switch (level) {
            case "high" -> "★ 당신은 이 책을 잘 알고 있습니다. 제공된 소개에 얽매이지 말고 당신의 지식을 적극 활용해 구체적이고 정확한 문장을 쓰세요.";
            case "medium" -> "★ 당신은 이 책을 어느 정도 알고 있습니다. 제공된 소개와 당신의 지식을 함께 활용하세요.";
            default -> "★ 제공된 책 소개를 최대한 활용하되, 추상적인 표현은 피하고 소개에서 구체적인 소재를 뽑아내세요.";
        };
    }

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

    private String buildLengthInstruction(UserSettings settings) {
        if (settings == null || settings.summaryLength() == null) return "5개의 문장. 전체 흐름이 하나의 짧은 글처럼 읽혀야 합니다.";
        return switch (settings.summaryLength()) {
            case "short" -> "3개의 문장. 짧고 강렬하게, 핵심만 담아 작성하세요.";
            case "long" -> "7개의 문장. 여유 있게 풀어가되, 글의 밀도를 유지하세요.";
            default -> "5개의 문장. 전체 흐름이 하나의 짧은 글처럼 읽혀야 합니다."; // medium
        };
    }

    private String buildCustomPromptInstruction(UserSettings settings) {
        if (settings == null || settings.defaultPrompt() == null || settings.defaultPrompt().isBlank()) return "";
        return "★ 사용자 추가 지시: " + settings.defaultPrompt();
    }

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
