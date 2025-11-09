export const STATUS = {
    DRAFT: "DRAFT",
    BOT_REVIEW: "BOT_REVIEW",
    SUBMITTED: "SUBMITTED",
    UNDER_REVIEW: "UNDER_REVIEW",
    NEEDS_FIX: "NEEDS_FIX",
    APPROVED: "APPROVED",
    REJECTED: "REJECTED",
};


export const SCENARIOS = {
    // ▶ 진입/인증
    INIT: {
        message: "안녕하세요! 무엇을 도와드릴까요?",
        options: ["서류 제출", "서류 제출 현황"],
        // API: (선택) GET /auth/me 로 로그인 확인 후 미로그인 시 로그인 유도
    },

    // ① 제출 흐름 -------------------------------------------------------------

    // ▶ 부서 선택 (동적 로딩)
    SELECT_DEPT: (departments) => ({
        message: "어느 부서에 서류를 제출하시나요?\n※ 검색으로 빠르게 찾을 수 있어요!",
        options: departments.map(d => `${d.leftLabel ?? ''}${d.leftLabel ? '|' : ''}${d.name}`),
        searchable: true,
        // API: GET /api/departments
    }),

    // ▶ 서류 유형 선택 (부서 선택 이후 동적)
    SELECT_TYPE: (docTypes) => ({
        message: "제출하실 서류 유형을 선택해주세요.",
        options: docTypes.map(t => t.name),
        searchable: true,
        // API: GET /api/departments/{departmentId}/doc-types
    }),

    // ▶ 제출 기한 확인
    CHECK_DEADLINE: {
        message: "제출 기한을 확인 중입니다...",
        // API: GET /api/doc-types/{docTypeId}/deadline
    },

    DEADLINE_VALID: (deadlineText) => ({
        message: `제출 기한: ${deadlineText}\n다음 단계로 진행합니다.`,
    }),

    DEADLINE_EXPIRED: (date) => ({
        message: `이 서류의 제출 기한은 ${date}이었습니다.\n현재는 제출할 수 없습니다.`,
        options: ["다른 서류 제출하기", "챗봇 종료하기"],
    }),

    // ▶ 폼(필수항목) 안내 + 파일 업로드
    FORM_AND_FILE_PROMPT: (requiredFields) => ({
        message:
            "필수 입력 항목을 작성하고, 파일(PDF/JPG/PNG 중 1개)을 업로드해주세요.\n" +
            requiredFields.map(f => `- ${f.label}${f.required ? ' *' : ''}`).join("\n"),
        uploadEnabled: true,
        accepts: [".pdf", ".jpg", ".jpeg", ".png"],
        // API: GET /api/doc-types/{docTypeId}/required-fields
    }),

    // ▶ 업로드 중/완료
    FILE_UPLOADED_PROCESSING: {
        message: "업로드가 완료되었습니다. 서류를 분석 중입니다...",
        systemProcessing: true,
        // API: POST /api/submissions (multipart: docTypeId, fieldsJson, file)
        // 이후 서버가 상태를 PENDING_BOT_REVIEW 로 전이
    },

    UPLOAD_FAILED: (reason) => ({
        message: `제출에 실패했습니다.\n사유: ${reason}\n다시 시도해주세요.`,
        uploadEnabled: true,
    }),

    SERVER_ERROR: {
        message:
            "지속적인 오류가 발생하고 있습니다.\n담당자에게 문의해주세요.\n이메일:  \n전화번호: ",
    },
    BOT_ANALYSIS_LOG: (text) => ({
        message: `\n[자동 검토 로그]\n${text || '(로그 없음)'}`
    }),
    // ▶ 챗봇(봇) 1차 검토 결과
    BOT_FEEDBACK_PASS: {
        message: "자동 검토 통과, 관리자 검토 대기",
    },
    BOT_FEEDBACK_FAIL: (reason) => ({
        message: `자동 검토 실패: ${reason || "사유 미기재"}\n수정 후 재제출하시겠습니까?`,
        options: ["수정 후 재제출", "바로 제출"], // 재제출 버튼 추가
    }),

    // ▶ 반려 후 재제출(리비전 없이 덮어쓰기)
    RESUBMIT_PROMPT: {
        message: "수정된 내용을 반영해 주세요. (필드값이나 파일을 다시 업로드하면 덮어쓰기됩니다)",
        uploadEnabled: true,
        accepts: [".pdf", ".jpg", ".jpeg", ".png"],
        options: ["수정 내용 저장", "바로 제출"],
        // API: PUT /api/submissions/{id}  (multipart 허용)
    },

    RESUBMIT_SAVED: {
        message: "수정 내용이 저장되었습니다. 제출하시겠습니까?",
        options: ["제출하기", "더 수정하기"],
    },

    // ▶ 최종 제출 (상태 전이)
    FINAL_SUBMITTING: {
        message: "제출 중입니다...",
        // API: POST /api/submissions/{id}/submit  → PENDING_BOT_REVIEW
    },

    FINAL_SUBMITTED: {
        message: "제출이 완료되었습니다. 자동 검토 후 관리자에게 전달됩니다.",
    },

    // ② 제출 현황/관리자 검토 보기 --------------------------------------------

    CHECK_STATUS: {
        message: "최근 제출한 서류 목록입니다.",
        showList: true,
        options: ["전체", "승인 완료", "반려", "검토 중"],
        // API: GET /api/my/submissions?status=...
    },

    // ▶ 제출 상세(학생이 관리자 검토 내용도 열람)
    SUBMISSION_DETAIL: (detail) => ({
        // detail = { status, fileUrl, submittedAt, botFindings[], admin: { reviewer, decidedAt, decisionMemo, fieldNotes[] } }
        message:
            `상태: ${detail.status}\n제출일: ${detail.submittedAt}\n` +
            (detail.botFindings?.length
                ? `\n자동 검토 결과:\n${detail.botFindings.map(f => `- ${f.label}: ${f.message}`).join("\n")}\n`
                : "") +
            (detail.admin
                ? `\n관리자 검토 (${detail.admin.reviewer}, ${detail.admin.decidedAt})\n` +
                (detail.admin.fieldNotes?.length
                    ? `- 필드별 코멘트:\n${detail.admin.fieldNotes.map(n => `  · ${n.label}: ${n.comment}`).join("\n")}\n`
                    : "") +
                (detail.admin.decisionMemo ? `- 종합 메모: ${detail.admin.decisionMemo}` : "")
                : ""),
        options:
            detail.status === STATUS.REJECTED
                ? ["수정 후 재제출", "뒤로가기"]
                : ["뒤로가기"],
        // API:
        // GET /api/submissions/{id} (요약)
        // GET /api/submissions/{id}/field-values (필드 검증/메모)
        // GET /api/submissions/{id}/histories (결정/메모 타임라인)
        // (선택) GET /api/submissions/{id}/admin-feedback 한 방에 내려줘도 좋음
    }),

    // ▶ 반려 사유 안내(관리자)
    ADMIN_REJECTION_REASON: (reasons) => ({
        message: `해당 서류는 다음 이유로 반려되었습니다:\n- ${reasons.join("\n- ")}`,
        options: ["수정 후 재제출", "뒤로가기", "종료"],
    }),

    LINKED_HISTORY_UPDATED: {
        message: "기존 제출 이력과 연결되어 상태가 갱신되었습니다.",
    },

    END_CHATBOT: {
        message: "이용해주셔서 감사합니다. 챗봇을 종료합니다!",
    },

    UNSUPPORTED: {
        message: "아직 지원하지 않는 기능입니다. 다른 요청을 선택해주세요.",
        options: ["서류 제출", "서류 제출 현황"],
    },
};
