package com.cnu.docserver.submission.entity;

import com.cnu.docserver.submission.enums.HistoryAction;
import com.cnu.docserver.user.entity.Admin;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

@Entity
@Table(name = "submission_histories")
public class SubmissionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_history_id")
    private Integer submissionHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;


    @ManyToOne(optional = true)
    @JoinColumn(name = "admin_id", nullable = true)
    private Admin admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = true, length = 40)
    private HistoryAction action;

    @Column(name = "memo")
    private String memo;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @PrePersist
    public void onCreate() {
        if (changedAt == null) changedAt = LocalDateTime.now();
    }



}
