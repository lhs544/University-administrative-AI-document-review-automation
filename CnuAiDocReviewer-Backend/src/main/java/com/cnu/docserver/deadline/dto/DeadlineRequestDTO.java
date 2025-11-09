package com.cnu.docserver.deadline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeadlineRequestDTO {

    @Schema(description = "문서 ID", example = "1")
    private Integer docTypeId;

    @Schema(description = "마감일", example = "2025-09-30")
    private LocalDate deadline;




}
