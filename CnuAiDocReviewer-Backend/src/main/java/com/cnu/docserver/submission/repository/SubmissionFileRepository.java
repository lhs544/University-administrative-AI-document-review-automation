package com.cnu.docserver.submission.repository;

import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, Integer> {
    Optional<SubmissionFile> findBySubmission(Submission submission);
    Optional<SubmissionFile> findTopBySubmissionOrderByUploadedAtDesc(Submission submission);
    Optional<SubmissionFile> findTopBySubmissionOrderBySubmissionFileIdDesc(Submission submission);

}
