package com.cnu.docserver.auth.service;

import com.cnu.docserver.auth.dto.LoginRequestDTO;
import com.cnu.docserver.auth.dto.LoginResponseDTO;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.repository.MemberRepository;
import com.cnu.docserver.user.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;

    public LoginResponseDTO login(LoginRequestDTO request) {
        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("해당 아이디의 사용자가 존재하지 않습니다."));

        if (!member.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return LoginResponseDTO.from(member);
    }

    public Member findMemberById(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }


}
