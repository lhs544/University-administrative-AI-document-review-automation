// src/pages/StudentMain.jsx
import React, { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import styles from './StudentMain.module.css';
import Chatbot from '../../components/Chatbot/Chatbot';
import useDepartments, { formatDeptLabel } from '../../hooks/useDepartments';
import { getMyInfo, getDocTypesByDepartment } from '../../api/api';

const toAbsUrl = (u) => {
    if (!u) return null;
    if (/^https?:\/\//i.test(u)) return u;
    // 상대경로일 때 API 호스트로 보정 (필요 시 환경변수로 교체)
    return `http://localhost:8080${u.startsWith('/') ? '' : '/'}${u}`;
};

const StudentMain = () => {
    const [userInfo, setUserInfo] = useState(null);
    const [isChatbotOpen, setIsChatbotOpen] = useState(false);

    // 부서 목록
    const { departments, loading: deptLoading, error: deptError } = useDepartments();

    // URL 쿼리에서 category 추출 → 쿼리 없으면 첫 부서 id로 폴백
    const location = useLocation();
    const searchParams = new URLSearchParams(location.search);
    const categoryParam = searchParams.get('category');

    const currentDeptId = useMemo(() => {
        if (categoryParam) return String(categoryParam);
        if (departments.length > 0) return String(departments[0].id);
        return '';
    }, [categoryParam, departments]);

    // 선택된 부서 라벨
    const selectedDeptLabel = useMemo(() => {
        const dept = departments.find(d => String(d.id) === String(currentDeptId));
        return dept ? formatDeptLabel(dept) : '';
    }, [departments, currentDeptId]);

    // 문서 목록
    const [documents, setDocuments] = useState([]);
    const [docsLoading, setDocsLoading] = useState(false);
    const [docsError, setDocsError] = useState(null);

    // 내 정보
    useEffect(() => {
        (async () => {
            try {
                const data = await getMyInfo();
                setUserInfo(data);
            } catch (err) {
                console.error('사용자 정보 불러오기 실패:', err);
            }
        })();
    }, []);

    // 부서 변경 시 문서 목록 로드 (단 하나의 useEffect만 유지)
    useEffect(() => {
        if (!currentDeptId) return;
        let mounted = true;

        (async () => {
            setDocsLoading(true);
            setDocsError(null);
            try {
                const list = await getDocTypesByDepartment(Number(currentDeptId));

                // 응답 스키마 방어적 정규화 (+ 파일 URL 보정)
                const normalized = (list ?? []).map(item => {
                    const id =
                        item.id ?? item.docTypeId ?? item.documentId ?? item.docId;

                    const title =
                        item.title ?? item.name ?? item.docTitle ?? '제목 없음';

                    let fields = [];
                    if (Array.isArray(item.requiredFields)) {
                        fields = item.requiredFields;
                    } else if (Array.isArray(item.requiredFieldDtos)) {
                        fields = item.requiredFieldDtos
                            .map(f => f.name ?? f.fieldName ?? '')
                            .filter(Boolean);
                    }

                    const fileUrl =
                        item.latestFileUrl ?? // 백엔드가 최신 파일 URL을 내려줄 경우
                        item.fileUrl ??
                        item.originalFileUrl ??
                        item.file?.url ??
                        item.originalFile?.fileUrl ??
                        null;

                    return { id, title, requiredFields: fields, fileUrl: toAbsUrl(fileUrl) };
                });

                if (mounted) setDocuments(normalized);
            } catch (e) {
                if (mounted) setDocsError(e);
                console.error(e);
            } finally {
                if (mounted) setDocsLoading(false);
            }
        })();

        return () => { mounted = false; };
    }, [currentDeptId]);

    if (!userInfo) return <div className={styles.loading}>로딩 중...</div>;
    if (deptError) return <div className={styles.loading}>부서 정보를 불러오지 못했습니다.</div>;

    const { name, memberId, department, academicStatus } = userInfo;
    const academicStatusLabel =
        academicStatus === 'ENROLLED' ? '재학중' :
            academicStatus === 'GRADUATED' ? '졸업' : '-';

    const renderDocumentList = () => {
        if (!currentDeptId) {
            return <div className={styles.placeholderText}>해당 부서를 선택해주세요.</div>;
        }

        return (
            <>
                <div className={styles.innerTitle}>
                    ▶ {selectedDeptLabel} 제출 서류
                </div>

                {docsLoading && <div className={styles.loadingSmall}>서류 목록을 불러오는 중…</div>}
                {docsError && <div className={styles.error}>서류 목록을 불러오지 못했습니다.</div>}
                {!docsLoading && !docsError && documents.length === 0 && (
                    <div className={styles.placeholderText}>표시할 서류가 없습니다.</div>
                )}

                {!docsLoading && !docsError && documents.map(doc => {
                    const fieldsText =
                        (doc.requiredFields ?? []).length > 0
                            ? doc.requiredFields.join(', ')
                            : '설정된 필수 항목 없음';

                    return (
                        <div key={doc.id} className={styles.documentGroup}>
                            <div className={styles.titleLine}>{doc.title}</div>
                            <div className={styles.detailLine}>필수 항목: {fieldsText}</div>

                            <div className={styles.actionsRow}>
                                {doc.fileUrl ? (
                                    <a
                                        className={styles.downloadBtn}
                                        href={doc.fileUrl}
                                        download
                                        target="_blank"
                                        rel="noreferrer"
                                        title="원본 서류 다운로드"
                                    >
                                        서류 다운로드
                                    </a>
                                ) : (
                                    <button className={styles.downloadBtnDisabled} disabled>
                                        서류 파일 없음
                                    </button>
                                )}
                            </div>
                        </div>
                    );
                })}
            </>
        );
    };

    return (
        <div className={styles.mainContent}>
            <div className={styles.contentWrapper}>
                <section className={styles.profileCard}>
                    <div className={styles.headerRow}>
                        <img src="/images/logo.png" alt="프로필" className={styles.logo} />
                        <div className={styles.userInfoBox}>
                            <div className={styles.name}>{name}</div>
                            <div className={styles.info}>
                                {department}({academicStatusLabel}) | {memberId}
                            </div>
                        </div>
                    </div>

                    <div className={styles.sectionTitle}>제출 지원 서류 목록</div>
                    <div className={styles.innerContent}>{renderDocumentList()}</div>
                </section>

                <section className={styles.chatbotSection}>
                    <div className={styles.chatbotContainer}>
                        <div className={styles.chatbotText}>
                            행정 지원 시스템은<br />
                            <strong>CNU 챗봇</strong>과 함께합니다 !
                        </div>
                        <img src="/images/mascot.png" alt="챗봇 마스코트" className={styles.chatbotImage} />
                        <button
                            className={styles.chatbotButton}
                            onClick={() => setIsChatbotOpen(true)}
                        >
                            CNU 챗봇
                        </button>
                    </div>
                </section>
            </div>

            {isChatbotOpen && <Chatbot onClose={() => setIsChatbotOpen(false)} />}
        </div>
    );
};

export default StudentMain;
