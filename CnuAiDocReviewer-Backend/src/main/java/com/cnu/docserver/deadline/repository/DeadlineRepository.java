package com.cnu.docserver.deadline.repository;

import com.cnu.docserver.deadline.entity.Deadline;
import com.cnu.docserver.docmanger.entity.DocType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeadlineRepository extends JpaRepository<Deadline,Integer> {

    // 1. docType 기준으로 마감일 조회
    Optional<Deadline> findByDocType(DocType docType);

}
