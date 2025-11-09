package com.cnu.docserver.submission.service;

import com.cnu.docserver.deadline.repository.DeadlineRepository;
import com.cnu.docserver.docmanger.entity.DocType;
import com.cnu.docserver.docmanger.entity.RequiredField;
import com.cnu.docserver.docmanger.repository.DocTypeRepository;
import com.cnu.docserver.docmanger.repository.RequiredFieldRepository;
import com.cnu.docserver.docmanger.service.FileStorageService;
import com.cnu.docserver.ocr.SubmissionReviewOrchestrator;
import com.cnu.docserver.submission.dto.FieldValueInputDTO;
import com.cnu.docserver.submission.dto.SubmissionSummaryDTO;
import com.cnu.docserver.submission.dto.SubmitRequestDTO;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFieldValue;
import com.cnu.docserver.submission.entity.SubmissionFile;
import com.cnu.docserver.submission.entity.SubmissionHistory;
import com.cnu.docserver.submission.enums.HistoryAction;
import com.cnu.docserver.submission.enums.SubmissionStatus;
import com.cnu.docserver.submission.event.SubmissionCreatedEvent;
import com.cnu.docserver.submission.repository.SubmissionFieldValueRepository;
import com.cnu.docserver.submission.repository.SubmissionFileRepository;
import com.cnu.docserver.submission.repository.SubmissionHistoryRepository;
import com.cnu.docserver.submission.repository.SubmissionRepository;
import com.cnu.docserver.user.entity.Admin;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.entity.Student;
import com.cnu.docserver.user.repository.StudentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final StudentRepository studentRepository;
    private final DocTypeRepository docTypeRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final DeadlineRepository deadlineRepository;

    private final FileStorageService fileStorageService;
    private final RequiredFieldRepository requiredFieldRepository;

    private final SubmissionFileRepository submissionFileRepository;
    private final SubmissionFieldValueRepository submissionFieldValueRepository;
    private final SubmissionHistoryRepository submissionHistoryRepository;

    // SubmissionReviewOrchestrator를 직접 호출하지 않으므로 주석 처리하거나 제거 가능
    private final SubmissionReviewOrchestrator submissionReviewOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // === 1) 최초 제출 ===
    @Transactional
    public SubmissionSummaryDTO create(Integer docTypeId, String fieldsJson, MultipartFile file) {
        // 1) 로그인 학생 조회
        String studentId = currentStudentId();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "학생 정보를 찾을 수 없습니다."));

        // 2) 문서 유형
        DocType docType = docTypeRepository.findById(docTypeId)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 유형을 찾을 수 없습니다."));

        // 3) 마감일 1차 체크
        ensureNotPastDeadline(docType);

        // 4) 파일 필수
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일은 필수입니다.");
        }

        // 5) 제출 생성(DRAFT)
        Submission submission = Submission.builder()
                .student(student)
                .docType(docType)
                .status(SubmissionStatus.DRAFT)
                .build();
        submissionRepository.save(submission);

        // 6) 파일/필드 upsert
        upsertFile(submission, file);
        upsertFieldValues(submission, parseFields(fieldsJson), docType);

        // 7) 제출 전이 (학생 제출) -> SUBMITTED
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(submission);


        // 8) 이력 기록: SUBMITTED (학생 제출)
        writeHistory(submission, null, HistoryAction.SUBMITTED, "학생 제출");

        // 9) 서버가 곧바로 UNDER_REVIEW로 전환
        submission.setStatus(SubmissionStatus.BOT_REVIEW);
        submissionRepository.save(submission);

        // 10) OCR 프로세스를 비동기 실행하는 대신, 이벤트를 발행합니다.
        // 이 이벤트는 현재 트랜잭션이 성공적으로 커밋된 후에만 리스너를 트리거합니다.
