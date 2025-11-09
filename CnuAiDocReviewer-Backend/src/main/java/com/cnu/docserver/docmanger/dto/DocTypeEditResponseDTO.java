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
public class DocTypeEditResponseDTO {

    private String title;
    private List<String> requiredFields;
    private List<String> exampleValues;
    private String fileUrl; // 조회용
}