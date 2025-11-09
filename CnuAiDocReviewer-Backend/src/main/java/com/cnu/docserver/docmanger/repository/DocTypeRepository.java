package com.cnu.docserver.docmanger.repository;

import com.cnu.docserver.department.entity.Department;
import com.cnu.docserver.docmanger.entity.DocType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocTypeRepository extends JpaRepository<DocType, Integer> {
    List<DocType> findByDepartment(Department department); // 부서별 서류 목록
}
