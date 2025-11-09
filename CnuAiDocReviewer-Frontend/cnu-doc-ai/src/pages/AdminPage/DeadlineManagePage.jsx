// src/pages/DeadlineManagePage.jsx
import React, { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import styles from "./DeadlineManagePage.module.css";

import useDepartments, { formatDeptLabel } from "../../hooks/useDepartments";
import {
    getDocTypesByDepartment,
    getDeadlineByDepartment,
    upsertDeadline,
    deleteDeadline,
} from "../../api/api";

const pageSize = 10;

const DeadlineManagePage = () => {
    const location = useLocation();
    const searchParams = new URLSearchParams(location.search);

    // 부서 목록 로드
    const { departments, loading: deptLoading, error: deptError } = useDepartments();

    // 선택 부서 ID: ?category=부서ID (없으면 첫 부서)
    const categoryParam = searchParams.get("category");
    const currentDeptId = useMemo(() => {
        if (categoryParam) return String(categoryParam);
        if (departments.length > 0) return String(departments[0].id);
        return "";
    }, [categoryParam, departments]);

    // 선택 부서 라벨
    const selectedDeptLabel = useMemo(() => {
        const dept = departments.find((d) => String(d.id) === String(currentDeptId));
        return dept ? formatDeptLabel(dept) : "";
    }, [departments, currentDeptId]);

    // 테이블 데이터
    // rows: { id: docTypeId, name: title, deadline: 'YYYY-MM-DD' | '' }
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(false);
    const [loadError, setLoadError] = useState(null);

    const [searchTerm, setSearchTerm] = useState("");
    const [page, setPage] = useState(1);

    // 부서 변경 시 데이터 로드
    useEffect(() => {
        if (!currentDeptId) return;
        let mounted = true;

        (async () => {
            setLoading(true);
            setLoadError(null);
            try {
                const [docs, deadlines] = await Promise.all([
                    getDocTypesByDepartment(Number(currentDeptId)),
                    getDeadlineByDepartment(Number(currentDeptId)),
                ]);

                const dlMap = new Map((deadlines ?? []).map((d) => [d.docTypeId, d.deadline || ""]));

                const merged = (docs ?? []).map((d) => ({
                    id: d.docTypeId ?? d.id ?? d.documentId, // 방어적 매핑
                    name: d.title ?? d.name ?? "",
                    deadline: dlMap.get(d.docTypeId ?? d.id ?? d.documentId) || "",
                }));

                if (mounted) {
                    setRows(merged);
                    setPage(1);
                }
            } catch (e) {
                if (mounted) setLoadError(e);
                console.error(e);
            } finally {
                if (mounted) setLoading(false);
            }
        })();

        return () => {
            mounted = false;
        };
    }, [currentDeptId]);

    // 검색/페이징
    const filtered = useMemo(() => {
        const term = (searchTerm || "").toLowerCase();
        if (!term) return rows;
        return rows.filter((r) => (r.name || "").toLowerCase().includes(term));
    }, [rows, searchTerm]);

    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
    const paged = useMemo(() => {
        const start = (page - 1) * pageSize;
        return filtered.slice(start, start + pageSize);
    }, [filtered, page]);

    const todayYMD = useMemo(() => {
        const d = new Date(); // 브라우저 로컬(한국이면 KST)
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}`; // "YYYY-MM-DD"
    }, []);

    // 핸들러
    const handleDateChange = (id, date) => {
        setRows((prev) => prev.map((r) => (r.id === id ? { ...r, deadline: date } : r)));
    };

    // handleRegister
    const handleRegister = async (id, date) => {
        try {
            if (!date) {
                alert("마감일을 선택하세요.");
                return;
            }
            // 오늘보다 이전이면 막기
            if (date < todayYMD) {
                alert(`오늘(${todayYMD})보다 이전 날짜는 등록할 수 없습니다.`);
                return;
            }
            await upsertDeadline({ docTypeId: id, deadline: date });
            alert("마감일이 등록/수정되었습니다.");
        } catch (e) {
            console.error(e);
            alert("등록/수정 중 오류가 발생했습니다.");
        }
    };

    const handleCancel = async (id) => {
        try {
            await deleteDeadline(id);
            setRows((prev) => prev.map((r) => (r.id === id ? { ...r, deadline: "" } : r)));
            alert("마감일이 삭제되었습니다.");
        } catch (e) {
            console.error(e);
            alert("삭제 중 오류가 발생했습니다.");
        }
    };

    // 렌더
    if (deptLoading || loading) {
        return (
            <div className={styles.page}>
                <div className={styles.loading}>불러오는 중…</div>
            </div>
        );
    }
    if (deptError || loadError) {
        return (
            <div className={styles.page}>
                <div className={styles.error}>
                    데이터 로딩 실패: {String(deptError || loadError)}
                </div>
            </div>
        );
    }

    return (
        <div className={styles.page}>
            <div className={styles.documentSection}>
                <h2 className={styles.pageTitle}>서류 유형 관리 (마감일 관리)</h2>
                <h3 className={styles.selectedDept}>▶ {selectedDeptLabel || `부서 ID: ${currentDeptId}`}</h3>

                <div className={styles.searchBox}>
                    <input
                        placeholder="서류명을 검색하세요"
                        value={searchTerm}
                        onChange={(e) => {
                            setSearchTerm(e.target.value);
                            setPage(1);
                        }}
                    />
                </div>

                <table className={styles.table}>
                    <thead>
                    <tr>
                        <th>서류명</th>
                        <th>현재 마감일</th>
                        <th>마감일 설정</th>
                    </tr>
                    </thead>
                    <tbody>
                    {paged.map((doc) => (
                        <tr key={doc.id}>
                            <td>{doc.name}</td>
                            <td>{doc.deadline || "없음"}</td>
                            <td className={styles.deadlineCell}>
                                <div className={styles.dateControl}>
                                    <input
                                        type="date"
                                        value={doc.deadline || ""}
                                        onChange={(e) => handleDateChange(doc.id, e.target.value)}
                                        className={styles.dateInput}
                                        min={todayYMD}
                                    />
                                    <div className={styles.buttonGroup}>
                                        <button
                                            onClick={() => handleRegister(doc.id, doc.deadline)}
                                            className={styles.submitBtn}
                                        >
                                            등록
                                        </button>
                                        <button
                                            onClick={() => handleCancel(doc.id)}
                                            className={styles.cancelBtn}
                                        >
                                            삭제
                                        </button>
                                    </div>
                                </div>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>

                <div className={styles.pagination}>
                    {Array.from({ length: totalPages }, (_, idx) => (
                        <button
                            key={idx}
                            onClick={() => setPage(idx + 1)}
                            className={page === idx + 1 ? styles.active : ""}
                        >
                            {idx + 1}
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default DeadlineManagePage;
