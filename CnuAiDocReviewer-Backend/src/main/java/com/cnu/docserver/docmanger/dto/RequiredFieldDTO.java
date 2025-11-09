package com.cnu.docserver.docmanger.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequiredFieldDTO {
    private Integer requiredFieldId; // FK 매핑용
    private String label;            // 화면 라벨 (현재 fieldName)
    private String example;          // 예시값
    private boolean required;        // 없으면 true 고정
    private Integer orderNo;         // 정렬(선택)
}
