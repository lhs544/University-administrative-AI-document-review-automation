package com.cnu.docserver.submission.dto;

import com.cnu.docserver.submission.enums.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Collections;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
// 제출 생성/수정/제출 API 공통 요약 응답
// 최초 제출, 수정(덮어쓰기), 최종 제출 직후 프론트에 간단 요약을 반환
@Schema(description = "제출 요약 응답")
public class SubmissionSummaryDTO {
    @Schema(description = "제출 ID", example = "12")
    private Integer submissionId;

    @Schema(description = "상태", example = "UNDER_REVIEW")
    private SubmissionStatus status;

    @Schema(description = "파일 URL")
    private String fileUrl;

    @Schema(description = "제출 시각(ISO8601)")
    private String submittedAt;

}