package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaverBookItem {
    private String title;
    private String author;
    private String publisher;
    private String image;
    private String isbn;
    private String description;
    private String link;
    private String pubdate;

    public String getCleanTitle() {
        return title != null ? title.replaceAll("<[^>]*>", "") : null;
    }

    public String getCleanAuthor() {
        return author != null ? author.replaceAll("<[^>]*>", "") : null;
    }
}
