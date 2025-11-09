// src/pages/SubmissionDetailPage.jsx
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import styles from './SubmissionDetailPage.module.css';
import {
    getSubmissionDetail,
    approveSubmission,
    rejectSubmission, downloadSubmissionFile,
} from '../../api/api';

// URL에서 퍼센트 인코딩된 파일명 대비 (백엔드가 fileName 주면 그걸 우선 사용)
const extractFileName = (url) => {
    if (!url) return '';
    const noQuery = url.split('?')[0];
    const last = noQuery.split(/[/\\]/).pop() || '';
    try { return decodeURIComponent(last); } catch { return last; }
};

const SubmissionDetailPage = () => {
    const navigate = useNavigate();
    const { id } = useParams(); // /submission/detail/:id

    const [loading, setLoading] = useState(true);
    const [loadErr, setLoadErr] = useState(null);
    const [detail, setDetail] = useState(null);

    // 화면 입력 상태
    const [status, setStatus] = useState('신규 등록'); // '신규 등록' | '반려 처리' | '승인 처리'
    const [rejectionReason, setRejectionReason] = useState('');

    // 모달
    const [showModal, setShowModal] = useState(false);
    const [showCancelModal, setShowCancelModal] = useState(false);

    // 상세 로드
    useEffect(() => {
        let mounted = true;
        (async () => {
            try {
                setLoading(true);
                setLoadErr(null);
                const data = await getSubmissionDetail(Number(id));
                if (!mounted) return;
                setDetail(data);
                // 현재 상태를 한국어 라벨로 맞춰서 초기표시 (선택)
                const current = (data?.status || 'SUBMITTED');
                if (current === 'APPROVED') setStatus('승인 처리');
                else if (current === 'REJECTED') setStatus('반려 처리');
                else setStatus('신규 등록');
            } catch (e) {
                if (mounted) setLoadErr(e);
            } finally {
                if (mounted) setLoading(false);
            }
        })();
        return () => { mounted = false; };
    }, [id]);

    const fileTitle = useMemo(() => {
        if (!detail) return '';
        return detail.fileName || extractFileName(detail.fileUrl);
    }, [detail]);

    const handleSubmit = () => setShowModal(true);
    const cancelSubmit = () => setShowModal(false);
    const handleCancel = () => setShowCancelModal(true);
    const cancelCancel = () => setShowCancelModal(false);

    const confirmSubmit = async () => {
        try {
            if (!detail) return;

            if (status === '승인 처리') {
                await approveSubmission(detail.submissionId);
                alert('승인 처리되었습니다.');
            } else if (status === '반려 처리') {
                await rejectSubmission(detail.submissionId, rejectionReason?.trim());
                alert('반려 처리되었습니다.');
            } else {
                // '신규 등록'이면 아무 것도 하지 않고 뒤로
                alert('변경 사항 없이 목록으로 돌아갑니다.');
            }
            navigate(-1);
        } catch (e) {
            const msg = e?.response?.data?.message || e?.message || '처리 중 오류가 발생했습니다.';
            alert(msg);
        } finally {
            setShowModal(false);
        }
    };

    const confirmCancel = () => {
        setShowCancelModal(false);
        navigate(-1);
    };

    if (loading) {
        return <div className={styles.container}><div className={styles.loading}>불러오는 중…</div></div>;
    }
    if (loadErr || !detail) {
        return <div className={styles.container}>상세를 불러오지 못했습니다: {String(loadErr)}</div>;
    }

    const handleDownload = async () => {
        try {
            const { blob, filename } = await downloadSubmissionFile(detail.submissionId);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } catch (e) {
            alert('파일 다운로드 중 오류가 발생했습니다.');
        }
    };
    return (
        <div className={styles.detailContainer}>
            <div className={styles.pageTitle}>제출 서류 관리 (상세)</div>
            <div className={styles.divider} />

            {/* 기본 정보 */}
            <div className={styles.infoRow}>
                <div className={styles.infoItem}>
                    <label>이름</label>
                    <input value={detail.studentName || ''} disabled />
                </div>
                <div className={styles.infoItem}>
                    <label>학번</label>
                    <input value={detail.memberId || ''} disabled />
                </div>
                <div className={styles.infoItem}>
                    <label>제출 일시</label>
                    <input value={(detail.submittedAt || '').replace('T', ' ')} disabled />
                </div>
            </div>

            <div className={styles.infoRow}>
                <div className={styles.infoItem}>
                    <label>서류 유형</label>
                    <input value={detail.docTypeName || ''} disabled />
                </div>
                <div className={`${styles.fileItem} ${styles.longInput}`}>
                    <label>서류 확인</label>
                    <div className={styles.fileRow}>
                        <input value={fileTitle || ''} disabled />
                        {detail.fileUrl ? (
                            <button
                                className={styles.linkBtn}
                                onClick={handleDownload}
                            >
                                다운로드
                            </button>
                        ) : null}
                    </div>
                </div>
            </div>

            {/* 1차/전체 이력 표시 (있으면) */}
            {Array.isArray(detail.history) && detail.history.length > 0 && (
                <div className={styles.checkSection}>
                    <label>이력</label>
                    <div className={styles.checkBox}>
                        {detail.history.map(h => (
                            <div key={h.historyId}>
                                <b>[{h.action}]</b> {h.memo || ''} <span className={styles.muted}>({h.changedAt || ''})</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            <div className={styles.divider} />

            {/* 최종 승인/반려 */}
            <div className={styles.section}>
                <label>관리자 최종 승인</label>
                <div className={styles.radioGroup}>
                    {['신규 등록', '반려 처리', '승인 처리'].map((opt) => (
                        <label key={opt}>
                            <input
                                type="radio"
                                name="status"
                                value={opt}
                                checked={status === opt}
                                onChange={() => setStatus(opt)}
                            />
                            {opt}
                        </label>
                    ))}
                </div>

                {status === '반려 처리' && (
                    <div className={styles.rejectBox}>
                        <label>관리자 반려 사유</label>
                        <textarea
                            rows={3}
                            value={rejectionReason}
                            onChange={(e) => setRejectionReason(e.target.value)}
                            placeholder="반려 사유를 입력하세요."
                        />
                    </div>
                )}
            </div>

            {/* 버튼 */}
            <div className={styles.buttonGroup}>
                <button className={styles.submitBtn} onClick={handleSubmit}>등록</button>
                <button className={styles.cancelBtn} onClick={handleCancel}>취소</button>
            </div>

            {/* 등록 확인 모달 */}
            {showModal && (
                <div className={styles.modalOverlay}>
                    <div className={styles.modal}>
                        <p>등록하시겠습니까?</p>
                        <div className={styles.modalButtons}>
                            <button className={styles.confirm} onClick={confirmSubmit}>확인</button>
                            <button className={styles.dismiss} onClick={cancelSubmit}>취소</button>
                        </div>
                    </div>
                </div>
            )}

            {/* 취소 확인 모달 */}
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

export default SubmissionDetailPage;
