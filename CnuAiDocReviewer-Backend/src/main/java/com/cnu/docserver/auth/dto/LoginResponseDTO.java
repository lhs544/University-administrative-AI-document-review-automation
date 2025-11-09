package com.cnu.docserver.auth.dto;

import com.cnu.docserver.user.entity.Member;
import lombok.*;;
@Getter
@Builder
public class LoginResponseDTO {
    private String memberId;
    private String name;
    private String role;
    private String department;
    private String academicStatus;

    public static LoginResponseDTO from(Member member) {
        String department = null;
        String academicStatus = null;

        if (member.getStudent() != null) {
            department = member.getStudent().getDepartment();
            academicStatus = member.getStudent().getAcademicStatus().name();
        }

        return LoginResponseDTO.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .role(member.getRole().name())
                .department(department)
                .academicStatus(academicStatus)
                .build();
    }
}
