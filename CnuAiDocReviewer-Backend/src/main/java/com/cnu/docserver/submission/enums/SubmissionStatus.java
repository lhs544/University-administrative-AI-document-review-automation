package com.cnu.docserver.submission.enums;

public enum SubmissionStatus {
    DRAFT,        // 임시저장
    BOT_REVIEW,   // ← 챗봇 검수 중
    SUBMITTED,    // 챗봇 통과 → 관리자 대기
    UNDER_REVIEW, // 관리자가 열어 검토 중
    NEEDS_FIX,    // (선택) 봇/관리자 보정요청
    APPROVED,     // 관리자 승인
    REJECTED      // 관리자 반려
}
