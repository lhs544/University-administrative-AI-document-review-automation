// com/cnu/docserver/submission/controller/SubmissionController.java
package com.cnu.docserver.submission.controller;

import com.cnu.docserver.submission.dto.MySubmissionRowDTO;
import com.cnu.docserver.submission.dto.SubmissionSummaryDTO;
import com.cnu.docserver.submission.dto.SubmitRequestDTO;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFile;
import com.cnu.docserver.submission.entity.SubmissionHistory;
import com.cnu.docserver.submission.repository.SubmissionFileRepository;
import com.cnu.docserver.submission.repository.SubmissionHistoryRepository;
import com.cnu.docserver.submission.repository.SubmissionRepository;
import com.cnu.docserver.submission.service.SubmissionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/submissions")
@Tag(name = "Student Submission", description = "학생 제출/수정/제출 API")
public class SubmissionController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final ObjectMapper OM = new ObjectMapper();

    private final SubmissionService submissionService;
    private final SubmissionRepository submissionRepository;
    private final SubmissionHistoryRepository submissionHistoryRepository;
    private final SubmissionFileRepository submissionFileRepository;

    /* ---------------- 최초 제출 ---------------- */
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "최초 제출",
            description = "문서 유형 ID, 필드 JSON, 파일을 멀티파트로 제출합니다. 마감일 당일까지 허용."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제출 성공",
                    content = @Content(schema = @Schema(implementation = SubmissionSummaryDTO.class))),
            @ApiResponse(responseCode = "400", description = "유효성 오류(파일 누락/마감 초과 등)"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionSummaryDTO create(
            @Parameter(description = "문서 유형 ID", required = true, example = "101")
            @RequestParam Integer docTypeId,
            @Parameter(description = "필드 값 JSON (예: [{\"label\":\"학번\",\"value\":\"20231234\"}])")
            @RequestParam(required = false) String fieldsJson,
            @Parameter(description = "제출 파일", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        return submissionService.create(docTypeId, fieldsJson, file);
    }

    /* ---------------- 단건 조회(요약) ---------------- */
    @PreAuthorize("permitAll()")
    @GetMapping("/{submissionId}")
    public SubmissionSummaryDTO getOne(@PathVariable Integer submissionId) {
        return submissionService.getSummary(submissionId);
    }

    /* ---------------- 반려 후 수정(임시저장) ---------------- */
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "반려 후 수정(임시저장)",
            description = "파일/필드 일부 또는 전체를 덮어씁니다. 상태는 즉시 제출로 바뀌지 않습니다."
    )
    @PutMapping(value = "/{submissionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionSummaryDTO update(
            @Parameter(description = "제출 ID", example = "12") @PathVariable Integer submissionId,
            @Parameter(description = "필드 값 JSON") @RequestParam(required = false) String fieldsJson,
            @Parameter(description = "수정 파일") @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return submissionService.update(submissionId, fieldsJson, file);
    }

    /* ---------------- 제출(바로/최종) ---------------- */
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "제출(바로/최종)",
            description = "mode=FINAL이면 최종 확정 제출, mode=DIRECT면 AI 검증을 건너뛰고 관리자 검토로 바로 제출합니다. 마감일 당일까지 허용."
    )
    @PostMapping(value = "/{submissionId}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SubmissionSummaryDTO submit(
            @Parameter(description = "제출 ID", example = "12") @PathVariable Integer submissionId,
            @RequestBody SubmitRequestDTO body
    ) {
        return submissionService.submit(submissionId, body);
    }

    /* ---------------- 봇 검토 요약(텍스트 로그 + 상세 JSON) ---------------- */
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}/review-result")
    public Map<String, Object> getBotReviewResult(@PathVariable Integer id) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<SubmissionHistory> histories =
                submissionHistoryRepository.findBySubmissionOrderBySubmissionHistoryIdAsc(s);

        // 사람이 읽는 로그
        List<String> debugTexts = histories.stream()
                .map(h -> Optional.ofNullable(h.getMemo()).orElse(""))
                .toList();

        // 최신 OCR_DETAIL 파싱
        Map<String, Object> payload = extractLatestOcrDetail(histories);

        // <-- 여기서부터 Map.of() 금지! -->
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", s.getSubmissionId());
        out.put("status", s.getStatus());
        out.put("submittedAt", s.getSubmittedAt() == null ? null : s.getSubmittedAt().format(ISO));
        out.put("debugTexts", debugTexts);

        // 프론트에서 우선 사용하는 키들
        // findings는 항상 리스트로 보장
        Object findings = payload.get("findings");
        out.put("findings", (findings instanceof List) ? findings : List.of());

        // verdict/reason은 null 이어도 허용 (HashMap은 null ok)
        out.put("verdict", payload.get("verdict"));
        out.put("reason", payload.get("reason"));

        return out;
    }

    /** 히스토리에서 최신 OCR_DETAIL JSON 추출 */
    private static Map<String, Object> extractLatestOcrDetail(List<SubmissionHistory> histories) {
        for (int i = histories.size() - 1; i >= 0; i--) {
            String memo = Optional.ofNullable(histories.get(i).getMemo()).orElse("");
            if (!memo.startsWith("OCR_DETAIL ")) continue;

            String json = memo.substring("OCR_DETAIL ".length()).trim();
            try {
                Map<String, Object> map = OM.readValue(json, new TypeReference<Map<String,Object>>() {});
                // 정규화
                Object findings = map.get("findings");
                if (!(findings instanceof List<?>)) {
                    map.put("findings", List.of());
                }
                // verdict/reason는 없어도 그대로 둠(null 허용)
                return map;
            } catch (Exception ignore) {
                // 파싱 실패 시 다음 후보 계속
            }
        }
        return new HashMap<>(); // 기본값
    }


    /* ---------------- 내 제출 현황 ---------------- */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public List<MySubmissionRowDTO> listMySubmissions(
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "status", required = false) String statusCsv
    ) {
        int size = Math.max(1, Math.min(limit, 20));
        String studentId = submissionService.getCurrentStudentId();
        Pageable pageable = PageRequest.of(0, size);

        var statuses = parseStatuses(statusCsv);
        List<Submission> rows = statuses.isEmpty()
                ? submissionRepository.findByStudent_StudentIdOrderBySubmissionIdDesc(studentId, pageable)
                : submissionRepository.findByStudent_StudentIdAndStatusInOrderBySubmissionIdDesc(studentId, statuses, pageable);

        return rows.stream().map(s -> {
            String submittedAt = (s.getSubmittedAt() == null) ? null : s.getSubmittedAt().format(ISO);
            SubmissionFile latest = submissionFileRepository
                    .findTopBySubmissionOrderBySubmissionFileIdDesc(s).orElse(null);
            String filename = (latest != null && nonBlank(latest.getFileUrl()))
                    ? basenameFromUrl(latest.getFileUrl()) : "(파일 미존재)";
            return new MySubmissionRowDTO(s.getSubmissionId(), s.getStatus().name(), submittedAt, filename);
        }).toList();
    }

    private static List<com.cnu.docserver.submission.enums.SubmissionStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        String[] parts = csv.split(",");
        List<com.cnu.docserver.submission.enums.SubmissionStatus> out = new ArrayList<>();
        for (String p : parts) {
            String k = p.trim();
            if (k.isEmpty()) continue;
            try { out.add(com.cnu.docserver.submission.enums.SubmissionStatus.valueOf(k)); }
            catch (IllegalArgumentException ignore) { }
        }
        return out;
    }

    private static boolean nonBlank(String s) { return s != null && !s.isBlank(); }
    private static String basenameFromUrl(String url) {
        if (url == null) return null;
        try {
            String path = URI.create(url).getPath();
            if (path == null) path = url;
            String base = path.substring(path.lastIndexOf('/') + 1);
            return java.net.URLDecoder.decode(base, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            int idx = url.lastIndexOf('/');
            return (idx >= 0 ? url.substring(idx + 1) : url);
        }
    }

}
