package com.cnu.docserver.user.repository;

import com.cnu.docserver.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, String> {



}
