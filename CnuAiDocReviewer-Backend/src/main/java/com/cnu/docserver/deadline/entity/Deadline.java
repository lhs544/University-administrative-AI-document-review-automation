package com.cnu.docserver.deadline.entity;

import com.cnu.docserver.docmanger.entity.DocType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

@Entity
@Table(name = "deadlines")
public class Deadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer deadlineId;

    @OneToOne
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocType docType;

    private LocalDate deadline;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
