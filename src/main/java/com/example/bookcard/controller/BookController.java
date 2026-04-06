package com.example.bookcard.controller;

import com.example.bookcard.dto.BookGenerateRequest;
import com.example.bookcard.dto.BookSearchResult;
import com.example.bookcard.dto.GenerationProgress;
import com.example.bookcard.dto.LikeResponse;
import com.example.bookcard.dto.RecommendationCategory;
import com.example.bookcard.entity.Book;
import com.example.bookcard.service.BookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 북카드 관련 REST API 컨트롤러
 *
 * 엔드포인트 목록:
 * - GET  /api/books              전체 북카드 조회 (하위 호환)
 * - GET  /api/books/paged        전체 북카드 페이지 조회
 * - GET  /api/books/my           내 북카드 페이지 조회 (인증 필요)
 * - GET  /api/books/{id}         특정 북카드 조회
 * - GET  /api/books/recommendations  카테고리별 추천 도서
 * - GET  /api/books/search       네이버 API 책 검색
 * - GET  /api/books/library/search   보관함 내부 검색
 * - POST /api/books/generate     북카드 생성 (동기)
 * - POST /api/books/generate/stream  북카드 생성 (SSE 스트리밍)
 * - DELETE /api/books/{id}       북카드 삭제
 * - POST /api/books/{id}/like    좋아요 토글
 * - GET  /api/books/{id}/like    좋아요 상태 조회
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final ObjectMapper objectMapper;
    /** 비동기 SSE 작업용 스레드 풀 (AppConfig에서 빈 등록) */
    private final ExecutorService executor;

    /**
     * 전체 북카드를 최신순으로 반환한다 (페이지네이션 없음, 하위 호환용).
     */
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    /**
     * 전체 북카드를 페이지네이션하여 반환한다.
     *
     * @param page 0 기반 페이지 번호 (기본값 0)
     * @param size 페이지당 항목 수 (기본값 12)
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<Book>> getPagedBooks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ResponseEntity.ok(bookService.getPagedBooks(PageRequest.of(page, size)));
    }

    /**
     * 현재 로그인한 사용자의 북카드만 페이지네이션하여 반환한다 (인증 필요).
     *
     * @param page 0 기반 페이지 번호 (기본값 0)
     * @param size 페이지당 항목 수 (기본값 12)
     */
    @GetMapping("/my")
    public ResponseEntity<Page<Book>> getMyBooks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ResponseEntity.ok(bookService.getMyBooks(PageRequest.of(page, size)));
    }

    /**
     * 특정 북카드를 ID로 조회한다.
     * 존재하지 않으면 404를 반환한다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable("id") Long id) {
        return bookService.getBookById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 메인 페이지에 표시할 카테고리별 추천 도서 목록을 반환한다.
     * 결과는 BookService 내부에서 1시간 캐시된다.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationCategory>> getRecommendations() {
        log.info("Fetching book recommendations by category");
        List<RecommendationCategory> recommendations = bookService.getRecommendations();
        return ResponseEntity.ok(recommendations);
    }

    /**
     * 네이버 책 검색 API를 호출하여 검색 결과를 반환한다.
     * 결과는 Caffeine 캐시(30분)에 저장되어 중복 요청 시 API를 재호출하지 않는다.
     *
     * @param query 검색어
     * @param start 검색 시작 위치 (1부터, 페이지 로드마다 증가)
     */
    @GetMapping("/search")
    public ResponseEntity<List<BookSearchResult>> searchBooks(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "start", defaultValue = "1") int start) {
        log.info("Searching books with query: {}, start: {}", query, start);
        List<BookSearchResult> results = bookService.searchBooksFromNaver(query, start);
        return ResponseEntity.ok(results);
    }

    /**
     * 보관함(DB)에 저장된 북카드를 키워드로 검색한다.
     * q 파라미터가 없으면 전체 목록을 반환한다.
     */
    @GetMapping("/library/search")
    public ResponseEntity<List<Book>> searchLibrary(@RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(bookService.searchBooksInLibrary(q));
    }

    /**
     * 북카드를 AI로 생성한다 (동기 방식).
     * 실시간 진행 상황이 필요 없는 환경에서 사용하는 단순 엔드포인트.
     */
    @PostMapping("/generate")
    public ResponseEntity<Book> generateBook(@RequestBody BookGenerateRequest request) {
        log.info("Generate request received for: {} by {}", request.getTitle(), request.getAuthor());
        Book generatedBook = bookService.generateBook(request);
        return ResponseEntity.ok(generatedBook);
    }

    /**
     * 북카드를 SSE(Server-Sent Events) 스트리밍 방식으로 생성한다.
     *
     * 동작 방식:
     * 1. SseEmitter를 즉시 반환하여 HTTP 연결을 유지한다.
     * 2. 별도 스레드(executor)에서 북카드 생성을 비동기로 수행한다.
     * 3. 각 단계 시작 시 "progress" 이벤트를 전송한다.
     * 4. 완료 시 생성된 Book 객체를 담은 "complete" 이벤트를 전송한다.
     * 5. ISBN 중복·DB 제약 위반·기타 오류 발생 시 "error" 이벤트를 전송한다.
     *
     * SecurityContext는 메인 스레드에서 복사하여 비동기 스레드에 주입한다.
     * (Spring Security 기본 ThreadLocal은 스레드 간 전파되지 않으므로 직접 전달)
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateBookWithProgress(@RequestBody BookGenerateRequest request) {
        log.info("SSE Generate request received for: {} by {}", request.getTitle(), request.getAuthor());

        // 5분 타임아웃 (GPT + Gemini 호출 합산 최대 ~21초를 고려한 여유 값)
        SseEmitter emitter = new SseEmitter(300000L);

        // SecurityContext를 비동기 스레드에 전달하기 위해 현재 컨텍스트를 캡처
        SecurityContext securityContext = SecurityContextHolder.getContext();

        executor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                // 진행 상황 콜백: "단계번호:메시지" 형식 문자열을 받아 SSE 이벤트로 변환
                Book generatedBook = bookService.generateBook(request, (progress) -> {
                    try {
                        String[] parts = progress.split(":", 2);
                        int step = Integer.parseInt(parts[0]);
                        String message = parts[1];

                        GenerationProgress progressDto = GenerationProgress.inProgress(step, message);
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(progressDto)));
                    } catch (IOException e) {
                        log.error("Failed to send SSE progress: {}", e.getMessage());
                    }
                });

                // 북카드 생성 완료 — Book 객체를 포함한 "complete" 이벤트 전송
                GenerationProgress completed = GenerationProgress.completed(generatedBook);
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(completed)));
                emitter.complete();

            } catch (DataIntegrityViolationException e) {
                // DB 유니크 제약 위반 (ISBN 중복) — 기존 북카드 ID를 조회하여 사용자 친화적 메시지로 변환
                log.warn("DataIntegrityViolation during SSE generation (isbn={}): {}",
                        request.getIsbn(), e.getMessage());
                try {
                    String isbn = request.getIsbn();
                    String message = "이미 존재하는 북카드입니다. 보관함에서 확인해주세요.";
                    if (isbn != null && !isbn.isBlank()) {
                        bookService.findByIsbn(isbn).ifPresent(existing ->
                                { throw new RuntimeException(
                                        "이미 생성된 북카드가 있습니다 (ID: " + existing.getId() + ")"); });
                    }
                    GenerationProgress error = GenerationProgress.error(message);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(error)));
                } catch (RuntimeException re) {
                    // findByIsbn이 던진 RuntimeException — 그 메시지를 그대로 SSE error 이벤트로 전달
                    try {
                        GenerationProgress error = GenerationProgress.error(re.getMessage());
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(error)));
                    } catch (IOException ignored) {}
                } catch (IOException ignored) {}
                emitter.complete();

            } catch (Exception e) {
                // 그 외 예외 — 오류 메시지를 SSE error 이벤트로 전달
                log.error("Error during SSE generation: {}", e.getMessage(), e);
                try {
                    GenerationProgress error = GenerationProgress.error("북카드 생성에 실패했습니다: " + e.getMessage());
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(error)));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                // 비동기 스레드의 SecurityContext를 반드시 정리
                SecurityContextHolder.clearContext();
            }
        });

        emitter.onCompletion(() -> log.info("SSE completed"));
        emitter.onTimeout(() -> log.warn("SSE timeout"));
        emitter.onError((e) -> log.error("SSE error: {}", e.getMessage()));

        return emitter;
    }

    /**
     * 북카드를 삭제한다.
     * 본인이 생성한 북카드만 삭제 가능하며, 연결된 이미지 파일도 함께 삭제된다.
     * 성공 시 204 No Content를 반환한다.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable("id") Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 북카드에 좋아요를 토글한다 (이미 좋아요 → 취소, 미좋아요 → 추가).
     * 토글 후 현재 likeCount와 liked 상태를 반환한다.
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<LikeResponse> likeBook(@PathVariable("id") Long id) {
        LikeResponse response = bookService.toggleLike(id);
        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인한 사용자의 특정 북카드 좋아요 여부를 조회한다.
     * 비로그인 상태면 liked=false로 반환한다.
     */
    @GetMapping("/{id}/like")
    public ResponseEntity<LikeResponse> getLikeStatus(@PathVariable("id") Long id) {
        boolean liked = bookService.isLikedByCurrentUser(id);
        Book book = bookService.getBookById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
        LikeResponse response = LikeResponse.builder()
                .bookId(id)
                .likeCount(book.getLikeCount())
                .liked(liked)
                .build();
        return ResponseEntity.ok(response);
    }
}
