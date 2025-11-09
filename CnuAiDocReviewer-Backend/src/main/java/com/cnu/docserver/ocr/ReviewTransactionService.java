package com.cnu.docserver.ocr;

import com.cnu.docserver.ocr.OcrClient.OcrResult;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionHistory;
import com.cnu.docserver.submission.enums.HistoryAction;
import com.cnu.docserver.submission.enums.SubmissionStatus;
import com.cnu.docserver.submission.repository.SubmissionHistoryRepository;
import com.cnu.docserver.submission.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewTransactionService {

    private final SubmissionRepository submissionRepo;
    private final SubmissionHistoryRepository historyRepo;
    private final ObjectMapper objectMapper;

    private static final boolean OCR_DETAIL_ENABLED =
            Boolean.parseBoolean(System.getProperty("ocr.detail.enabled",
                    System.getenv().getOrDefault("OCR_DETAIL_ENABLED", "false")));

    /**
     * OCR 결과를 바탕으로 DB 상태를 업데이트하는 트랜잭션 메서드
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSubmissionStatus(Integer submissionId, OcrResult res, long latency) {
        Submission s = submissionRepo.findById(submissionId)
                .orElseThrow(() -> {
                    log.error("Database entry for submission ID: {} not found during status update.", submissionId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND);
                });

        String verdict = Optional.ofNullable(res.getVerdict()).orElse("").toUpperCase();
        String humanMemo;
        HistoryAction actionForHistory;

        switch (verdict) {
            case "PASS" -> {
                s.setStatus(SubmissionStatus.SUBMITTED);
                humanMemo = "자동 검토 통과, 관리자 검토 대기";
                actionForHistory = HistoryAction.MODIFIED;
            }
            case "NEEDS_FIX" -> {
                s.setStatus(SubmissionStatus.NEEDS_FIX);
                String reasonMsg = (res.getFindings() == null || res.getFindings().isEmpty())
                        ? (res.getReason() == null ? "사유 미기재" : res.getReason())
                        : res.getFindings().stream()
                        .map(f -> f.getLabel() + ": " + f.getMessage())
                        .limit(10).collect(Collectors.joining("; "));
                humanMemo = "자동 검토 실패: " + reasonMsg;
                actionForHistory = HistoryAction.MODIFIED;
            }
            case "REJECT" -> {
                s.setStatus(SubmissionStatus.REJECTED);
                humanMemo = "자동 검토 실패: " + (res.getReason() == null ? "사유 미기재" : res.getReason());
                actionForHistory = HistoryAction.REJECTED;
            }
            default -> {
                s.setStatus(SubmissionStatus.NEEDS_FIX);
                humanMemo = "자동 검토 실패: OCR 응답 이상";
                actionForHistory = HistoryAction.MODIFIED;
            }
        }
        log.info("➡️ Updating submission ID {} status to {}. Memo: {}", submissionId, s.getStatus(), humanMemo);

        historyRepo.save(SubmissionHistory.builder()
                .submission(s)
                .action(actionForHistory)
                .memo(humanMemo)
                .build());

        if (OCR_DETAIL_ENABLED) {
            try {
                ObjectNode root = objectMapper.createObjectNode();
                root.put("type", "OCR");
                root.put("verdict", verdict);
                root.put("latency", latency);

                ArrayNode arr = objectMapper.createArrayNode();
                Optional.ofNullable(res.getFindings()).orElse(List.of()).forEach(f -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("label", f.getLabel());
                    item.put("message", f.getMessage());
                    arr.add(item);
                });
                root.set("findings", arr);

                root.put("reason", Optional.ofNullable(res.getReason()).orElse(null));
                root.put("debug_text", Optional.ofNullable(res.getDebugText()).orElse(null));

                String json = objectMapper.writeValueAsString(root);
                log.debug("📝 Saving OCR_DETAIL for submission ID {}: {}", submissionId, json);

                historyRepo.save(SubmissionHistory.builder()
                        .submission(s)
                        .action(HistoryAction.MODIFIED)
                        .memo("OCR_DETAIL " + json)
                        .build());
            } catch (Exception e) {
                log.error("JSON serialization failed for submission ID {}: {}", submissionId, e.getMessage(), e);
            }
        }

        submissionRepo.saveAndFlush(s);
        log.info("✅ Submission ID {} status update and history saved successfully.", submissionId);
    }

    /**
     * 오류 발생 시 상태를 NEEDS_FIX로 업데이트하는 트랜잭션 메서드
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAsNeedsFix(Integer submissionId, String memo) {
        log.warn("⚠️ Setting status to NEEDS_FIX for submission ID {}. Reason: {}", submissionId, memo);
        Submission s = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        s.setStatus(SubmissionStatus.NEEDS_FIX);
        submissionRepo.saveAndFlush(s);

        historyRepo.save(SubmissionHistory.builder()
                .submission(s)
                .action(HistoryAction.MODIFIED)
                .memo(memo)
                .build());
    }

}