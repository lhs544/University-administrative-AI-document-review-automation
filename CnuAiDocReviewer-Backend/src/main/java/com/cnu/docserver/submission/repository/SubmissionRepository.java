package com.cnu.docserver.submission.repository;

import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.submission.entity.Submission;
import com.cnu.docserver.submission.enums.SubmissionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    // 기존: 상태 목록으로 필터
    List<Submission> findByDocType_DepartmentAndStatusInOrderBySubmittedAtDesc(
            Department department,
            List<SubmissionStatus> statuses
    );
    List<Submission> findByDocType_DepartmentOrderBySubmittedAtDesc(Department department);

    // ▶ 학생 본인 최신 제출 N개
    List<Submission> findByStudent_StudentIdOrderBySubmissionIdDesc(
            String studentId, Pageable pageable);

    // ▶ 상태 필터 포함
    List<Submission> findByStudent_StudentIdAndStatusInOrderBySubmissionIdDesc(
            String studentId, List<SubmissionStatus> statuses, Pageable pageable);
    @Query("""
      from Submission s
      join fetch s.student st
      join fetch st.member m
      left join fetch s.docType dt
      where s.submissionId = :id
    """)
    Optional<Submission> findDetailById(Integer id);

}
