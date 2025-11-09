package com.cnu.docserver.ocr;

import com.cnu.docserver.docmanger.service.FileStorageService;
import com.cnu.docserver.ocr.OcrClient.OcrException;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFile;
import com.cnu.docserver.submission.event.SubmissionCreatedEvent;
import com.cnu.docserver.submission.repository.SubmissionFileRepository;
import com.cnu.docserver.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionReviewOrchestrator {

    private final SubmissionRepository submissionRepo;
    private final SubmissionFileRepository fileRepo;
    private final OcrClient ocrClient;
    private final FileStorageService fileStorageService;
    private final ReviewTransactionService reviewTransactionService; // ⭐ 새로 추가된 서비스

    /**
     * SubmissionService에서 이벤트가 발행되면 호출되는 메서드
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async // OCR 작업은 비동기로 처리
    public void onSubmissionCreated(SubmissionCreatedEvent event) {
        this.runBotReview(event.getSubmissionId());
    }

    public void runBotReview(Integer submissionId) {
        log.info("🤖 Starting OCR review for submission ID: {}", submissionId);

        Submission s = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            String fileUrl = fileRepo.findTopBySubmissionOrderBySubmissionFileIdDesc(s)
                    .map(SubmissionFile::getFileUrl)
                    .orElseThrow(() -> {
                        log.error("❌ File not found for submission ID: {}", submissionId);
                        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 없음");
                    });

            log.info("📞 Calling OCR service for submission ID: {} with file URL: {}", submissionId, fileUrl);
            byte[] fileBytes = fileStorageService.readBytes(fileUrl);
            long t0 = System.currentTimeMillis();
            OcrClient.OcrResult res = ocrClient.review(fileBytes, "submission.pdf");
            long latency = System.currentTimeMillis() - t0;
            log.info("✅ OCR service call successful for submission ID: {}. Verdict: {}, Latency: {}ms",
                    submissionId, res.getVerdict(), latency);

            // ⭐ 분리된 서비스의 메소드 호출
            reviewTransactionService.updateSubmissionStatus(submissionId, res, latency);

        } catch (FileStorageService.FileReadException e) {
            log.error("❌ Failed to read file for submission {}: {}", submissionId, e.getMessage(), e);
            reviewTransactionService.saveAsNeedsFix(submissionId, "자동 검토 실패: 파일 읽기 오류 - " + e.getMessage());
        } catch (OcrClient.OcrException e) {
            log.error("❌ OCR service call failed for submission {}: {}", submissionId, e.getMessage(), e);
            reviewTransactionService.saveAsNeedsFix(submissionId, "자동 검토 실패: OCR 호출 오류 - " + e.getMessage());
        } catch (ResponseStatusException e) {
            log.warn("⚠️ OCR review skipped for submission {} due to client-side error: {}", submissionId, e.getMessage());
        } catch (Throwable t) {
            log.error("❌ Unexpected error during bot review for submission {}: {}", submissionId, t.getMessage(), t);
            reviewTransactionService.saveAsNeedsFix(submissionId, "자동 검토 실패: 시스템 오류 - " + firstLine(t.getMessage()));
        }
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int p = s.indexOf('\n');
        return p >= 0 ? s.substring(0, p) : s;
    }
}