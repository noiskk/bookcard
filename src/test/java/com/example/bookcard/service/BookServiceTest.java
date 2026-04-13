package com.example.bookcard.service;

import com.example.bookcard.dto.BookGenerateRequest;
import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.BookRepository;
import com.example.bookcard.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.bookcard.dto.LikeResponse;
import com.example.bookcard.entity.BookLike;
import com.example.bookcard.repository.BookLikeRepository;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NaverSearchService naverSearchService;

    @Mock
    private OpenAiService openAiService;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private BookLikeRepository bookLikeRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BookService bookService;

    // ========================================
    // 공통 헬퍼: SecurityContextHolder 목킹
    // ========================================

    private void mockSecurityContext(String email) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getName()).willReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    // ========================================
    // getAllBooks() 테스트
    // ========================================

    @Test
    @DisplayName("getAllBooks(): 전체 북카드 목록을 최신순으로 반환한다")
    void getAllBooks_returnsAllBooks() {
        // given
        Book book1 = Book.builder().title("책 A").author("저자 A").build();
        Book book2 = Book.builder().title("책 B").author("저자 B").build();
        given(bookRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(book1, book2));

        // when
        List<Book> books = bookService.getAllBooks();

        // then
        assertThat(books).hasSize(2);
        assertThat(books.get(0).getTitle()).isEqualTo("책 A");
        assertThat(books.get(1).getTitle()).isEqualTo("책 B");
        verify(bookRepository).findAllByOrderByCreatedAtDesc();
    }

    // ========================================
    // generateBook() 테스트
    // ========================================

    @Test
    @DisplayName("generateBook(): ISBN이 중복되면 IllegalArgumentException이 발생한다")
    void generateBook_duplicateIsbn_throwsException() {
        // given
        BookGenerateRequest request = BookGenerateRequest.builder()
                .isbn("978-89-1234-567-8")
                .title("중복 책")
                .author("저자")
                .description("설명")
                .build();

        Book existingBook = Book.builder()
                .id(1L)
                .isbn("978-89-1234-567-8")
                .title("기존 책")
                .author("저자")
                .build();

        given(bookRepository.findByIsbn("978-89-1234-567-8")).willReturn(Optional.of(existingBook));

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bookService.generateBook(request)
        );
        assertThat(exception.getMessage()).contains("이미 생성된 북카드가 있습니다");
    }

    @Test
    @DisplayName("generateBook(): ISBN이 없으면 중복 체크를 건너뛰고 북카드를 생성한다")
    void generateBook_withoutIsbn_createsBook() {
        // given
        String email = "user@example.com";
        mockSecurityContext(email);

        BookGenerateRequest request = BookGenerateRequest.builder()
                .isbn(null)
                .title("새 책")
                .author("저자")
                .description("설명")
                .build();

        User currentUser = User.builder()
                .id(1L)
                .email(email)
                .nickname("테스터")
                .password("encoded")
                .build();

        OpenAiService.GenerationResult generationResult = new OpenAiService.GenerationResult(
                null,
                List.of("요약 문장 1", "요약 문장 2"),
                null
        );

        given(openAiService.generateWithChaining(anyString(), anyString(), any(), any(), any()))
                .willReturn(generationResult);
        given(userRepository.findByEmail(email)).willReturn(Optional.of(currentUser));

        Book savedBook = Book.builder()
                .id(10L)
                .title("새 책")
                .author("저자")
                .creator(currentUser)
                .build();
        given(bookRepository.save(any(Book.class))).willReturn(savedBook);

        // TransactionTemplate.execute()가 콜백을 그대로 실행하도록 stub
        given(transactionTemplate.execute(any()))
                .willAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        // when
        Book result = bookService.generateBook(request);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("새 책");
        verify(bookRepository, never()).findByIsbn(anyString());
    }

    // ========================================
    // deleteBook() 테스트
    // ========================================

    @Test
    @DisplayName("deleteBook(): 북카드 생성자 본인은 삭제에 성공한다")
    void deleteBook_byOwner_success() {
        // given
        String email = "owner@example.com";
        mockSecurityContext(email);

        User owner = User.builder()
                .id(1L)
                .email(email)
                .nickname("오너")
                .password("encoded")
                .build();

        Book book = Book.builder()
                .id(100L)
                .title("내 책")
                .author("저자")
                .creator(owner)
                .generatedImage(null)
                .build();

        given(bookRepository.findById(100L)).willReturn(Optional.of(book));
        given(userRepository.findByEmail(email)).willReturn(Optional.of(owner));

        // when
        bookService.deleteBook(100L);

        // then
        verify(bookRepository).deleteById(100L);
    }

    @Test
    @DisplayName("deleteBook(): 다른 사람의 북카드를 삭제하려 하면 AccessDeniedException이 발생한다")
    void deleteBook_byNonOwner_throwsAccessDeniedException() {
        // given
        String ownerEmail = "owner@example.com";
        String otherEmail = "other@example.com";
        mockSecurityContext(otherEmail);

        User owner = User.builder()
                .id(1L)
                .email(ownerEmail)
                .nickname("오너")
                .password("encoded")
                .build();

        User other = User.builder()
                .id(2L)
                .email(otherEmail)
                .nickname("타인")
                .password("encoded")
                .build();

        Book book = Book.builder()
                .id(100L)
                .title("오너의 책")
                .author("저자")
                .creator(owner)
                .build();

        given(bookRepository.findById(100L)).willReturn(Optional.of(book));
        given(userRepository.findByEmail(otherEmail)).willReturn(Optional.of(other));

        // when & then
        assertThrows(
                AccessDeniedException.class,
                () -> bookService.deleteBook(100L)
        );
        verify(bookRepository, never()).deleteById(any());
    }

    // ========================================
    // likeBook() 테스트
    // ========================================

    @Test
    @DisplayName("toggleLike(): 좋아요가 없는 상태에서 토글하면 좋아요가 추가된다")
    void toggleLike_addsLike() {
        // given
        mockSecurityContext("user@test.com");
        User user = User.builder().id(1L).email("user@test.com").build();
        Book book = Book.builder().id(1L).title("인기 책").author("저자").likeCount(5).build();

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(bookLikeRepository.findByBookAndUser(book, user)).willReturn(Optional.empty());
        given(bookLikeRepository.countByBook(book)).willReturn(6L);

        // when
        LikeResponse result = bookService.toggleLike(1L);

        // then
        verify(bookLikeRepository).save(any(BookLike.class));
        verify(bookRepository).syncLikeCount(1L);
        assertThat(result.isLiked()).isTrue();
        assertThat(result.getLikeCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("toggleLike(): 이미 좋아요한 상태에서 토글하면 좋아요가 취소된다")
    void toggleLike_removesLike() {
        // given
        mockSecurityContext("user@test.com");
        User user = User.builder().id(1L).email("user@test.com").build();
        Book book = Book.builder().id(1L).title("인기 책").author("저자").likeCount(5).build();
        BookLike existingLike = BookLike.builder().id(1L).book(book).user(user).build();

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));
        given(bookLikeRepository.findByBookAndUser(book, user)).willReturn(Optional.of(existingLike));
        given(bookLikeRepository.countByBook(book)).willReturn(4L);

        // when
        LikeResponse result = bookService.toggleLike(1L);

        // then
        verify(bookLikeRepository).delete(existingLike);
        verify(bookRepository).syncLikeCount(1L);
        assertThat(result.isLiked()).isFalse();
        assertThat(result.getLikeCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("toggleLike(): 존재하지 않는 북카드에 좋아요 시 RuntimeException이 발생한다")
    void toggleLike_bookNotFound_throwsException() {
        // given
        given(bookRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThrows(
                RuntimeException.class,
                () -> bookService.toggleLike(999L)
        );
    }
}
