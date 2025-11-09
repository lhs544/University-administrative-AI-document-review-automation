package com.cnu.docserver.auth.dto;

import lombok.*;

@Getter
@Setter // 요청 DTO는 외부로부터 데이터를 받기 때문에 @Setter 사용이 일반적
public class LoginRequestDTO {

    private String memberId;
    private String password;

}
