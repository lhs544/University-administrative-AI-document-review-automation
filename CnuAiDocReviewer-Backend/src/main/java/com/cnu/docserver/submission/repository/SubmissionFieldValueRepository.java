package com.cnu.docserver.submission.repository;

import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionFieldValueRepository extends JpaRepository<SubmissionFieldValue, Integer> {

    List<SubmissionFieldValue> findBySubmission(Submission submission);
    void deleteBySubmission(Submission submission);
}
