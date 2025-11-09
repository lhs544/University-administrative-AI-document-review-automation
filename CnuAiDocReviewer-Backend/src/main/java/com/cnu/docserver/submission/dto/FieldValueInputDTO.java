package com.cnu.docserver.submission.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FieldValueInputDTO {
    private Integer requiredFieldId;
    private String label;
    private String value;
}
