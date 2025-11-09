package com.cnu.docserver.docmanger.controller;

import com.cnu.docserver.docmanger.dto.DocTypeEditRequestDTO;
import com.cnu.docserver.docmanger.dto.DocTypeEditResponseDTO;
import com.cnu.docserver.docmanger.dto.DocTypeRequestDTO;
import com.cnu.docserver.docmanger.dto.DocTypeResponseDTO;
import com.cnu.docserver.docmanger.service.DocTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/documents")
@Tag(name = "DocType", description = "서류 유형 및 필수 항목 등록 API")
public class DocTypeController {

    private final DocTypeService docTypeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "서류 유형 등록", description = "부서 ID, 제목, 필수 항목 및 파일을 등록합니다.")
    public String registerDocType(@ModelAttribute @Valid DocTypeRequestDTO request) {
        docTypeService.registerDocType(
                request.getDepartmentId(),
                request.getTitle(),
                request.getRequiredFields(),
                request.getExampleValues(),
                request.getFile()
        );
        return "등록 완료";
    }

    @GetMapping
    @Operation(summary = "부서별 문서 목록 조회", description = "부서 ID에 해당하는 모든 서류 제목 및 필수 항목을 반환합니다.")
    public List<DocTypeResponseDTO> getDocTypesByDepartmentId(
            @RequestParam("departmentId") Integer departmentId
    ) {
        return docTypeService.getDocTypesByDepartment(departmentId);
    }

    @GetMapping("/{docTypeId}")
    @Operation(summary = "문서 수정용 데이터 조회(단건 조회)", description = "문서 ID에 해당하는 제목, 파일 URL, 필수 항목, 예시 목록을 반환합니다.")
    public DocTypeEditResponseDTO getDocTypeForEdit(@PathVariable Integer docTypeId) {
        return docTypeService.getDocTypeForEdit(docTypeId);
    }

    @PutMapping(value = "/{docTypeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "문서 수정", description = "문서 제목, 파일, 필수 항목, 예시 항목을 수정합니다.")
    public String updateDocType(
            @PathVariable Integer docTypeId,
            @ModelAttribute @Valid DocTypeEditRequestDTO editRequestDTO
    ) {
        docTypeService.updateDocType(
                docTypeId,
                editRequestDTO.getTitle(),
                editRequestDTO.getRequiredFields(),
                editRequestDTO.getExampleValues(),
                editRequestDTO.getFile()
        );
        return "수정 완료";
    }


    @GetMapping("/{docTypeId}/file")
    @org.springframework.security.access.prepost.PreAuthorize("permitAll()")
    public ResponseEntity<Resource> downloadFile(@PathVariable Integer docTypeId) {
        var file = docTypeService.getOriginalFileByDocTypeId(docTypeId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] bytes = docTypeService.readBytes(file.getFileUrl());
        ByteArrayResource body = new ByteArrayResource(bytes);

        // ▼ fileUrl에서 안전하게 파일명 추출 (URL 디코딩 + 마지막 세그먼트만)
        String filename = extractFilenameFromUrl(file.getFileUrl());
        if (filename == null || filename.isBlank()) {
            filename = "document-" + docTypeId;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + UriUtils.encode(filename, StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM) // 필요시 확장자에 따라 바꿔도 OK
                .contentLength(bytes.length)
                .body(body);
    }

    // --- helper ---
    private String extractFilenameFromUrl(String fileUrl) {
        if (fileUrl == null) return null;
        try {
            // 예: "/uploads/3/%ED%95%9C%EA%B8%80%20양식.hwp"
            String decoded = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            // 마지막 세그먼트만 추출
            return Paths.get(decoded).getFileName().toString();
        } catch (Exception e) {
            return null;
        }
    }
}

