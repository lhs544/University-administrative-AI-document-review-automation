// src/pages/AddRequiredDocumentPage.jsx
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import styles from './AddRequiredDocumentPage.module.css';
import { createDocType, getDocTypeForEdit, updateDocType } from '../../api/api';

const AddRequiredDocumentPage = () => {
    const navigate = useNavigate();
    const { documentId } = useParams();
    const [searchParams] = useSearchParams();

    // 쿼리
    const category = searchParams.get('category'); // = departmentId 로 사용
    const titleQuery = searchParams.get('title') || '';

    const isEditMode = !!documentId;

    // 폼 상태
    const [docName, setDocName] = useState(titleQuery);
    const [requiredFields, setRequiredFields] = useState([]); // [{name, example}]
    const [newFieldName, setNewFieldName] = useState('');
    const [newExample, setNewExample] = useState('');

    // 파일
    const fileRef = useRef(null);         // 실제 File 객체
    const [uploadedFileName, setUploadedFileName] = useState(''); // UI 표시용
    const extractFileName = (url) => {
        if (!url) return '';
        try {
            // 절대/상대 URL 모두 처리
            const u = new URL(url, window.location.origin);
            const pathname = u.pathname || '';
            const last = pathname.substring(pathname.lastIndexOf('/') + 1);
            return decodeURIComponent(last);
        } catch {
            // new URL 실패 시(특수 문자열) 수동 파싱
            const path = url.split('?')[0].split('#')[0];
            const last = path.substring(path.lastIndexOf('/') + 1);
            return decodeURIComponent(last);
        }
    };

    // 모달
    const [showSubmitModal, setShowSubmitModal] = useState(false);
    const [showCancelModal, setShowCancelModal] = useState(false);

    // 로딩/에러
    const [loading, setLoading] = useState(isEditMode);
    const [error, setError] = useState(null);

    // ─────────────────────────────────────
    // 수정모드: 초기값 세팅
    // ─────────────────────────────────────
    useEffect(() => {
        if (!isEditMode) return;
        let mounted = true;

        (async () => {
            setLoading(true);
            setError(null);
            try {
                const data = await getDocTypeForEdit(Number(documentId));
                // 응답 예시 가정:
                // {
                //   departmentId: 2,
                //   title: "장학금 신청서",
                //   requiredFields: ["이름", "학번", "신청 사유"],
                //   exampleValues: ["홍길동", "20231234", "경제적 사유"],
                //   fileName: "scholarship.hwp" // 있을 수도/없을 수도
                // }
                if (!mounted) return;

                const rf = Array.isArray(data.requiredFields) ? data.requiredFields : [];
                const ex = Array.isArray(data.exampleValues) ? data.exampleValues : [];
                const merged = rf.map((name, idx) => ({
                    name,
                    example: ex[idx] ?? ''
                }));

                setDocName(data.title ?? '');
                setRequiredFields(merged);
                const onlyName = extractFileName(data.fileUrl);
                setUploadedFileName(onlyName);
            } catch (e) {
                if (mounted) setError(e);
                console.error(e);
            } finally {
                if (mounted) setLoading(false);
            }
        })();

        return () => { mounted = false; };
    }, [isEditMode, documentId]);

    // ─────────────────────────────────────
    // 필드 추가/삭제
    // ─────────────────────────────────────
    const handleAddField = () => {
        if (!newFieldName.trim()) return;
        setRequiredFields(prev => [...prev, { name: newFieldName.trim(), example: newExample.trim() }]);
        setNewFieldName('');
        setNewExample('');
    };

    const handleDeleteField = (index) => {
        setRequiredFields(prev => prev.filter((_, i) => i !== index));
    };

    // ─────────────────────────────────────
    // 파일 업로드
    // ─────────────────────────────────────
    const handleFileChange = (e) => {
        const file = e.target.files?.[0];
        fileRef.current = file || null;
        setUploadedFileName(file ? file.name : '');
    };

    // ─────────────────────────────────────
    // 등록/취소 버튼 → 모달
    // ─────────────────────────────────────
    const handleSubmitClick = () => setShowSubmitModal(true);
    const handleCancelClick = () => setShowCancelModal(true);

    const confirmSubmit = () => {
        setShowSubmitModal(false);
        handleSubmit();
    };
    const cancelSubmit = () => setShowSubmitModal(false);

    const confirmCancel = () => {
        setShowCancelModal(false);
        navigate(-1);
    };
    const cancelCancel = () => setShowCancelModal(false);

    // ─────────────────────────────────────
    // 제출 처리
    // ─────────────────────────────────────
    const handleSubmit = async () => {
        // UI 상태 → API payload 변환
        const names = requiredFields.map(f => f.name?.trim()).filter(Boolean);
        const examples = requiredFields.map(f => f.example ?? '');

        try {
            if (isEditMode) {
                await updateDocType(Number(documentId), {
                    title: docName.trim(),
                    requiredFields: names,
                    exampleValues: examples,
                    file: fileRef.current || undefined, // 없으면 파일 변경 없음
                });
                alert('수정 완료');
            } else {
                if (!category) {
                    alert('부서 정보(category)가 없습니다.');
                    return;
                }
                if (!docName.trim()) {
                    alert('서류명을 입력하세요.');
                    return;
                }
                await createDocType({
                    departmentId: Number(category),
                    title: docName.trim(),
                    requiredFields: names,
                    exampleValues: examples,
                    file: fileRef.current || undefined,
                });
                alert('등록 완료');
            }
            navigate(`/admin/required?category=${category || ''}`);
        } catch (e) {
            console.error(e);
            alert('처리 중 오류가 발생했습니다.');
        }
    };

    // ─────────────────────────────────────
    // Render
    // ─────────────────────────────────────
    if (loading) {
        return <div className={styles.page}><div className={styles.loading}>불러오는 중…</div></div>;
    }
    if (error) {
        return <div className={styles.page}><div className={styles.error}>데이터를 불러오지 못했습니다.</div></div>;
    }

    return (
        <div className={styles.page}>
            <h2>필수 항목 관리 / {isEditMode ? docName : '신규 서류 등록'}</h2>

            {/* ── 서류명 ── */}
            <div className={styles.formGroup}>
                <label>서류명</label>
                <input
                    type="text"
                    value={docName}
                    onChange={(e) => setDocName(e.target.value)}
                    maxLength={100}
                />
            </div>

            {/* ── 파일 업로드 ── */}
            <div className={styles.formGroup}>
                <label>서류 파일</label>
                <div className={styles.fileRow}>
                    <input type="text" value={uploadedFileName} placeholder="선택된 파일 없음" disabled />
                    <label className={styles.uploadBtn}>
                        파일 업로드
                        <input type="file" onChange={handleFileChange} hidden />
                    </label>
                </div>
                {isEditMode && (
                    <p className={styles.helpText}>※ 새 파일을 업로드하면 기존 파일을 대체합니다.</p>
                )}
            </div>

            {/* ── 필수 항목 리스트 ── */}
            <div className={styles.formGroup}>
                <label>현재 설정된 항목</label>
                {requiredFields.length > 0 ? (
                    <ul className={styles.fieldList}>
                        {requiredFields.map((field, idx) => (
                            <li key={idx} className={styles.fieldItem}>
                <span>
                  - {field.name}
                    {field.example && ` (예: ${field.example})`}
                </span>
                                <button onClick={() => handleDeleteField(idx)}>삭제</button>
                            </li>
                        ))}
                    </ul>
                ) : (
                    <p className={styles.noneText}>항목이 없습니다.</p>
                )}
            </div>

            {/* ── 항목 추가 ── */}
            <div className={styles.formRow}>
                <input
                    placeholder="필수 항목 명칭"
                    value={newFieldName}
                    onChange={(e) => setNewFieldName(e.target.value)}
                />
                <input
                    placeholder="예시 답안"
                    value={newExample}
                    onChange={(e) => setNewExample(e.target.value)}
                />
                <button onClick={handleAddField}>항목 추가</button>
            </div>

            {/* ── 버튼 ── */}
            <div className={styles.buttonGroup}>
                <button className={styles.submitBtn} onClick={handleSubmitClick}>
                    {isEditMode ? '수정' : '등록'}
                </button>
                <button className={styles.cancelBtn} onClick={handleCancelClick}>취소</button>
            </div>

            {/* ── 등록/수정 확인 모달 ── */}
            {showSubmitModal && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <p>{isEditMode ? '수정하시겠습니까?' : '등록하시겠습니까?'}</p>
                        <div className={styles.modalButtons}>
                            <button className={styles.confirm} onClick={confirmSubmit}>확인</button>
                            <button className={styles.dismiss} onClick={cancelSubmit}>취소</button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── 취소 확인 모달 ── */}
            {showCancelModal && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <p>취소하시겠습니까?</p>
                        <div className={styles.modalButtons}>
                            <button className={styles.confirm} onClick={confirmCancel}>확인</button>
                            <button className={styles.dismiss} onClick={cancelCancel}>취소</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AddRequiredDocumentPage;
