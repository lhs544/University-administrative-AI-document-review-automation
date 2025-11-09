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
@Table(name = "original_files")
public class OriginalFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer originalFileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocType docType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String fileUrl;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
    }
}
