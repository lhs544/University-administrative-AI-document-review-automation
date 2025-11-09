package com.cnu.docserver.submission.service;

import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.department.repository.DepartmentRepository;
import com.cnu.docserver.docmanger.service.FileStorageService;
import com.cnu.docserver.submission.dto.HistoryDTO;
import com.cnu.docserver.submission.dto.SubmissionDetailDTO;
import com.cnu.docserver.submission.dto.SubmissionSummaryDTO;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFile;
import com.cnu.docserver.submission.entity.SubmissionHistory;
import com.cnu.docserver.submission.enums.HistoryAction;
import com.cnu.docserver.submission.enums.SubmissionStatus;
import com.cnu.docserver.submission.repository.SubmissionFileRepository;
import com.cnu.docserver.submission.repository.SubmissionHistoryRepository;
import com.cnu.docserver.submission.repository.SubmissionRepository;
import com.cnu.docserver.user.entity.Admin;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.repository.AdminRepository;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionHistoryRepository submissionHistoryRepository;
    private final AdminRepository adminRepository;
    private final SubmissionFileRepository submissionFileRepository; // ★ 추가
    private final DepartmentRepository departmentRepository;
    private final FileStorageService fileStorageService;
    private static final EnumSet<SubmissionStatus> REVIEWABLE_STATUSES =
            EnumSet.of(
                    SubmissionStatus.SUBMITTED,
                    SubmissionStatus.UNDER_REVIEW,
                    SubmissionStatus.BOT_REVIEW,
                    SubmissionStatus.NEEDS_FIX
            );
    @Transactional(readOnly = true)
    public SubmissionDetailDTO getDetail(Integer id) {

        Submission s = submissionRepository.findDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."));

        // 파일 URL / 파일명
        String fileUrl = submissionFileRepository.findBySubmission(s)
                .map(SubmissionFile::getFileUrl)
                .orElse(null);
        String fileName = extractFileName(fileUrl); // ← 제목으로 쓸 파일명

        // 문서 유형명
        String docTypeName = (s.getDocType() != null ? s.getDocType().getTitle() : null);

        // 히스토리
        List<HistoryDTO> history = submissionHistoryRepository.findBySubmissionOrderByChangedAtAsc(s)
                .stream()
                .map(h -> new HistoryDTO(
                        h.getSubmissionHistoryId(),
                        h.getAction() == null ? null : h.getAction().name(),
                        h.getMemo(),
                        resolveAdminName(h.getAdmin()),
                        h.getChangedAt() == null ? null : h.getChangedAt().toString()
                ))
                .toList();

        // 학생 정보
        String studentName = (s.getStudent() != null && s.getStudent().getMember() != null)
                ? s.getStudent().getMember().getName() : null;


        String memberId = (s.getStudent() != null && s.getStudent().getMember() != null)
                ? s.getStudent().getMember().getMemberId() : null;

        return new SubmissionDetailDTO(
                s.getSubmissionId(),
                s.getStatus() == null ? null : s.getStatus().name(),
                s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString(),
                memberId,
                studentName,
                fileUrl,
                docTypeName,
                fileName,
                history
        );
    }


    private String resolveAdminName(Admin admin) {
        if (admin == null) return "학생/시스템";
        // Admin -> Member -> 이름(또는 memberId) 경로에 맞게 꺼내세요.
        if (admin.getMember() != null && admin.getMember().getName() != null) {
            return admin.getMember().getName();
        }
        return admin.getAdminId() != null ? admin.getAdminId().toString() : "관리자";
    }

    private String extractFileName(String url) {
        if (url == null || url.isBlank()) return null;

        // ?query 제거
        String base = url.split("\\?")[0];

        // 마지막 경로 토큰
        int idx = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        String raw = (idx >= 0 && idx < base.length() - 1) ? base.substring(idx + 1) : base;

        // 퍼센트 인코딩 디코딩
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw; // 잘못 인코딩된 경우 원본 유지
        }
    }
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDTO> listAdminQueue(Integer departmentId, @Nullable List<SubmissionStatus> statuses) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."));

        List<Submission> list;
        if (statuses == null || statuses.isEmpty()) {
            // ★ 전체 상태
            list = submissionRepository.findByDocType_DepartmentOrderBySubmittedAtDesc(dept);
        } else {
            list = submissionRepository.findByDocType_DepartmentAndStatusInOrderBySubmittedAtDesc(dept, statuses);
        }

        return list.stream().map(this::toSummary).toList();
    }


    @Transactional
    public SubmissionSummaryDTO approve(Integer submissionId, Member adminMember) {
        Submission s = requireReviewable(submissionId);
        Admin admin = adminRepository.findByMember(adminMember)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 없습니다."));


        s.setStatus(SubmissionStatus.APPROVED);
        submissionRepository.save(s);

        saveHistory(s, admin, HistoryAction.APPROVED, "승인 처리되었습니다.");

        return toSummary(s);
    }

    @Transactional
    public SubmissionSummaryDTO reject(Integer submissionId, Member adminMember, String reason) {
        Submission s = requireReviewable(submissionId);
        Admin admin = adminRepository.findByMember(adminMember)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 없습니다."));

        s.setStatus(SubmissionStatus.REJECTED);
        submissionRepository.save(s);
        saveHistory(s, admin, HistoryAction.REJECTED, "반려 사유: " + (reason == null ? "사유 미기재" : reason));

        return toSummary(s);
    }

    // --- helpers ---

    private Submission requireReviewable(Integer id) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."));

        if (!REVIEWABLE_STATUSES.contains(s.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "검토 가능한 상태가 아닙니다.");
        }

        // 관리자가 개입하면 '검토 중'으로 진입 표시 (기존 컨벤션 유지)
        if (s.getStatus() == SubmissionStatus.SUBMITTED
                || s.getStatus() == SubmissionStatus.BOT_REVIEW
                || s.getStatus() == SubmissionStatus.NEEDS_FIX) {
            s.setStatus(SubmissionStatus.UNDER_REVIEW);
            // 같은 트랜잭션 내 flush 시 반영되므로 save 생략해도 무방
            // submissionRepository.save(s);
        }

        return s;
    }

    private void saveHistory(Submission s, Admin admin, HistoryAction action, String memo) {
        submissionHistoryRepository.save(
                SubmissionHistory.builder()
                        .submission(s)
                        .admin(admin)          // ★ admin_id NOT NULL 보장
                        .action(action)
                        .memo(memo)
                        .build()
        );
    }

    private SubmissionSummaryDTO toSummary(Submission s) {
        String fileUrl = submissionFileRepository.findBySubmission(s)
                .map(SubmissionFile::getFileUrl).orElse(null);
        return SubmissionSummaryDTO.builder()
                .submissionId(s.getSubmissionId())
                .status(s.getStatus())
                .fileUrl(fileUrl)
                .submittedAt(s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString())
                .build();
    }

    public record FileDownload(String filename, byte[] data) {}

    @Transactional(readOnly = true)
    public FileDownload downloadFile(Integer submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."));

        String fileUrl = submissionFileRepository.findBySubmission(s)
                .map(SubmissionFile::getFileUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출 파일이 없습니다."));

        byte[] bytes = fileStorageService.readBytes(fileUrl);

        // URL에서 안전하게 파일명 추출 (디코딩 + 마지막 세그먼트)
        String decoded = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
        String filename = Paths.get(decoded).getFileName().toString();
        if (filename == null || filename.isBlank()) {
            filename = "submission-" + submissionId;
        }
        return new FileDownload(filename, bytes);
    }
}
