// src/pages/AdminMain.jsx
import React, { useEffect, useMemo, useRef, useState } from 'react';
import styles from './AdminMain.module.css';
import useDepartments, { formatDeptLabel } from '../../hooks/useDepartments';
import {
    listAdminQueue,
    getDocTypesByDepartment,
    getDeadlineByDepartment,
} from '../../api/api';

// ---- 유틸/상수 --------------------------------------------------
const STATUSES_FOR_NEW = ['SUBMITTED', 'NEEDS_FIX','BOT_REVIEW']; // 신규 제출 정의

const fmtNowKST = () =>
    new Date().toLocaleString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        weekday: 'short',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }) + ' 기준';

const toDate = (ymd) => {
    if (!ymd) return null;
    const [y, m, d] = ymd.split('-').map(Number);
    if (!y || !m || !d) return null;
    return new Date(y, m - 1, d);
};

const parseTotalCount = (res) => {
    if (!res) return 0;
    if (Array.isArray(res)) return res.length;
    if (typeof res.totalElements === 'number') return res.totalElements;
    if (typeof res.total === 'number') return res.total;
    if (typeof res.count === 'number') return res.count;
    if (Array.isArray(res.content)) return res.content.length;
    return 0;
};

const calcDeadlineStatus = (deadlineYmd) => {
    const d = toDate(deadlineYmd);
    if (!d) return '기간 미설정';
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return d >= today ? '진행 중' : '마감 지남';
};

// ---- 컴포넌트 ---------------------------------------------------
const AdminMain = () => {
    // "페이지 표시 시점" 기준으로 고정된 타임스탬프
    const timestamp = useMemo(() => fmtNowKST(), []);
    const { departments, loading: deptLoading, error: deptError } = useDepartments();

    const [isLoading, setIsLoading] = useState(true);
    const [pageError, setPageError] = useState(null);
    const [submissionStatus, setSubmissionStatus] = useState([]);    // [{ departmentLabel, count }]
    const [submissionDeadlines, setSubmissionDeadlines] = useState([]); // [{ title, deadline, departmentLabel }]
    const unmountedRef = useRef(false);

    useEffect(() => {
        unmountedRef.current = false;

        const load = async () => {
            // 부서 정보 로딩/에러 처리
            if (deptLoading) {
                setIsLoading(true);
                return;
            }
            if (deptError) {
                setIsLoading(false);
                setPageError(deptError);
                return;
            }
            if (!departments || departments.length === 0) {
                setSubmissionStatus([]);
                setSubmissionDeadlines([]);
                setIsLoading(false);
                setPageError(null);
                return;
            }

            setIsLoading(true);
            setPageError(null);

            // ---- 1) 부서별 신규 제출 카운트 ----
            const statusResults = await Promise.allSettled(
                departments.map(async (dept) => {
                    const res = await listAdminQueue(Number(dept.id), STATUSES_FOR_NEW);
                    const count = parseTotalCount(res);
                    return { departmentLabel: formatDeptLabel(dept), count };
                })
            );

            const statusRows = [];
            const statusErrors = [];
            statusResults.forEach((r, idx) => {
                const label = formatDeptLabel(departments[idx]);
                if (r.status === 'fulfilled') {
                    statusRows.push(r.value);
                } else {
                    statusErrors.push(`[${label}] 큐 조회 실패: ${r.reason?.message || String(r.reason)}`);
                }
            });

            // ---- 2) 부서별 서류/마감 취합 ----
            const deadlineResults = await Promise.allSettled(
                departments.map(async (dept) => {
                    const [docs, dls] = await Promise.all([
                        getDocTypesByDepartment(Number(dept.id)),
                        getDeadlineByDepartment(Number(dept.id)),
                    ]);
                    const dlMap = new Map((dls || []).map((d) => [d.docTypeId, d.deadline || '']));
                    return (docs || []).map((doc) => ({
                        title: doc.title ?? doc.name ?? '',
                        deadline: dlMap.get(doc.docTypeId ?? doc.id ?? doc.documentId) || '',
                        departmentLabel: formatDeptLabel(dept),
                    }));
                })
            );

            const deadlineRows = [];
            const deadlineErrors = [];
            deadlineResults.forEach((r, idx) => {
                const label = formatDeptLabel(departments[idx]);
                if (r.status === 'fulfilled') {
                    deadlineRows.push(...(r.value || []));
                } else {
                    deadlineErrors.push(`[${label}] 마감 데이터 조회 실패: ${r.reason?.message || String(r.reason)}`);
                }
            });

            // 마감 정렬 (가까운 순)
            const flatDeadlines = deadlineRows
                .filter((r) => r.deadline)
                .sort((a, b) => {
                    const da = toDate(a.deadline);
                    const db = toDate(b.deadline);
                    return da && db ? da - db : da ? -1 : 1;
                });

            if (unmountedRef.current) return;

            setSubmissionStatus(statusRows);
            setSubmissionDeadlines(flatDeadlines);

            // 부분 에러를 묶어서 화면에 보여줌 (전체를 막지 않음)
            const mergedErrors = [...statusErrors, ...deadlineErrors];
            setPageError(mergedErrors.length ? mergedErrors.join('\n') : null);
            setIsLoading(false);
        };

        load();

        return () => {
            unmountedRef.current = true;
        };
    }, [departments, deptLoading, deptError]);

    // ---- 렌더링 ----------------------------------------------------
    if (isLoading) {
        return (
            <div className={styles.wrapper}>
                <div className={styles.container}>
                    <div className={styles.loading}>불러오는 중…</div>
                </div>
            </div>
        );
    }

    return (
        <div className={styles.wrapper}>
            <div className={styles.container}>
                {pageError && (
                    <div className={styles.error}>
                        데이터 일부 로드 실패:
                        <pre style={{ whiteSpace: 'pre-wrap', margin: '8px 0 0' }}>{String(pageError)}</pre>
                    </div>
                )}

                <section>
                    <h2 className={styles.title}>
                        서류 제출 현황 <span className={styles.timestamp}>{timestamp}</span>
                    </h2>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>행정부서</th>
                            <th className={styles.rightAlign}>신규 제출 서류</th>
                        </tr>
                        </thead>
                        <tbody>
                        {submissionStatus.length > 0 ? (
                            submissionStatus.map((item, idx) => (
                                <tr key={idx}>
                                    <td>{item.departmentLabel}</td>
                                    <td className={styles.rightAlign}>{item.count}건</td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan={2} className={styles.empty}>표시할 항목이 없습니다.</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </section>

                <section>
                    <h2 className={styles.title}>
                        서류 제출 마감 <span className={styles.timestamp}>{timestamp}</span>
                    </h2>
                    <table className={styles.table}>
                        <thead>
                        <tr>
                            <th>서류 제목</th>
                            <th>마감 기한</th>
                            <th>행정부서</th>
                            <th className={styles.rightAlign}>상태</th>
                        </tr>
                        </thead>
                        <tbody>
                        {submissionDeadlines.length > 0 ? (
                            submissionDeadlines.map((item, idx) => (
                                <tr key={idx}>
                                    <td>{item.title}</td>
                                    <td>{item.deadline}</td>
                                    <td>{item.departmentLabel}</td>
                                    <td className={styles.rightAlign}>{calcDeadlineStatus(item.deadline)}</td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan={4} className={styles.empty}>표시할 항목이 없습니다.</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </section>
            </div>
        </div>
    );
};

export default AdminMain;
