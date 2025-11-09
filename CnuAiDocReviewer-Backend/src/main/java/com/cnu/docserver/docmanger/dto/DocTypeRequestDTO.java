package com.cnu.docserver.docmanger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocTypeRequestDTO {

    @Schema(description = "부서 ID", example = "1")
    @NotNull
    private Integer departmentId;

    @Schema(description = "서류 제목", example = "장학금 신청서")
    @NotBlank
    private String title;

    @Schema(description = "필수 항목 목록", example = "[\"이름\", \"학번\"]")
    private List<@NotBlank String> requiredFields;

    @Schema(description = "예시 값 목록", example = "[\"홍길동\", \"202312345\"]")
    private List< String> exampleValues;

    @Schema(description = "업로드 파일")
    private MultipartFile file; // 업로드 파일 (null 가능)
}
