package com.cnu.docserver.docmanger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocTypeEditRequestDTO {

    @NotBlank
    private String title;

    private List<@NotBlank String> requiredFields;
    private List<String> exampleValues;

    private MultipartFile file; // 업로드 파일
}