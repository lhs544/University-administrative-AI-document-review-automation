package com.cnu.docserver.submission.dto;

public record HistoryDTO(
        Integer  historyId,
        String action,          // SUBMITTED / APPROVED / REJECTED / MODIFIED
        String memo,
        String adminName,       // 관리자가 기록한 경우만 값, 아니면 null
        String changedAt        // ISO 문자열
) {}