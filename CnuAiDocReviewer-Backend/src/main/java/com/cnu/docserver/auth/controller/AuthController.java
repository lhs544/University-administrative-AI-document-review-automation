package com.cnu.docserver.auth.controller;

import com.cnu.docserver.auth.dto.LoginRequestDTO;
import com.cnu.docserver.auth.dto.LoginResponseDTO;
import com.cnu.docserver.auth.service.AuthService;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.repository.StudentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final StudentRepository studentRepository;

    /**
     * 로그인 처리: ID, PW 기반 로그인 후 세션에 사용자 저장
     */

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하고 세션에 사용자 정보를 저장합니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @RequestBody LoginRequestDTO requestDTO,
            HttpSession session
    ) {
        // 1) 로그인 처리 및 사용자 정보 획득
        LoginResponseDTO response = authService.login(requestDTO);

        // 2) 로그인 사용자 로드
        Member loginMember = authService.findMemberById(response.getMemberId());

        // 3) 권한 구성 (프로젝트에 맞게 로직 결정)
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 기본적으로 학생이면 ROLE_STUDENT 부여
        String roleName = "ROLE_" + loginMember.getRole().name(); // e.g. ROLE_STUDENT, ROLE_ADMIN
        authorities.add(new SimpleGrantedAuthority(roleName));

        //  학생인 경우 실제 Student 레코드가 없으면 로그인 실패로 처리 (데이터 무결성)
        if ("ROLE_STUDENT".equals(roleName) && studentRepository.findByMember(loginMember).isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        // 4) SecurityContext 에 Authentication 심기
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginMember, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // 5) 세션에 SecurityContext 저장 (중요)
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);

        // 6) 세션에 Member도 저장 — 기존 코드 호환용
        session.setAttribute("loginUser", loginMember);

        return ResponseEntity.ok(response);
    }

    /**
     * 현재 로그인된 사용자 정보 반환 (세션 기반)
     */
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자 정보를 반환합니다.")
    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO> getMyInfo(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginUser");

        if (loginMember == null) {
            return ResponseEntity.status(401).build(); // UNAUTHORIZED
        }

        return ResponseEntity.ok(LoginResponseDTO.from(loginMember));
    }
}
