package com.cnu.docserver.submission.dto;

import com.cnu.docserver.submission.enums.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "내 제출 목록 한 줄")
public record MySubmissionRowDTO(
        @Schema(description = "제출 ID") Integer submissionId,
        @Schema(description = "상태") String status,
        @Schema(description = "제출일(ISO)") String submittedAt,
        @Schema(description = "표시 제목(파일명)") String title
) {}