package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 북카드 생성 진행 상황 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationProgress {

    private int step;           // 현재 단계 (1-4)
    private int totalSteps;     // 전체 단계 수 (4)
    private String status;      // 상태: "in_progress", "completed", "error"
    private String message;     // 사용자에게 보여줄 메시지
    private Object data;        // 추가 데이터 (완료 시 Book 객체)

    public static GenerationProgress inProgress(int step, String message) {
        return GenerationProgress.builder()
                .step(step)
                .totalSteps(5)
                .status("in_progress")
                .message(message)
                .build();
    }

    public static GenerationProgress completed(Object data) {
        return GenerationProgress.builder()
                .step(5)
                .totalSteps(5)
                .status("completed")
                .message("북카드 생성이 완료되었습니다!")
                .data(data)
                .build();
    }

    public static GenerationProgress error(String message) {
        return GenerationProgress.builder()
                .step(0)
                .totalSteps(4)
                .status("error")
                .message(message)
                .build();
    }
}
