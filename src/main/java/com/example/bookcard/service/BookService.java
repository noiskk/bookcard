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
import org.springframework.transaction.support.TransactionTemplate;

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

/**
 * 북카드 핵심 비즈니스 로직 서비스
 *
 * 주요 기능:
 * - 북카드 생성: OpenAI 4단계 프롬프트 체이닝 → Gemini 이미지 생성 → DB 저장
 * - 도서 검색: 네이버 API 검색 (Caffeine 캐시 적용)
 * - ISBN 동시 생성 방지: ConcurrentHashMap putIfAbsent 락 패턴
 * - 메인 페이지 추천: 카테고리별 도서 목록 (인메모리 1시간 캐시)
 * - 좋아요 토글 및 보관함 관리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {

    /** 메인 페이지 추천 도서 인메모리 캐시 TTL (1시간) */
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;

    /**
     * 동시에 동일 ISBN으로 생성 요청이 들어올 경우 AI 작업(~21초)이 중복 실행되는 것을 방지한다.
     * putIfAbsent: 키가 없으면 삽입하고 null 반환(락 획득), 이미 있으면 기존 값 반환(락 실패).
     */
    private final ConcurrentHashMap<String, Object> isbnInProgress = new ConcurrentHashMap<>();
    private static final Object LOCK_SENTINEL = new Object();

    /** 카테고리별 추천 도서 요청 시 네이버 API 검색에 사용할 쿼리 수 */
    private static final int RECOMMENDATIONS_PER_CATEGORY = 6;

    /** 메인 페이지에 표시할 추천 카테고리와 검색 쿼리 매핑 (순서 보존을 위해 LinkedHashMap 사용) */
    private static final Map<String, String[]> CATEGORY_QUERIES = new LinkedHashMap<>();

    static {
        CATEGORY_QUERIES.put("소설",        new String[]{"소설", "한국소설 추천"});
        CATEGORY_QUERIES.put("에세이",      new String[]{"에세이", "에세이 추천"});
        CATEGORY_QUERIES.put("자기계발",    new String[]{"자기계발", "자기계발 베스트"});
        CATEGORY_QUERIES.put("인문학",      new String[]{"인문학", "인문학 교양"});
    }

    /** 추천 도서 인메모리 캐시: CACHE_KEY → [결과 리스트, 캐시된 시각(epoch ms)] */
    private final ConcurrentHashMap<String, Object[]> recommendationCache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "recommendations";

    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;
    private final UserRepository userRepository;
    private final NaverSearchService naverSearchService;
    private final OpenAiService openAiService;
    private final ImageStorageService imageStorageService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Spring Security 컨텍스트에서 현재 인증된 사용자 엔티티를 조회한다.
     * JWT 필터가 SecurityContext에 이메일을 저장하므로 DB에서 그대로 조회 가능하다.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("인증된 사용자를 찾을 수 없습니다"));
    }

    /** 전체 북카드를 최신순으로 반환한다 (페이지네이션 없음, 하위 호환용). */
    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        return bookRepository.findAllByOrderByCreatedAtDesc();
    }

    /** ID로 특정 북카드를 조회한다. 존재하지 않으면 Optional.empty() 반환 */
    @Transactional(readOnly = true)
    public Optional<Book> getBookById(Long id) {
        return bookRepository.findById(id);
    }

    /** 전체 북카드를 최신순으로 페이지네이션하여 반환한다. */
    @Transactional(readOnly = true)
    public Page<Book> getPagedBooks(Pageable pageable) {
        return bookRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** 현재 로그인한 사용자가 생성한 북카드만 최신순으로 페이지네이션하여 반환한다. */
    @Transactional(readOnly = true)
    public Page<Book> getMyBooks(Pageable pageable) {
        User user = getCurrentUser();
        return bookRepository.findByCreatorOrderByCreatedAtDesc(user, pageable);
    }

    /**
     * 보관함 내 키워드 검색. 제목·저자·출판사를 대상으로 LIKE 검색한다.
     * 키워드가 없으면 전체 목록을 반환한다.
     */
    @Transactional(readOnly = true)
    public List<Book> searchBooksInLibrary(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllBooks();
        }
        return bookRepository.searchByKeyword(keyword.trim());
    }

    /** 네이버 API로 책을 검색한다 (기본 시작 위치 1). */
    public List<BookSearchResult> searchBooksFromNaver(String query) {
        return searchBooksFromNaver(query, 1);
    }

    /**
     * 네이버 API로 책을 검색한다. 결과는 Caffeine 캐시(30분)에 저장된다.
     *
     * @param query 검색어
     * @param start 시작 위치 (1부터, 페이지 로드 시 증가)
     */
    public List<BookSearchResult> searchBooksFromNaver(String query, int start) {
        return naverSearchService.searchBooks(query, 20, start);
    }

    /**
     * 메인 페이지 카테고리별 추천 도서를 반환한다.
     * 최초 호출 시 네이버 API를 호출하고, 이후 1시간 동안은 캐시에서 반환한다.
     */
    @SuppressWarnings("unchecked")
    public List<RecommendationCategory> getRecommendations() {
        // 캐시 히트 확인
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

        // 결과를 캐시에 저장
        recommendationCache.put(CACHE_KEY, new Object[]{result, Instant.now().toEpochMilli()});

        return result;
    }

    /** 진행 콜백 없이 북카드를 생성한다 (동기 방식). */
    public Book generateBook(BookGenerateRequest request) {
        return generateBook(request, null);
    }

    /**
     * AI 프롬프트 체이닝으로 북카드를 생성하고 DB에 저장한다.
     *
     * 트랜잭션 전략:
     * - AI 작업(~21초)은 트랜잭션 없이 실행하여 DB 커넥션을 점유하지 않는다.
     * - DB 저장만 TransactionTemplate으로 짧은 트랜잭션을 사용한다.
     * - 이렇게 하면 AI 작업 중에도 커넥션 풀에 영향을 주지 않는다.
     *
     * 처리 순서:
     * 1. ISBN 중복 체크 (DB, Spring Data JPA 자체 트랜잭션)
     * 2. 동시 생성 방지 락 획득 (ConcurrentHashMap)
     * 3. OpenAI 4단계 체이닝 실행 (트랜잭션 없이)
     * 4. Gemini 생성 이미지를 로컬 파일로 저장 (트랜잭션 없이)
     * 5. Book 엔티티 빌드 후 DB 저장 (TransactionTemplate으로 짧은 트랜잭션)
     * 6. finally 블록에서 ISBN 락 해제
     *
     * @param request          북카드 생성에 필요한 책 정보 + 사용자 설정
     * @param progressCallback SSE 진행 상황 콜백 (null이면 무시)
     */
    public Book generateBook(BookGenerateRequest request, Consumer<String> progressCallback) {
        log.info("Generating book card for: {} by {}", request.getTitle(), request.getAuthor());

        final String isbnKey = (request.getIsbn() != null && !request.getIsbn().isBlank())
                ? request.getIsbn() : null;

        // ISBN 중복 체크 (Spring Data JPA가 자체 짧은 트랜잭션으로 처리)
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

            // 사용자 설정값 추출 (요약 스타일·길이·커스텀 프롬프트)
            OpenAiService.UserSettings userSettings = new OpenAiService.UserSettings(
                    request.getSummaryStyle(),
                    request.getSummaryLength(),
                    request.getDefaultPrompt()
            );

            // 4단계 프롬프트 체이닝 실행 (트랜잭션 없이, DB 커넥션 점유하지 않음)
            OpenAiService.GenerationResult result = openAiService.generateWithChaining(
                    title, author, description, progressCallback, userSettings);

            // Gemini 생성 이미지를 서버 로컬에 저장 (트랜잭션 없이)
            if (progressCallback != null) {
                progressCallback.accept("5:이미지를 저장하고 있습니다...");
            }
            String generatedImage = null;
            if (result.imageResult() != null) {
                generatedImage = imageStorageService.saveBase64Image(
                        java.util.Base64.getEncoder().encodeToString(result.imageResult().data()),
                        result.imageResult().mimeType());
            }

            // DB 저장만 짧은 트랜잭션으로 실행 (커넥션 점유 ~수십ms)
            final String finalGeneratedImage = generatedImage;
            Book savedBook = transactionTemplate.execute(status -> {
                Book book = Book.builder()
                        .isbn(request.getIsbn())
                        .title(title)
                        .author(author)
                        .publisher(request.getPublisher())
                        .originalImage(request.getOriginalImage())
                        .generatedImage(finalGeneratedImage)
                        .description(description)
                        .summary(result.summary())
                        .creator(getCurrentUser())
                        .build();
                return bookRepository.save(book);
            });

            log.info("Book card created with ID: {}", savedBook.getId());
            return savedBook;

        } finally {
            // 성공·실패 여부와 관계없이 락을 반드시 해제
            if (isbnKey != null) {
                isbnInProgress.remove(isbnKey);
                log.debug("ISBN lock released: {}", isbnKey);
            }
        }
    }

    /**
     * 북카드를 삭제한다.
     * 본인이 생성한 북카드만 삭제할 수 있으며, 연결된 이미지 파일도 함께 삭제한다.
     *
     * 삭제 순서: DB 먼저 삭제 → 파일 삭제.
     * DB 삭제가 실패하면 트랜잭션이 롤백되어 파일도 보존된다.
     * 파일 삭제가 실패해도 고아 파일일 뿐, DB 정합성은 유지된다.
     *
     * @param id 삭제할 북카드 ID
     * @throws IllegalArgumentException  북카드를 찾을 수 없는 경우
     * @throws AccessDeniedException     다른 사용자의 북카드를 삭제하려는 경우
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("북카드를 찾을 수 없습니다: " + id));

        User currentUser = getCurrentUser();
        if (book.getCreator() != null && !book.getCreator().getId().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("본인이 생성한 북카드만 삭제할 수 있습니다");
        }

        String imagePath = book.getGeneratedImage();

        // DB 먼저 삭제 (실패 시 트랜잭션 롤백, 파일은 안전하게 보존)
        bookRepository.deleteById(id);
        log.info("Deleted book with ID: {}", id);

        // DB 삭제 성공 후 파일 삭제 (실패해도 고아 파일일 뿐, 데이터 정합성 유지)
        if (imagePath != null) {
            imageStorageService.delete(imagePath);
        }
    }

    /** ISBN으로 기존 북카드를 조회한다. SSE 오류 처리 시 중복 감지에도 활용된다. */
    @Transactional(readOnly = true)
    public Optional<Book> findByIsbn(String isbn) {
        return bookRepository.findByIsbn(isbn);
    }

    /**
     * 좋아요를 토글한다 (이미 좋아요 → 취소, 미좋아요 → 추가).
     * 토글 후 원자적 UPDATE 쿼리로 likeCount를 동기화하여 동시 요청 시 Lost Update를 방지한다.
     *
     * @param id 대상 북카드 ID
     * @return 토글 후의 좋아요 상태 및 카운트
     */
    @Transactional(rollbackFor = Exception.class)
    public LikeResponse toggleLike(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found: " + id));
        User currentUser = getCurrentUser();

        Optional<BookLike> existingLike = bookLikeRepository.findByBookAndUser(book, currentUser);
        boolean liked;

        if (existingLike.isPresent()) {
            // 이미 좋아요 상태 → 취소
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

        // 원자적 UPDATE로 likeCount 동기화 (단일 SQL문으로 Race Condition 방지)
        bookRepository.syncLikeCount(id);

        // 동기화된 카운트를 조회하여 응답에 포함
        long count = bookLikeRepository.countByBook(book);

        return LikeResponse.builder()
                .bookId(book.getId())
                .likeCount((int) count)
                .liked(liked)
                .build();
    }

    /**
     * 현재 로그인한 사용자가 특정 북카드를 좋아요했는지 확인한다.
     * 비로그인 상태거나 북카드를 찾을 수 없으면 false를 반환한다.
     *
     * @param bookId 확인할 북카드 ID
     */
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
