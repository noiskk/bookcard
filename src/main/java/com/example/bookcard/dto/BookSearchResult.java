package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSearchResult {
    private String title;
    private String author;
    private String publisher;
    private String image;
    private String isbn;
    private String description;
}
