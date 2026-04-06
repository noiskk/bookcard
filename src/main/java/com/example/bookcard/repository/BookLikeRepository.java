package com.example.bookcard.repository;

import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.BookLike;
import com.example.bookcard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 좋아요 레포지토리
 *
 * (book_id, user_id) 복합 유니크 제약이 걸린 book_likes 테이블을 관리한다.
 * 좋아요 존재 여부 확인, 토글(삽입/삭제), 좋아요 수 집계에 사용한다.
 */
@Repository
public interface BookLikeRepository extends JpaRepository<BookLike, Long> {

    /**
     * 특정 사용자가 특정 북카드에 좋아요를 눌렀는지 확인
     * 좋아요 버튼의 현재 상태(활성/비활성)를 판별하는 데 사용한다.
     */
    boolean existsByBookAndUser(Book book, User user);

    /**
     * 특정 사용자의 특정 북카드 좋아요 레코드 조회
     * 좋아요 취소(삭제) 시 레코드를 찾기 위해 사용한다.
     */
    Optional<BookLike> findByBookAndUser(Book book, User user);

    /**
     * 특정 북카드의 전체 좋아요 수 집계
     * 좋아요 토글 후 Book.likeCount를 실제 레코드 수와 동기화하는 데 사용한다.
     */
    long countByBook(Book book);
}
