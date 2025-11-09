// src/main/java/com/cnu/docserver/ocr/OCRReviewResult.java
package com.cnu.docserver.ocr;

import com.cnu.docserver.ocr.dto.Finding;
import com.cnu.docserver.submission.entity.Submission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "ocr_review_result")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OCRReviewResult {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Submission submission;

    @Column(length = 32, nullable = false)
    private String verdict;   // PASS | NEEDS_FIX | REJECT

    @Lob
    @Column(columnDefinition = "TEXT")
    private String findingsJson;   // JSON 문자열 저장

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String debugText;

    private LocalDateTime createdAt;

    @Transient
    private static final ObjectMapper om = new ObjectMapper();

    // 편의 메서드
    @Transient
    public List<Finding> getFindings() {
        try {
            if (findingsJson == null || findingsJson.isBlank()) return List.of();
            return om.readValue(findingsJson, new TypeReference<List<Finding>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setFindings(List<Finding> list) {
        try {
            this.findingsJson = (list == null ? "[]" : om.writeValueAsString(list));
        } catch (Exception e) {
            this.findingsJson = "[]";
        }
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
