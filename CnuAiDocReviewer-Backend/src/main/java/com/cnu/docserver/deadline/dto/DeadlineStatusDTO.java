package com.cnu.docserver.deadline.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeadlineStatusDTO {

    private Integer docTypeId;
    private String title;
    private LocalDate deadline; // null이면 "없음"
}