//        submissionReviewOrchestrator.runBotReview(submission.getSubmissionId());
        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, submission.getSubmissionId()));

        return toSummary(submission);
    }

    public String getCurrentStudentId() {
        return currentStudentId();
    }
    // === 2) 반려 후 수정(덮어쓰기) ===
    @Transactional
    public SubmissionSummaryDTO update(Integer submissionId, String fieldsJson, MultipartFile file) {
        Submission s = requireSubmission(submissionId);
        mustBeOneOf(s, SubmissionStatus.DRAFT, SubmissionStatus.REJECTED);

        boolean changed = false;

        if (file != null && !file.isEmpty()) {
            upsertFile(s, file);
            changed = true;
        }
        if (fieldsJson != null && !fieldsJson.isBlank()) {
            upsertFieldValues(s, parseFields(fieldsJson), s.getDocType());
            changed = true;
        }

        if (changed) {
            writeHistory(s, null, HistoryAction.MODIFIED, "학생 수정(임시 저장)");
        }
        return toSummary(s);
    }


    // === 3) 최종 제출 ===
    @Transactional
    public SubmissionSummaryDTO submit(Integer submissionId, SubmitRequestDTO body) {
        Submission s = requireSubmission(submissionId);
        // 재제출은 REJECTED/NEEDS_FIX에서도 가능하도록 하는 게 자연스러워요.
        mustBeOneOf(s, SubmissionStatus.DRAFT, SubmissionStatus.REJECTED, SubmissionStatus.NEEDS_FIX);

        if (body == null || body.getMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출 모드가 필요합니다.");
        }

        // 마감 체크
        ensureNotPastDeadline(s.getDocType());

        SubmissionStatus prev = s.getStatus();

        // 1) 학생 제출 접수(시각 기록)
        s.setStatus(SubmissionStatus.SUBMITTED);
        s.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(s);

        // 2) 히스토리 남기기 (학생 제출/재제출 구분)
        String memo = switch (body.getMode()) {
            case FINAL  -> (prev == SubmissionStatus.REJECTED || prev == SubmissionStatus.NEEDS_FIX)
                    ? "학생 재제출(FINAL)" : "학생 최종제출(FINAL)";
            case DIRECT -> (prev == SubmissionStatus.REJECTED || prev == SubmissionStatus.NEEDS_FIX)
                    ? "학생 재제출(DIRECT)" : "학생 바로제출(DIRECT)";
        };
        writeHistory(s, null, HistoryAction.SUBMITTED, memo);

        // 3) 챗봇 단계로 보낼지 여부
        if (body.getMode() == SubmitRequestDTO.SubmitMode.DIRECT) {
            // 검증 건너뜀 → 관리자 대기 큐 유지(SUBMITTED 유지)
            // 아무 것도 안 함
        } else {
            // 기본: 챗봇 검수로 전환
            s.setStatus(SubmissionStatus.BOT_REVIEW);
            submissionRepository.save(s);
        }

        // (참고) 챗봇 검수 통과 시: BOT API에서 BOT_REVIEW -> SUBMITTED 로 바꿔 관리자 큐에 올림
        return toSummary(s);
    }
    // ───────────────────────── 내부 유틸 ─────────────────────────

    @Transactional(readOnly = true)
    public SubmissionSummaryDTO getSummary(Integer submissionId) {
        // 소유권 체크가 필요하면 requireMySubmission(...)으로 바꾸세요.
        Submission s = requireSubmission(submissionId);
        return toSummary(s);
    }


    // 로그인 학생 검증
    private String currentStudentId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Member m)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return studentRepository.findByMember(m)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "학생 정보를 찾을 수 없습니다."))
                .getStudentId();
    }


    //  마감일 검증: deadline이 없거나(null) 오늘이 마감일보다 늦지 않으면(<=) 통과
    private void ensureNotPastDeadline(DocType docType) {
        deadlineRepository.findByDocType(docType).ifPresent(deadline -> {
            LocalDate d = deadline.getDeadline();
            LocalDate today = LocalDate.now(KST);
            if (d != null && today.isAfter(d)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "마감일이 지났습니다.");
            }
        });
    }

    // 이력 기록
    private void writeHistory(Submission s, Admin admin, HistoryAction action, String memo) {
        submissionHistoryRepository.save(
                SubmissionHistory.builder()
                        .submission(s)
                        .admin(admin) // 학생/시스템 액션이면 null
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

    private Submission requireSubmission(Integer id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제출을 찾을 수 없습니다."));
    }



    private void mustBeOneOf(Submission s, SubmissionStatus... allowed) {
        for (SubmissionStatus a : allowed) if (s.getStatus() == a) return;
        throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 상태에서 허용되지 않는 작업입니다.");
    }

    private void upsertFile(Submission submission, MultipartFile file) {

        String newUrl = fileStorageService.saveSubmission(submission.getSubmissionId(),file);

        submissionFileRepository.findBySubmission(submission).ifPresentOrElse(existing->{
            safeDelete(existing.getFileUrl());
            existing.setFileUrl(newUrl);
            existing.setUploadedAt(LocalDateTime.now());
            submissionFileRepository.save(existing);
        },()->{
            submissionFileRepository.save(
                    SubmissionFile.builder()
                            .submission(submission)
                            .fileUrl(newUrl)
                            .uploadedAt(LocalDateTime.now())
                            .build()
            );
        });

    }
    private void safeDelete(String url){
        try{
            fileStorageService.deleteByUrl(url);
        }catch(Exception ignored){}
    }
    private void upsertFieldValues(Submission submission, List<FieldValueInputDTO> inputs, DocType docType) {

        // 기존 값 전체 삭제 후 다시 저장 (덮어쓰기 정책)
        submissionFieldValueRepository.deleteBySubmission(submission);
        if (inputs == null || inputs.isEmpty()) return;

        // 해당 문서 유형의 필수항목 정의 불러오기
        List<RequiredField> defined = requiredFieldRepository.findByDocType(docType);

        // field_name(=RequiredField.fieldName) → RequiredField 매핑
        Map<String, RequiredField> byName = new HashMap<>();
        for (RequiredField rf : defined) {
            String name = rf.getFieldName();
            if (name != null && !name.isBlank()) {
                byName.put(name, rf);
            }
        }

        List<SubmissionFieldValue> rows = new ArrayList<>();
        for (FieldValueInputDTO in : inputs) {
            // 1) 필수 검증: label(field_name), value
            String label = (in.getLabel() == null) ? null : in.getLabel().trim();
            if (label == null || label.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "field_name(label)은 필수입니다.");
            }
            if (in.getValue() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value는 필수입니다.");
            }

            // 2) field_name 기준 매칭 (정의된 필드가 있으면 FK 연결)
            RequiredField rf = byName.get(label);

            rows.add(SubmissionFieldValue.builder()
                    .submission(submission)
                    .requiredField(rf)     // 매칭되면 FK 세팅, 없으면 null (FK가 NOT NULL이면 여기서 400 던지세요)
                    .fieldName(label)      // DB column field_name (NOT NULL)
                    .fieldValue(in.getValue())
                    .build());
        }

        submissionFieldValueRepository.saveAll(rows);
    }

    private List<FieldValueInputDTO> parseFields(String fieldsJson) {
        try {
            if (fieldsJson == null || fieldsJson.isBlank()) return Collections.emptyList();
            String s = fieldsJson.trim();

            // 바깥 따옴표로 감싼 경우 벗겨내기
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                s = s.substring(1, s.length() - 1).trim();
            }

            // 단일 객체 {…} 로 오면 허용
            if (s.startsWith("{") && s.endsWith("}")) {
                FieldValueInputDTO one = objectMapper.readValue(s, FieldValueInputDTO.class);
                return List.of(one);
            }

            // 기본: 배열
            return objectMapper.readValue(s, new TypeReference<List<FieldValueInputDTO>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldsJson 파싱 실패", e);
        }
    }

}


