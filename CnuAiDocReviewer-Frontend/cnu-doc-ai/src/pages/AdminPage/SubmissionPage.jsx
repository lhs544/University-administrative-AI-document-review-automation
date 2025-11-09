// src/pages/SubmissionPage.jsx
import React, { useState, useEffect, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './SubmissionPage.module.css';
import useDepartments, { formatDeptLabel } from '../../hooks/useDepartments';

// ★ API 함수 import
import { listAdminQueue, getSubmissionDetail } from '../../api/api';

// 상태 → 한글 라벨 (보조용)
const statusToLabel = (status) => {
    switch (status) {
        case 'APPROVED': return '승인 완료';
        case 'REJECTED': return '반려 처리';
        case 'SUBMITTED':
        case 'UNDER_REVIEW':
        default: return '신규 등록';
    }
};

// 승인여부(Y/N)
const statusToApproved = (status) => (status === 'APPROVED' ? 'Y' : 'N');

// 파일명 추출(혹시 detail에 fileName이 없을 때 대비)
const extractFileName = (url) => {
    if (!url) return '';
    const noQuery = url.split('?')[0];
    const last = noQuery.split(/[/\\]/).pop() || '';
    try { return decodeURIComponent(last); } catch { return last; }
};


const SubmissionPage = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const searchParams = new URLSearchParams(location.search);

    // ───────────────── 부서 선택: ?category=부서ID ─────────────────
    const { departments, loading: deptLoading, error: deptError } = useDepartments();
    const categoryParam = searchParams.get('category');

    // 현재 선택된 부서ID(문자열). 쿼리파라미터가 없으면 첫 부서로.
    const currentDeptId = useMemo(() => {
        if (categoryParam) return String(categoryParam);
        if (departments.length > 0) return String(departments[0].id);
        return '';
    }, [categoryParam, departments]);

    // 선택 부서 라벨
    const selectedDeptLabel = useMemo(() => {
        const dept = departments.find(d => String(d.id) === String(currentDeptId));
        return dept ? formatDeptLabel(dept) : '';
    }, [departments, currentDeptId]);

    // ───────────────── 테이블/필터 상태 ─────────────────
    const [page, setPage] = useState(1);
    const [searchField, setSearchField] = useState('fileTitle');
    const [search, setSearch] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');
    const [typeFilter, setTypeFilter] = useState('전체');     // 라디오 하나만 선택
    const [approvedFilter, setApprovedFilter] = useState(null); // 'Y' | 'N' | null
    const [selectedRowId, setSelectedRowId] = useState(null);

    const [rows, setRows] = useState([]);   // 실제 데이터
    const [loading, setLoading] = useState(true);

    // ───────────────── 데이터 로드: 부서별 관리자 큐 ─────────────────
    useEffect(() => {
        let cancelled = false;
        async function fetchData() {
            try {
                setLoading(true);
                setRows([]);
                if (!currentDeptId) return; // 부서 없으면 빈 목록 유지

                const list = await listAdminQueue(Number(currentDeptId));
                const enriched = await Promise.all(
                    (list ?? []).map(async (s) => {
                        try {
                            const d = await getSubmissionDetail(s.submissionId);
                            return {
                                id: s.submissionId,
                                name: d?.studentName ?? '',
                                memberId: d?.memberId ?? '',
                                fileTitle: d?.fileName ?? extractFileName(s.fileUrl),
                                date: (s?.submittedAt ?? '').slice(0, 10),
                                status: s?.status ?? 'SUBMITTED',
                                type: statusToLabel(s?.status ?? 'SUBMITTED'),
                                approved: statusToApproved(s?.status ?? 'SUBMITTED'),
                            };
                        } catch {
                            return {
                                id: s.submissionId,
                                name: '',
                                memberId: '',
                                fileTitle: extractFileName(s.fileUrl),
                                date: (s?.submittedAt ?? '').slice(0, 10),
                                status: s?.status ?? 'SUBMITTED',
                                type: statusToLabel(s?.status ?? 'SUBMITTED'),
                                approved: statusToApproved(s?.status ?? 'SUBMITTED'),
                            };
                        }
                    })
                );
                if (!cancelled) {
                    setRows(enriched);
                    setPage(1); // 부서 바뀌면 페이지 초기화
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        }
        fetchData();
        return () => { cancelled = true; };
    }, [currentDeptId]);

    // ───────────────── 필터링 ─────────────────
    const filtered = useMemo(() => {
        return rows.filter((item) => {
            // 검색
            const fieldValue = (item[searchField] ?? '').toString();
            const fieldMatch = fieldValue.includes((search ?? '').trim());

            // 구분(라디오): 전체 | 신규 등록 | 반려 처리 | 승인 완료
            const typeMatch = (typeFilter === '전체') || (item.type === typeFilter);

            // 승인(Y/N/전체)
            const approvedMatch = (approvedFilter === null) || (item.approved === approvedFilter);

            // 날짜
            const dateMatch =
                (!startDate || item.date >= startDate) &&
                (!endDate || item.date <= endDate);

            return fieldMatch && typeMatch && approvedMatch && dateMatch;
        });
    }, [rows, searchField, search, typeFilter, approvedFilter, startDate, endDate]);

    const pageSize = 10;
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const paged = filtered.slice((page - 1) * pageSize, page * pageSize);

    // 필터 변경 시 1페이지로
    useEffect(() => { setPage(1); }, [search, typeFilter, approvedFilter, startDate, endDate]);

    // 행 클릭 → 상세 페이지 이동
    const handleRowClick = (doc) => {
        setSelectedRowId(doc.id);
        navigate(`/submission/detail/${doc.id}`);
    };

    // ───────────────── 렌더링 ─────────────────
    if (deptLoading) {
        return (
            <div className={styles.page}>
                <div className={styles.loading}>부서 불러오는 중…</div>
            </div>
        );
    }
    if (deptError) {
        return (
            <div className={styles.page}>
                <div className={styles.error}>부서 로드 실패: {String(deptError)}</div>
            </div>
        );
    }

    return (
        <div className={styles.page}>
            <div className={styles.documentSection}>
                <h2 className={styles.pageTitle}>제출 서류 관리 ( 행정 서비스 )</h2>
                <h3 className={styles.selectedDept}>▶ {selectedDeptLabel || `부서 ID: ${currentDeptId || '-'}`}</h3>

                {/* 필터 */}
                <div className={styles.filterContainer}>
                    <div className={styles.leftFilters}>
                        <div className={styles.filterGroup}>
                            <label>조건검색</label>
                            <select value={searchField} onChange={(e) => setSearchField(e.target.value)}>
                                <option value="fileTitle">서류 제목</option>
                                <option value="name">이름</option>
                            </select>
                            <input
                                type="text"
                                placeholder={searchField === 'fileTitle' ? '서류 제목' : '이름'}
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                            />
                        </div>

                        <div className={styles.filterGroup}>
                            <label>서류 제출일</label>
                            <div className={styles.dateRange}>
                                <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                                <span>~</span>
                                <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                            </div>
                        </div>
                    </div>

                    <div className={styles.rightFilters}>
                        <div className={styles.filterGroup}>
                            <label>서류 구분</label>
                            {['전체', '신규 등록', '반려 처리', '승인 완료'].map((t) => (
                                <label key={t} className={styles.checkboxLabel}>
                                    <input
                                        type="radio"
                                        name="docType"
                                        checked={typeFilter === t}
                                        onChange={() => setTypeFilter(t)}
                                    /> {t}
                                </label>
                            ))}
                        </div>

                        <div className={styles.filterGroup}>
                            <label>승인 항목</label>
                            <label>
                                <input
                                    type="radio"
                                    checked={approvedFilter === 'Y'}
                                    onChange={() => setApprovedFilter('Y')}
                                /> Y
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    checked={approvedFilter === 'N'}
                                    onChange={() => setApprovedFilter('N')}
                                /> N
                            </label>
                            <label>
                                <input
                                    type="radio"
                                    checked={approvedFilter === null}
                                    onChange={() => setApprovedFilter(null)}
                                /> 전체
                            </label>
                        </div>
                    </div>
                </div>

                {/* 테이블 */}
                <div className={styles.tableWrapper}>
                    {loading ? (
                        <div className={styles.loading}>불러오는 중…</div>
                    ) : (
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>번호</th>
                                <th>이름</th>
                                <th>학번</th>
                                <th>서류 제목</th>
                                <th>서류 제출 일자</th>
                                <th>구분</th>
                            </tr>
                            </thead>
                            <tbody>
                            {paged.map((doc, idx) => (
                                <tr
                                    key={doc.id}
                                    onClick={() => handleRowClick(doc)}
                                    className={selectedRowId === doc.id ? styles.selectedRow : ''}
                                >
                                    <td>{(page - 1) * pageSize + idx + 1}</td>
                                    <td>{doc.name}</td>
                                    <td>{doc.memberId}</td>
                                    <td>{doc.fileTitle}</td>
                                    <td>{doc.date}</td>
                                    <td className={doc.type === '승인 완료' ? styles.approved : ''}>{doc.type}</td>
                                </tr>
                            ))}
                            {paged.length === 0 && (
                                <tr><td colSpan={6} className={styles.empty}>표시할 항목이 없습니다.</td></tr>
                            )}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* 페이지네이션 */}
                <div className={styles.pagination}>
                    {Array.from({ length: totalPages }, (_, idx) => (
                        <button
                            key={idx}
                            onClick={() => setPage(idx + 1)}
                            className={page === idx + 1 ? styles.active : ''}
                        >
                            {idx + 1}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default SubmissionPage;
