package com.example.bookcard.repository;

import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 북카드 레포지토리
 *
 * JPA를 통해 북카드 데이터를 조회·저장·삭제한다.
 * 페이지네이션, 키워드 검색, 사용자별 조회 등의 쿼리 메서드를 제공한다.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /** 제목에 특정 문자열이 포함된 북카드 조회 (대소문자 무시) */
    List<Book> findByTitleContainingIgnoreCase(String title);

    /** 저자명에 특정 문자열이 포함된 북카드 조회 (대소문자 무시) */
    List<Book> findByAuthorContainingIgnoreCase(String author);

    /** ISBN으로 북카드 조회. 중복 생성 방지 체크에 사용 */
    Optional<Book> findByIsbn(String isbn);

    /**
     * 제목 또는 저자명으로 통합 키워드 검색 (대소문자 무시)
     * 보관함 내부 검색 기능에 사용한다.
     */
    @Query("SELECT b FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Book> searchByKeyword(@Param("keyword") String keyword);

    /** 전체 북카드를 생성일 내림차순으로 조회 (페이지네이션 없는 전체 목록용) */
    List<Book> findAllByOrderByCreatedAtDesc();

    /** 전체 북카드를 생성일 내림차순으로 페이지 단위 조회 */
    Page<Book> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 특정 사용자가 생성한 북카드를 생성일 내림차순으로 페이지 단위 조회 */
    Page<Book> findByCreatorOrderByCreatedAtDesc(User creator, Pageable pageable);

    /**
     * 좋아요 수를 DB에서 원자적으로 1 증가
     * 동시 요청 환경에서 Lost Update 없이 안전하게 카운트를 올린다.
     * (현재는 bookLikeRepository.countByBook()으로 동기화하는 방식 사용)
     */
    @Modifying
    @Query("UPDATE Book b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id")
    void incrementLikeCount(@Param("id") Long id);
}
