package com.cnu.docserver.user.repository;

import com.cnu.docserver.user.entity.Admin;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository  extends JpaRepository<Admin, String> {
    Optional<Admin> findByMember(Member member);
}
