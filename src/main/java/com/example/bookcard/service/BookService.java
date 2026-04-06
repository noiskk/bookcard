package com.example.bookcard.service;

import com.example.bookcard.dto.BookGenerateRequest;
import com.example.bookcard.dto.BookSearchResult;
import com.example.bookcard.dto.LikeResponse;
import com.example.bookcard.dto.RecommendationCategory;
import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.BookLike;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.BookLikeRepository;
import com.example.bookcard.repository.BookRepository;
import com.example.bookcard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {

    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour

    /**
     * 동시에 동일 ISBN으로 생성 요청이 들어올 경우 AI 작업(~21초)이 중복 실행되는 것을 방지한다.
     * putIfAbsent: 키가 없으면 삽입하고 null 반환(락 획득), 이미 있으면 기존 값 반환(락 실패).
     */
    private final ConcurrentHashMap<String, Object> isbnInProgress = new ConcurrentHashMap<>();
    private static final Object LOCK_SENTINEL = new Object();

    private static final int RECOMMENDATIONS_PER_CATEGORY = 6;
    private static final Map<String, String[]> CATEGORY_QUERIES = new LinkedHashMap<>();

    static {
        CATEGORY_QUERIES.put("소설",        new String[]{"소설", "한국소설 추천"});
        CATEGORY_QUERIES.put("에세이",      new String[]{"에세이", "에세이 추천"});
        CATEGORY_QUERIES.put("자기계발",    new String[]{"자기계발", "자기계발 베스트"});
        CATEGORY_QUERIES.put("인문학",      new String[]{"인문학", "인문학 교양"});
    }

    // Simple in-memory cache: category key -> cached result + timestamp
    private final ConcurrentHashMap<String, Object[]> recommendationCache = new ConcurrentHashMap<>();
    // Sentinel key for the whole recommendations list
    private static final String CACHE_KEY = "recommendations";

    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;
    private final UserRepository userRepository;
    private final NaverSearchService naverSearchService;
    private final OpenAiService openAiService;
    private final ImageStorageService imageStorageService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다"));
    }

    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        return bookRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Book> getPagedBooks(Pageable pageable) {
        return bookRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Book> getMyBooks(Pageable pageable) {
        User user = getCurrentUser();
        return bookRepository.findByCreatorOrderByCreatedAtDesc(user, pageable);
    }

    @Transactional(readOnly = true)
    public List<Book> searchBooksInLibrary(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBooks();
        }
        return bookRepository.searchByKeyword(keyword.trim());
    }

    public List<BookSearchResult> searchBooksFromNaver(String query) {
        return searchBooksFromNaver(query, 1);
    }

    public List<BookSearchResult> searchBooksFromNaver(String query, int start) {
        return naverSearchService.searchBooks(query, 20, start);
    }

    @SuppressWarnings("unchecked")
    public List<RecommendationCategory> getRecommendations() {
        // Return from cache if still valid
        Object[] cached = recommendationCache.get(CACHE_KEY);
        if (cached != null) {
            long cachedAt = (long) cached[1];
            if (Instant.now().toEpochMilli() - cachedAt < CACHE_TTL_MS) {
                log.debug("Returning recommendations from cache");
                return (List<RecommendationCategory>) cached[0];
            }
        }

        log.info("Fetching fresh recommendations from Naver API");
        List<RecommendationCategory> result = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : CATEGORY_QUERIES.entrySet()) {
            String displayName = entry.getKey();
            String[] queries = entry.getValue();
            String searchQuery = queries[1];

            List<BookSearchResult> books = naverSearchService.searchBooks(
                    searchQuery, RECOMMENDATIONS_PER_CATEGORY, 1);

            result.add(RecommendationCategory.builder()
                    .category(displayName)
                    .displayName(displayName)
                    .books(books)
                    .build());

            log.info("Fetched {} books for category '{}'", books.size(), displayName);
        }

        // Store in cache
        recommendationCache.put(CACHE_KEY, new Object[]{result, Instant.now().toEpochMilli()});

        return result;
    }

    @Transactional
    public Book generateBook(BookGenerateRequest request) {
        return generateBook(request, null);
    }

    @Transactional
    public Book generateBook(BookGenerateRequest request, Consumer<String> progressCallback) {
        log.info("Generating book card for: {} by {}", request.getTitle(), request.getAuthor());

        final String isbnKey = (request.getIsbn() != null && !request.getIsbn().isBlank())
                ? request.getIsbn() : null;

        // ISBN 중복 체크 (DB)
        if (isbnKey != null) {
            bookRepository.findByIsbn(isbnKey).ifPresent(existing -> {
                throw new IllegalArgumentException("이미 생성된 북카드가 있습니다 (ID: " + existing.getId() + ")");
            });
            // 동시 생성 방지 락: 동일 ISBN을 처리 중인 요청이 있으면 즉시 거부
            if (isbnInProgress.putIfAbsent(isbnKey, LOCK_SENTINEL) != null) {
                throw new IllegalArgumentException(
                        "해당 책의 북카드가 현재 생성 중입니다. 잠시 후 보관함을 확인해주세요.");
            }
            log.debug("ISBN lock acquired: {}", isbnKey);
        }

        try {
            String title = request.getTitle();
            String author = request.getAuthor();
            String description = request.getDescription();

            // 사용자 설정값 추출
            OpenAiService.UserSettings userSettings = new OpenAiService.UserSettings(
                    request.getSummaryStyle(),
                    request.getSummaryLength(),
                    request.getDefaultPrompt()
            );

            // 프롬프트 체이닝 실행 (1단계 → 2단계 → 3단계 → 4단계)
            OpenAiService.GenerationResult result = openAiService.generateWithChaining(
                    title, author, description, progressCallback, userSettings);

            // 이미지를 로컬에 저장
            if (progressCallback != null) {
                progressCallback.accept("5:이미지를 저장하고 있습니다...");
            }
            String generatedImage = null;
            if (result.imageResult() != null) {
                generatedImage = imageStorageService.saveBase64Image(
                        java.util.Base64.getEncoder().encodeToString(result.imageResult().data()),
                        result.imageResult().mimeType());
            }

            Book book = Book.builder()
                    .isbn(request.getIsbn())
                    .title(title)
                    .author(author)
                    .publisher(request.getPublisher())
                    .originalImage(request.getOriginalImage())
                    .generatedImage(generatedImage)
                    .description(description)
                    .summary(result.summary())
                    .creator(getCurrentUser())
                    .build();

            Book savedBook = bookRepository.save(book);
            log.info("Book card created with ID: {}", savedBook.getId());

            return savedBook;

        } finally {
            if (isbnKey != null) {
                isbnInProgress.remove(isbnKey);
                log.debug("ISBN lock released: {}", isbnKey);
            }
        }
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("북카드를 찾을 수 없습니다: " + id));

        User currentUser = getCurrentUser();
        if (book.getCreator() != null && !book.getCreator().getId().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("본인이 생성한 북카드만 삭제할 수 있습니다");
        }

        if (book.getGeneratedImage() != null) {
            imageStorageService.delete(book.getGeneratedImage());
        }
        bookRepository.deleteById(id);
        log.info("Deleted book with ID: {}", id);
    }

    @Transactional(readOnly = true)
    public Optional<Book> findByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }

    @Transactional
    public LikeResponse toggleLike(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
        User currentUser = getCurrentUser();

        Optional<BookLike> existingLike = bookLikeRepository.findByBookAndUser(book, currentUser);
        boolean liked;

        if (existingLike.isPresent()) {
            // 이미 좋아요 → 취소
            bookLikeRepository.delete(existingLike.get());
            liked = false;
        } else {
            // 좋아요 추가
            BookLike bookLike = BookLike.builder()
                    .book(book)
                    .user(currentUser)
                    .build();
            bookLikeRepository.save(bookLike);
            liked = true;
        }

        // likeCount를 실제 카운트로 동기화
        long count = bookLikeRepository.countByBook(book);
        book.setLikeCount((int) count);
        bookRepository.save(book);

        return LikeResponse.builder()
                .bookId(book.getId())
                .likeCount(book.getLikeCount())
                .liked(liked)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isLikedByCurrentUser(Long bookId) {
        try {
            User currentUser = getCurrentUser();
            Book book = bookRepository.findById(bookId).orElse(null);
            if (book == null) return false;
            return bookLikeRepository.existsByBookAndUser(book, currentUser);
        } catch (Exception e) {
            return false;
        }
    }
}
