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

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    /**
     * Get all saved book cards (no pagination, for backward compatibility)
     */
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    /**
     * Get paged book cards
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<Book>> getPagedBooks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ResponseEntity.ok(bookService.getPagedBooks(PageRequest.of(page, size)));
    }

    /**
     * Get current user's book cards (authentication required)
     */
    @GetMapping("/my")
    public ResponseEntity<Page<Book>> getMyBooks(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ResponseEntity.ok(bookService.getMyBooks(PageRequest.of(page, size)));
    }

    /**
     * Get a specific book card by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable("id") Long id) {
        return bookService.getBookById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get curated book recommendations by category
     * Results are cached for 1 hour to avoid excessive Naver API calls
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationCategory>> getRecommendations() {
        log.info("Fetching book recommendations by category");
        List<RecommendationCategory> recommendations = bookService.getRecommendations();
        return ResponseEntity.ok(recommendations);
    }

    /**
     * Search books from Naver API (external search)
     * Used when user types in the search bar to find books to generate cards for
     * @param query 검색어
     * @param start 시작 위치 (1부터 시작, 기본값 1)
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
     * Search within saved library (internal search)
     */
    @GetMapping("/library/search")
    public ResponseEntity<List<Book>> searchLibrary(@RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(bookService.searchBooksInLibrary(q));
    }

    /**
     * Generate a new book card using AI (GPT + DALL-E)
     * Called when user selects a book from search results
     */
    @PostMapping("/generate")
    public ResponseEntity<Book> generateBook(@RequestBody BookGenerateRequest request) {
        log.info("Generate request received for: {} by {}", request.getTitle(), request.getAuthor());
        Book generatedBook = bookService.generateBook(request);
        return ResponseEntity.ok(generatedBook);
    }

    /**
     * Generate a new book card with SSE progress updates
     * Returns Server-Sent Events for real-time progress feedback
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateBookWithProgress(@RequestBody BookGenerateRequest request) {
        log.info("SSE Generate request received for: {} by {}", request.getTitle(), request.getAuthor());

        // 5분 타임아웃 설정
        SseEmitter emitter = new SseEmitter(300000L);

        SecurityContext securityContext = SecurityContextHolder.getContext();

        executor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                // 진행 상황 콜백
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

                // 완료 이벤트 전송
                GenerationProgress completed = GenerationProgress.completed(generatedBook);
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(completed)));
                emitter.complete();

            } catch (DataIntegrityViolationException e) {
                // DB unique 제약 위반 — ISBN으로 기존 북카드를 조회해 중복 에러 메시지로 변환
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
                    // findByIsbn이 던진 RuntimeException — 그 메시지를 SSE로 전달
                    try {
                        GenerationProgress error = GenerationProgress.error(re.getMessage());
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(objectMapper.writeValueAsString(error)));
                    } catch (IOException ignored) {}
                } catch (IOException ignored) {}
                emitter.complete();

            } catch (Exception e) {
                log.error("Error during SSE generation: {}", e.getMessage(), e);
                try {
                    GenerationProgress error = GenerationProgress.error("북카드 생성에 실패했습니다: " + e.getMessage());
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(error)));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        emitter.onCompletion(() -> log.info("SSE completed"));
        emitter.onTimeout(() -> log.warn("SSE timeout"));
        emitter.onError((e) -> log.error("SSE error: {}", e.getMessage()));

        return emitter;
    }

    /**
     * Delete a book card from the library
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable("id") Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle like on a book card (like/unlike)
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<LikeResponse> likeBook(@PathVariable("id") Long id) {
        LikeResponse response = bookService.toggleLike(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Check if current user has liked a book
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
