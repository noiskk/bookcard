package com.example.bookcard.repository;

import com.example.bookcard.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByTitleContainingIgnoreCase(String title);

    List<Book> findByAuthorContainingIgnoreCase(String author);

    Optional<Book> findByIsbn(String isbn);

    @Query("SELECT b FROM Book b WHERE LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Book> searchByKeyword(@Param("keyword") String keyword);

    List<Book> findAllByOrderByCreatedAtDesc();

    Page<Book> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 동시 요청 안전한 좋아요 증가 (DB에서 원자적 처리)
    @Modifying
    @Query("UPDATE Book b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id")
    void incrementLikeCount(@Param("id") Long id);
}
