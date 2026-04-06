package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookGenerateRequest {
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String originalImage;
    private String description;
}
