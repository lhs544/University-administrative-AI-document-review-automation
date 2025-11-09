package com.cnu.docserver.docmanger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

@Entity
@Table(name = "required_fields")
public class RequiredField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer requiredFieldId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocType docType;

    @Column(nullable = false, length = 100)
    private String fieldName;

    @Column(length = 255)
    private String exampleValue;

}
