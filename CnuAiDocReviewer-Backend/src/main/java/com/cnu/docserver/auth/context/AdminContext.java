package com.cnu.docserver.auth.context;


import com.cnu.docserver.user.entity.Admin;
import com.cnu.docserver.user.entity.Member;
import com.cnu.docserver.user.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminContext {

    private final AdminRepository adminRepository;

    public Admin currentAdminOrThrow() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Member m)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return adminRepository.findByMember(m)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));
    }
}