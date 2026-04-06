package com.example.bookcard.repository;

import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.BookLike;
import com.example.bookcard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookLikeRepository extends JpaRepository<BookLike, Long> {

    boolean existsByBookAndUser(Book book, User user);

    Optional<BookLike> findByBookAndUser(Book book, User user);

    long countByBook(Book book);
}
