package com.cnu.docserver.submission.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

@Entity
@Table(name = "submission_files")
public class SubmissionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_file_id")
    private Integer submissionFileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @PrePersist
    public void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }
}

