// src/pages/RequiredFieldListPage.jsx
import React, { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './RequiredFieldListPage.module.css';

import useDepartments, { formatDeptLabel } from '../../hooks/useDepartments';
import { getDocTypesByDepartment } from '../../api/api';

const pageSize = 10;

const RequiredFieldListPage = () => {
    const navigate = useNavigate();
    const location = useLocation();

    // 부서 목록 훅 (DB 연동)
    const { departments, loading: deptLoading, error: deptError } = useDepartments();

    // 현재 선택된 부서 id (쿼리에 없으면 첫 부서 id)
    const searchParams = new URLSearchParams(location.search);
    const categoryParam = searchParams.get('category');
    const currentDeptId = useMemo(() => {
        if (categoryParam) return String(categoryParam);
        if (departments.length > 0) return String(departments[0].id);
        return '';
    }, [categoryParam, departments]);

    // 선택된 부서 라벨 (ex. "학생 | 학생과")
    const selectedDeptLabel = useMemo(() => {
        const dept = departments.find(d => String(d.id) === String(currentDeptId));
        return dept ? formatDeptLabel(dept) : '';
    }, [departments, currentDeptId]);

    // 문서 목록 상태
    const [documents, setDocuments] = useState([]);
    const [docsLoading, setDocsLoading] = useState(false);
    const [docsError, setDocsError] = useState(null);

    // UI 상태
    const [searchTerm, setSearchTerm] = useState('');
    const [page, setPage] = useState(1);
    const [previewIndex, setPreviewIndex] = useState(null);

    // 부서별 문서 목록 로드
    useEffect(() => {
        if (!currentDeptId) return;
        let mounted = true;

        (async () => {
            setDocsLoading(true);
            setDocsError(null);
            try {
                const list = await getDocTypesByDepartment(Number(currentDeptId));
                // API 스키마 방어적으로 매핑
                const normalized = (list ?? []).map(item => {
                    const id = item.id ?? item.docTypeId ?? item.documentId;
                    const title = item.title ?? item.name ?? '';
                    // requiredFields: string[] 혹은 DTO 배열일 수 있어 방어적 처리
                    let fields = [];
                    if (Array.isArray(item.requiredFields)) {
                        fields = item.requiredFields;
                    } else if (Array.isArray(item.requiredFieldDtos)) {
                        fields = item.requiredFieldDtos.map(f => f.name ?? f.fieldName ?? '').filter(Boolean);
                    }
                    return { id, title, requiredFields: fields };
                });
                if (mounted) {
                    setDocuments(normalized);
                    setPage(1); // 부서 변경 시 페이지 리셋
                    setPreviewIndex(null);
                }
            } catch (e) {
                if (mounted) setDocsError(e);
                console.error(e);
            } finally {
                if (mounted) setDocsLoading(false);
            }
        })();

        return () => { mounted = false; };
    }, [currentDeptId]);

    // 검색/페이징
    const filteredDocs = useMemo(() => {
        const term = searchTerm.trim().toLowerCase();
        if (!term) return documents;
        return documents.filter(doc => (doc.title ?? '').toLowerCase().includes(term));
    }, [documents, searchTerm]);

    const totalPages = Math.ceil(filteredDocs.length / pageSize) || 1;
    const paged = useMemo(() => {
        const start = (page - 1) * pageSize;
        return filteredDocs.slice(start, start + pageSize);
    }, [filteredDocs, page]);

    const handlePreviewToggle = (id) => {
        setPreviewIndex(prev => (prev === id ? null : id));
    };

    const handleManageClick = (doc) => {
        // title은 쿼리로 넘겨 UI 상단에 표시용
        const q = new URLSearchParams({
            category: currentDeptId,
            title: doc.title ?? '',
        }).toString();
        navigate(`/admin/required/edit/${doc.id}?${q}`);
    };

    // 로딩/에러 처리
    if (deptLoading) {
        return <div className={styles.page}><div className={styles.loading}>부서 정보를 불러오는 중…</div></div>;
    }
    if (deptError) {
        return <div className={styles.page}><div className={styles.error}>부서 정보를 불러오지 못했습니다.</div></div>;
    }
    if (!currentDeptId) {
        return <div className={styles.page}><div className={styles.empty}>등록된 부서가 없습니다.</div></div>;
    }

    return (
        <div className={styles.page}>
            <div className={styles.documentSection}>
                <h2 className={styles.pageTitle}>서류 유형 관리 ( 서류 관리 )</h2>
                <h3 className={styles.selectedDept}>▶ {selectedDeptLabel}</h3>

                <div className={styles.searchBox}>
                    <input
                        placeholder="서류명을 검색하세요"
                        value={searchTerm}
                        onChange={e => { setSearchTerm(e.target.value); setPage(1); }}
                    />
                </div>

                <div className={styles.topBar}>
                    <button
                        className={styles.addBtn}
                        onClick={() => navigate(`/admin/required/add?category=${currentDeptId}`)}
                    >
                        서류 유형 추가
                    </button>
                </div>

                {docsLoading ? (
                    <div className={styles.loading}>서류 목록을 불러오는 중…</div>
                ) : docsError ? (
                    <div className={styles.error}>서류 목록을 불러오지 못했습니다.</div>
                ) : (
                    <>
                        <table className={styles.table}>
                            <thead>
                            <tr>
                                <th>서류명</th>
                                <th>현재 설정된 필수 항목</th>
                                <th>서류 관리</th>
                            </tr>
                            </thead>
                            <tbody>
                            {paged.length === 0 ? (
                                <tr><td colSpan={3} className={styles.empty}>표시할 서류가 없습니다.</td></tr>
                            ) : (
                                paged.map((doc) => (
                                    <tr key={doc.id}>
                                        <td>{doc.title}</td>
                                        <td className={styles.previewCell}>
                                            <button onClick={() => handlePreviewToggle(doc.id)} className={styles.viewBtn}>보기</button>
                                            {previewIndex === doc.id && (
                                                <div className={styles.tooltip}>
                                                    {doc.requiredFields?.length > 0 ? (
                                                        <div>
                                                            {doc.requiredFields.map((field, i) => (
                                                                <div key={i}>- {field}</div>
                                                            ))}
                                                        </div>
                                                    ) : (
                                                        <div>설정된 항목 없음</div>
                                                    )}
                                                </div>
                                            )}
                                        </td>
                                        <td>
                                            <button onClick={() => handleManageClick(doc)}>관리</button>
                                        </td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>

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
                    </>
                )}
            </div>
        </div>
    );
};

export default RequiredFieldListPage;
