package com.cnu.docserver.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder

@Schema(description = "최종/바로 제출 요청")
public class SubmitRequestDTO {

    @Schema(description = "제출 모드(DIRECT: 검증 건너뛰기, FINAL: 최종 확정 제출)",
            example = "FINAL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private SubmitMode mode;

    public enum SubmitMode { DIRECT, FINAL }

}
