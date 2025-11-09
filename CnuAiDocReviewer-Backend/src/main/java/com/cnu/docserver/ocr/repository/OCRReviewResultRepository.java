package com.cnu.docserver.ocr.repository;

import com.cnu.docserver.ocr.OCRReviewResult;
import com.cnu.docserver.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OCRReviewResultRepository extends JpaRepository<OCRReviewResult, Integer> {
    Optional<OCRReviewResult> findTopBySubmissionOrderByIdDesc(Submission submission);
}
