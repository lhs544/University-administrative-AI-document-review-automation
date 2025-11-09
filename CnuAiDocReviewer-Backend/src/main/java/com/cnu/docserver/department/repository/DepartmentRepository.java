package com.cnu.docserver.department.repository;

import com.cnu.docserver.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;


public interface DepartmentRepository extends JpaRepository<Department, Integer> {
}
