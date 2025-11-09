package com.cnu.docserver.docmanger.repository;

import com.cnu.docserver.docmanger.entity.DocType;
import com.cnu.docserver.docmanger.entity.RequiredField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequiredFieldRepository extends JpaRepository<RequiredField, Integer> {
    List<RequiredField> findByDocType(DocType docType);
}