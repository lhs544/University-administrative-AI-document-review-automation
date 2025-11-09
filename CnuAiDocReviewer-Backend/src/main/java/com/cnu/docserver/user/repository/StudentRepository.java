package com.cnu.docserver.user.repository;

import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, String> {
    Optional<Student> findByMember(Member member);
}
