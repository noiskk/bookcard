package com.example.bookcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 좋아요 토글 및 상태 조회 응답 DTO
 *
 * POST /{id}/like (토글) 및 GET /{id}/like (상태 조회) 양쪽에서 동일하게 사용한다.
 * 프론트엔드는 liked 값으로 UI 버튼 상태를, likeCount로 표시 숫자를 업데이트한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeResponse {
    /** 좋아요 대상 북카드 ID */
    private Long bookId;
    /** 현재 총 좋아요 수 (DB 실시간 카운트 기준) */
    private int likeCount;
    /** 현재 로그인한 사용자의 좋아요 여부 */
    private boolean liked;
}
