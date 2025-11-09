package com.cnu.docserver.submission.entity;

import com.cnu.docserver.docmanger.entity.RequiredField;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.aot.generate.GeneratedTypeReference;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

@Entity
@Table(name = "submission_field_values")
public class SubmissionFieldValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_field_value_id")
    private Integer submissionFieldValueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_field_id")
    private RequiredField requiredField;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;
}
