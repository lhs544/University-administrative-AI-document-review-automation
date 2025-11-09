// hooks/useChatScenario.js
import { useCallback, useEffect, useRef, useState } from "react";
import {
    getStudentDepartments,
    getDocTypesByDepartmentPublic,
    getRequiredFields,
    getDeadline,
    createSubmission,
    getSubmissionSummary,
    getBotReviewResult,
    listMySubmissions,
    pickErrorMessage,
} from "../api/api";
import { SCENARIOS, STATUS } from "../utils/scenarioConstants";

/* =========================
 * 상수/유틸
 * ========================= */
const statusLabel = {
    DRAFT: "임시저장",
    BOT_REVIEW: "챗봇 검사",
    SUBMITTED: "관리자 대기",
    UNDER_REVIEW: "관리자 검토 중",
    NEEDS_FIX: "보정 요청",
    APPROVED: "승인 완료",
    REJECTED: "반려 처리",
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const formatDate = (isoStr) => {
    if (!isoStr) return "";
    const d = new Date(isoStr);
    if (Number.isNaN(d.valueOf())) return isoStr;
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const hh = String(d.getHours()).padStart(2, "0");
    const mi = String(d.getMinutes()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
};

const normDept = (d) => ({ id: d?.id, name: d?.name, leftLabel: null });
const normDocType = (t) => ({ id: t?.docTypeId, name: t?.title });
const normRequiredField = (it, idx) => ({
    label:
        typeof it === "string"
            ? it
            : it?.label ?? it?.name ?? it?.title ?? `필수 항목 ${idx + 1}`,
    required: true,
});
const isExpired = (deadlineStr) => {
    if (!deadlineStr) return false;
    const now = new Date();
    const d = new Date(deadlineStr);
    return Number.isFinite(d.valueOf()) && d < now;
};

/** OCR 결과 사유 우선순위 추출 */
async function fetchReviewReasons(submissionId) {
    try {
        const review = await getBotReviewResult(submissionId);
        if (Array.isArray(review?.findings) && review.findings.length) {
            return review.findings.map((f) => `${f.label}: ${f.message}`);
        }
        if (review?.reason) return [review.reason];
        if (Array.isArray(review?.debugTexts) && review.debugTexts.length) {
            return review.debugTexts.slice(0, 5);
        }
    } catch (_) {
        /* ignore */
    }
    return [];
}

/**
 * 상태 폴링(최대 24시간)
 * - BOT_REVIEW는 완료 상태 아님
 * - 네트워크 오류는 백오프 재시도
 * - 5분마다 onProgress 호출
 */
async function pollUntilDone(getSummaryFn, submissionId, opts) {
    const {
        initialDelayMs = 2000,
        stepMs = 3000,
        maxDelayMs = 9000000,
        timeoutMs = 24 * 60 * 60 * 1000, // 24h
        onProgress = null, // (elapsedMs, status) => void
        isCancelled = () => false, // 외부에서 취소 지원
    } = opts || {};

    const DONE = new Set([
        STATUS.NEEDS_FIX,
        STATUS.REJECTED,
        STATUS.SUBMITTED,
        STATUS.UNDER_REVIEW,
        STATUS.APPROVED,
    ]);

    let delay = initialDelayMs;
    let elapsed = 0;

    // 첫 조회
    let summary = null;
    try {
        summary = await getSummaryFn(submissionId);
    } catch (_) {
        // 첫 호출 실패 시에도 계속 진행
    }
    let lastStatus = summary?.status || null;
    if (lastStatus && DONE.has(lastStatus)) return summary;

    // 5분마다 한 번만 진행 메시지
    let lastProgressAt = Date.now();

    while (elapsed < timeoutMs && !isCancelled()) {
        await sleep(delay);
        elapsed += delay;
        delay = Math.min(delay + stepMs, maxDelayMs);

        // 네트워크 오류 시 백오프 재시도
        let attempt = 0;
        while (attempt < 3) {
            try {
                summary = await getSummaryFn(submissionId);
                break; // 성공
            } catch (e) {
                attempt += 1;
                if (attempt >= 3) throw e; // 3회 연속 실패면 밖으로
                await sleep(1000 * attempt); // 1s, 2s 백오프
            }
        }

        lastStatus = summary?.status;
        if (lastStatus && DONE.has(lastStatus)) return summary;

        const now = Date.now();
        if (onProgress && now - lastProgressAt >= 5 * 60 * 1000) {
            onProgress(elapsed, lastStatus || null);
            lastProgressAt = now;
        }
    }

    return summary ?? null; // 타임아웃 시 마지막 스냅샷
}

/* =========================
 * 메인 훅
 * ========================= */
export default function useChatScenario() {
    const [chatHistory, setChatHistory] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [docTypes, setDocTypes] = useState([]);
    const [selectedDeptId, setSelectedDeptId] = useState(null);
    const [selectedDocType, setSelectedDocType] = useState(null); // { id, name }
    const [deadlineInfo, setDeadlineInfo] = useState(null); // {deadline:string}|null

    const bootstrapped = useRef(false);
    const cancelledRef = useRef(false);       // 언마운트/재업로드 시 폴링 취소
    const progressMinuteRef = useRef(0);      // 진행 메시지 분 단위 중복 방지

    const pushBot = useCallback((msg) => {
        setChatHistory((h) => [...h, { from: "bot", ...msg }]);
    }, []);
    const pushUser = useCallback((text) => {
        setChatHistory((h) => [...h, { from: "user", message: text }]);
    }, []);

    /** ▶ 부서 선택 단계로 깔끔히 리셋 */
    const resetToDeptSelect = useCallback(() => {
        setSelectedDeptId(null);
        setSelectedDocType(null);
        setDocTypes([]);
        setDeadlineInfo(null);

        pushBot(
            SCENARIOS.SELECT_DEPT(
                (departments || []).map((d) => ({
                    id: d.id,
                    name: d.name,
                    leftLabel: d.leftLabel,
                }))
            )
        );
    }, [departments, pushBot]);

    // 초기 로딩
    useEffect(() => {
        if (bootstrapped.current) return;
        bootstrapped.current = true;

        (async () => {
            try {
                const deptsRes = await getStudentDepartments();
                const normDepts = (deptsRes || []).map(normDept).filter((d) => d.id && d.name);
                setDepartments(normDepts);
                pushBot({ message: SCENARIOS.INIT.message, options: SCENARIOS.INIT.options });
            } catch (e) {
                console.error("[INIT] departments load error:", e);
                pushBot(SCENARIOS.SERVER_ERROR);
            }
        })();

        return () => {
            cancelledRef.current = true; // 언마운트 시 모든 폴링 중단
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleUserInput = useCallback(
        async (raw) => {
            if (!raw) return;
            const text = String(raw).trim();
            pushUser(text);

            // 공통 메뉴
            if (text === "서류 제출") {
                resetToDeptSelect();
                return;
            }
            if (text === "서류 제출 현황" || text === "뒤로가기") {
                try {
                    const rows = await listMySubmissions({ limit: 10 });
                    if (!rows || rows.length === 0) {
                        pushBot({ message: "제출 이력이 없습니다." });
                    } else {
                        const lines = rows.map((r) => {
                            const status = statusLabel[r.status] || r.status;
                            return `- ${status} | ${r.title || "(제목 없음)"} | ${formatDate(r.submittedAt)}`;
                        });
                        pushBot({ message: `최근 제출 내역:\n${lines.join("\n")}` });
                    }
                } catch {
                    pushBot({ message: "제출 현황을 불러오지 못했습니다. 잠시 후 다시 시도해주세요." });
                }
                return;
            }
            if (text === "다른 서류 제출하기") {
                resetToDeptSelect();
                return;
            }
            if (text === "챗봇 종료하기") {
                pushBot(SCENARIOS.END_CHATBOT);
                return;
            }

            // 부서 선택
            const dept = (departments || []).find(
                (d) =>
                    d.name === text ||
                    `${d.leftLabel ?? ""}${d.leftLabel ? "|" : ""}${d.name}` === text
            );
            if (dept) {
                setSelectedDeptId(dept.id);
                pushBot({ message: `"${dept.name}" 부서 선택됨. 제출하실 서류 유형을 선택해주세요.` });
                try {
                    const typesRes = await getDocTypesByDepartmentPublic(dept.id);
                    const normTypes = (typesRes || []).map(normDocType).filter((t) => t.id && t.name);
                    setDocTypes(normTypes);
                    pushBot(SCENARIOS.SELECT_TYPE(normTypes));
                } catch (e) {
                    console.error("[SELECT_DEPT] doc types load error:", e);
                    pushBot(SCENARIOS.SERVER_ERROR);
                }
                return;
            }

            // 문서 유형 선택
            const dt = (docTypes || []).find((t) => t.name === text);
            if (dt) {
                setSelectedDocType({ id: dt.id, name: dt.name });

                // 1) 필수항목
                let requiredFields = [];
                try {
                    const fields = await getRequiredFields(dt.id);
                    requiredFields = (fields || []).map(normRequiredField);
                } catch (e) {
                    console.warn("[required-fields] load failed", e);
                }

                // 2) 마감일
                try {
                    const dl = await getDeadline(dt.id); // {deadline: "..."} | string | null
                    const deadlineStr = typeof dl === "string" ? dl : dl?.deadline;
                    setDeadlineInfo(deadlineStr ? { deadline: deadlineStr } : null);

                    if (deadlineStr && isExpired(deadlineStr)) {
                        pushBot(SCENARIOS.DEADLINE_EXPIRED(deadlineStr));
                        return; // 업로드 차단
                    }
                    if (deadlineStr) {
                        pushBot(SCENARIOS.DEADLINE_VALID(deadlineStr));
                    }
                } catch (e) {
                    console.warn("[deadline] fetch failed (ignored)", e);
                }

                // 3) 업로드 프롬프트
                pushBot(SCENARIOS.FORM_AND_FILE_PROMPT(requiredFields));
                return;
            }
        },
        [departments, docTypes, resetToDeptSelect, pushUser, pushBot]
    );

    const handleFileUpload = useCallback(
        async (file) => {
            if (!file) return;
            if (!selectedDocType?.id) {
                pushBot({ message: "문서 유형을 먼저 선택해주세요.", uploadEnabled: false });
                return;
            }
            if (deadlineInfo?.deadline && isExpired(deadlineInfo.deadline)) {
                pushBot(SCENARIOS.DEADLINE_EXPIRED(deadlineInfo.deadline));
                return;
            }

            // 기존 폴링 취소 플래그 초기화
            cancelledRef.current = false;
            progressMinuteRef.current = 0;

            // 업로드 진행
            pushUser(`📎 ${file.name}`);
            pushBot(SCENARIOS.FILE_UPLOADED_PROCESSING);

            try {
                const created = await createSubmission({
                    docTypeId: selectedDocType.id,
                    fieldsJson: "[]",
                    file,
                });

                const submissionId = created?.submissionId;
                if (!submissionId) throw new Error("submissionId 없음");

                // 24시간 폴링 + 5분마다 진행 메시지
                const summary = await pollUntilDone(getSubmissionSummary, submissionId, {
                    initialDelayMs: 2000,
                    stepMs: 3000,
                    // maxDelayMs: 30000,
                    timeoutMs: 24 * 60 * 60 * 1000,
                    isCancelled: () => cancelledRef.current,
                    onProgress: (elapsedMs, status) => {
                        const min = Math.floor(elapsedMs / 60000);
                        if (min > progressMinuteRef.current && min % 5 === 0) {
                            progressMinuteRef.current = min;
                            const label =
                                status === STATUS.BOT_REVIEW ? "OCR 검사 중" : status || "대기 중";
                            pushBot({
                                message: `처리 중입니다 (${min}분 경과, 현재 상태: ${label}).\n조금만 더 기다려 주세요!`,
                            });
                        }
                    },
                });

                if (!summary) throw new Error("결과 조회 실패");

                // 여전히 BOT_REVIEW면 안내 후 종료
                if (summary.status === STATUS.BOT_REVIEW) {
                    pushBot({
                        message:
                            "OCR 검사 중입니다. 시간이 조금 더 걸리고 있어요.\n'서류 제출 현황'에서 결과를 확인하세요.",
                    });
                    return;
                }

                // ⭐ 결과 문구: NEEDS_FIX와 REJECTED를 분리 처리
                if (summary.status === STATUS.NEEDS_FIX) {
                    const reasonLines = await fetchReviewReasons(submissionId);
                    const reasonText = reasonLines.length ? `\n- ${reasonLines.join("\n- ")}` : "(사유 미기재)";
                    pushBot(SCENARIOS.BOT_FEEDBACK_FAIL(reasonText));
                } else if (summary.status === STATUS.REJECTED) {
                    // 관리자 반려 상태인 경우, 반려 사유를 가져옵니다.
                    const rejectionDetail = await getSubmissionSummary(submissionId);
                    const memo = rejectionDetail?.admin?.decisionMemo || "반려 사유가 없습니다.";
                    const reasons = rejectionDetail?.admin?.fieldNotes?.map(n => n.comment) || [];
                    if (memo) reasons.push(memo);

                    pushBot(SCENARIOS.ADMIN_REJECTION_REASON(reasons));
                } else {
                    pushBot(SCENARIOS.BOT_FEEDBACK_PASS);
                }
            } catch (err) {
                const msg = pickErrorMessage(err, "업로드에 실패했습니다.");
                if (msg.includes("마감일")) {
                    pushBot(SCENARIOS.DEADLINE_EXPIRED(deadlineInfo?.deadline ?? "마감"));
                } else {
                    pushBot({ message: `자동 검토 실패: ${msg}` });
                }
            }
        },
        [selectedDocType, deadlineInfo, pushUser, pushBot]
    );

    return { chatHistory, handleUserInput, handleFileUpload };
}