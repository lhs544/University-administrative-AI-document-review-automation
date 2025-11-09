package com.cnu.docserver.submission.repository;

import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.entity.SubmissionHistory;
import org.springdoc.core.converters.models.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionHistoryRepository extends JpaRepository<SubmissionHistory, Integer> {
    List<SubmissionHistory> findBySubmissionOrderByChangedAtAsc(Submission submission);
    // 기존: 전체 목록
    List<SubmissionHistory> findBySubmissionOrderBySubmissionHistoryIdAsc(Submission submission);
    // 혹은 최신순이 필요하면 Desc 버전도 함께
    List<SubmissionHistory> findBySubmissionOrderBySubmissionHistoryIdDesc(Submission submission);
}
