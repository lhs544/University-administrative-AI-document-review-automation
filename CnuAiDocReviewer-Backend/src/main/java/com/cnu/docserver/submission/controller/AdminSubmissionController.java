package com.cnu.docserver.submission.controller;

import com.cnu.docserver.submission.dto.AdminDecisionRequestDTO;
import com.cnu.docserver.submission.dto.SubmissionDetailDTO;
import com.cnu.docserver.submission.dto.SubmissionSummaryDTO;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.enums.SubmissionStatus;
import com.cnu.docserver.submission.service.AdminSubmissionService;
import com.cnu.docserver.user.entity.Member;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin Review", description = "관리자 제출 검토 API")
@RestController
@RequestMapping("/api/admin/submissions")
@RequiredArgsConstructor
public class AdminSubmissionController {

    private final AdminSubmissionService adminSubmissionService;

    // 검토 대기 목록

    @GetMapping
    public List<SubmissionSummaryDTO> list(
            @RequestParam Integer departmentId,
            @RequestParam(required = false) List<SubmissionStatus> statuses // 예: ?statuses=SUBMITTED&statuses=APPROVED
    ) {
        return adminSubmissionService.listAdminQueue(departmentId, statuses);
    }
    // 상세 조회 (필요시)
    @GetMapping("/{id}")
    public SubmissionDetailDTO getOne(@PathVariable Integer id) {
        return adminSubmissionService.getDetail(id); // DTO로 매핑해서 리턴
    }

    // 승인
    @PostMapping("/{id}/approve")
    public SubmissionSummaryDTO approve(@PathVariable Integer id) {
        Member adminMember = (Member) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return adminSubmissionService.approve(id, adminMember); // memo 인자 제거
    }
    // 반려
    @PostMapping("/{id}/reject")
    public SubmissionSummaryDTO reject(@PathVariable Integer id, @RequestBody AdminDecisionRequestDTO body) {
        Member adminMember = (Member) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String reason = (body != null && body.getMemo()!=null) ? body.getMemo() : "사유 미기재";
        return adminSubmissionService.reject(id, adminMember, reason);
    }

    @GetMapping("/{id}/file")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')") // 필요 권한에 맞춰 조정
    public ResponseEntity<Resource> download(@PathVariable Integer id) {
        var file = adminSubmissionService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + org.springframework.web.util.UriUtils.encode(file.filename(), java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.data().length)
                .body(new ByteArrayResource(file.data()));
    }
}
