package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 북카드 생성 진행 상황 DTO
 *
 * SSE(Server-Sent Events)를 통해 프론트엔드로 실시간 진행 상황을 전달한다.
 * 총 5단계: 책 분석 → 한글 요약 → 이미지 프롬프트 → 이미지 생성 → 저장
 * status: "in_progress" | "completed" | "error"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationProgress {

    /** 현재 단계 번호 (1~5). 오류 시 0 */
    private int step;
    /** 전체 단계 수 (5) */
    private int totalSteps;
    /** 진행 상태: "in_progress" | "completed" | "error" */
    private String status;
    /** 프론트엔드에 표시할 진행 메시지 */
    private String message;
    /** 완료 시 생성된 Book 객체, 진행 중에는 null */
    private Object data;

    /**
     * 진행 중 상태 이벤트 생성 팩토리 메서드
     *
     * @param step    현재 단계 번호
     * @param message 사용자에게 보여줄 진행 메시지
     */
    public static GenerationProgress inProgress(int step, String message) {
        return GenerationProgress.builder()
                .step(step)
                .totalSteps(5)
                .status("in_progress")
                .message(message)
                .build();
    }

    /**
     * 완료 상태 이벤트 생성 팩토리 메서드
     *
     * @param data 생성 완료된 Book 객체 (프론트엔드로 전달)
     */
    public static GenerationProgress completed(Object data) {
        return GenerationProgress.builder()
                .step(5)
                .totalSteps(5)
                .status("completed")
                .message("북카드 생성이 완료되었습니다!")
                .data(data)
                .build();
    }

    /**
     * 오류 상태 이벤트 생성 팩토리 메서드
     *
     * @param message 오류 원인 메시지 (프론트엔드 알림에 표시)
     */
    public static GenerationProgress error(String message) {
        return GenerationProgress.builder()
                .step(0)
                .totalSteps(4)
                .status("error")
                .message(message)
                .build();
    }
}
