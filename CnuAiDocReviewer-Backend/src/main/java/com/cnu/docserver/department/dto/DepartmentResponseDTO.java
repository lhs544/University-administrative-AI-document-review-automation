package com.cnu.docserver.department.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DepartmentResponseDTO {

    Integer id;

    String name;

    String phone;
}
