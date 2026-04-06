package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NaverSearchResponse {
    private String lastBuildDate;
    private int total;
    private int start;
    private int display;
    private List<NaverBookItem> items;
}
