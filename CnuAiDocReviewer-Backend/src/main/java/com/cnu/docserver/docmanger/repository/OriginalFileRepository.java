package com.cnu.docserver.docmanger.repository;

import com.cnu.docserver.docmanger.entity.DocType;
import com.cnu.docserver.docmanger.entity.OriginalFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OriginalFileRepository extends JpaRepository<OriginalFile, Integer> {
    Optional<OriginalFile> findByDocType(DocType docType);
    void deleteByDocType(DocType docType);
}
