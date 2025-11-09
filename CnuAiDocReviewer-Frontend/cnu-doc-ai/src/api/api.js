// src/api/api.js
import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080',
    withCredentials: true,
});

/* ───────── 인증 ───────── */
export const login = async (memberId, password) => {
    const { data } = await api.post('/auth/login', { memberId, password });
    return data;
};
export const getMyInfo = async () => {
    const { data } = await api.get('/auth/me');
    return data;
};

/* ───────── 학생용 공개 API (챗봇이 사용) ───────── */
// 부서 목록
export const getStudentDepartments = async () => {
    const { data } = await api.get('/api/departments');
    return data; // [{id,name,phone}]
};
// 부서별 문서유형
export const getDocTypesByDepartmentPublic = async (departmentId) => {
    const { data } = await api.get(`/api/departments/${departmentId}/doc-types`);
    return data; // [{docTypeId,title,requiredFields,fileUrl}]
};
// 문서유형 필수항목
export const getRequiredFields = async (docTypeId) => {
    const { data } = await api.get(`/api/doc-types/${docTypeId}/required-fields`);
    return data; // ["이름","학번",...]
};
//  마감일
export const getDeadline = async (docTypeId) => {
    // 없으면 서버에서 404/204 줄 수 있음 → 호출부에서 널 처리
    const { data } = await api.get(`/api/doc-types/${docTypeId}/deadline`);
    return data; // 예: { deadline: "2025-08-21T23:59:59" } 혹은 문자열
};



/* ───────── 학생 제출 ───────── */
export const createSubmission = async ({ docTypeId, fieldsJson, file }) => {
    const form = new FormData();
    form.append('docTypeId', docTypeId);
    if (fieldsJson) form.append('fieldsJson', fieldsJson);
    form.append('file', file);

    const { data } = await api.post('/api/submissions', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data; // SubmissionSummaryDTO {submissionId,status,fileUrl,submittedAt}
};

// 상태/봇결과 폴링용
export const getBotReviewResult = async (submissionId) => {
    const { data } = await api.get(`/api/submissions/${submissionId}/review-result`);
    return data; // { verdict, findings:[{label,message}], reason? }
};

/* ───────── (참고) 내 제출 목록/상세 ───────── */
export const listMySubmissions = async ({ status, limit = 10 } = {}) => {
    const params = {};
     if (status) params.status = status;
     if (limit) params.limit = limit;
     const { data } = await api.get('/api/submissions/my', { params });
    return data;
};

/* ───────── 관리자 API (기존 화면 유지용) ───────── */
/* 이름을 명확히: Admin prefix */
export const getAdminDepartments = async () => {
    const { data } = await api.get('/api/admin/departments');
    return data;
};
export const getDepartment = async (id) => {
    const { data } = await api.get(`/api/admin/departments/${id}`);
    return data;
};
export const getAdminDocTypesByDepartment = async (departmentId) => {
    const { data } = await api.get('/api/admin/documents', { params: { departmentId } });
    return data;
};
export const getDocTypeForEdit = async (docTypeId) => {
    const { data } = await api.get(`/api/admin/documents/${docTypeId}`);
    return data;
};
export const updateDocType = async (docTypeId, { title, requiredFields = [], exampleValues = [], file }) => {
    const form = new FormData();
    if (title != null) form.append('title', title);
    requiredFields.forEach((v) => form.append('requiredFields', v));
    exampleValues.forEach((v) => form.append('exampleValues', v));
    if (file) form.append('file', file);

    const { data } = await api.put(`/api/admin/documents/${docTypeId}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
};
export const createDocType = async ({ departmentId, title, requiredFields = [], exampleValues = [], file }) => {
    const form = new FormData();
    form.append('departmentId', departmentId);
    form.append('title', title);
    requiredFields.forEach((v) => form.append('requiredFields', v));
    exampleValues.forEach((v) => form.append('exampleValues', v));
    if (file) form.append('file', file);
    const { data } = await api.post('/api/admin/documents', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
};
/* 마감일(관리자) */
export const getDeadlineByDepartment = async (departmentId) => {
    const { data } = await api.get('/api/admin/deadline', { params: { departmentId } });
    return data ?? [];
};
export const upsertDeadline = async ({ docTypeId, deadline }) => {
    await api.post('/api/admin/deadline', { docTypeId, deadline });
};
export const deleteDeadline = async (docTypeId) => {
    await api.delete(`/api/admin/deadline/${docTypeId}`);
};
/* 관리자 제출 큐/결정 */
// api.js (예시)
// src/api/api.js
export async function listAdminQueue(deptId, statuses = []) {
    const url = '/api/admin/submissions';
    const params = new URLSearchParams();
    params.set('departmentId', String(deptId));
    // ✅ brackets 없이 반복 파라미터로 직렬화
    statuses.forEach(s => params.append('statuses', s));

    const { data } = await api.get(`${url}?${params.toString()}`, {
        headers: { Accept: 'application/json' },
    });
    return data;
}

export async function getSubmissionDetail(id) {
    const subId = Number(id);
    if (Number.isNaN(subId)) throw new Error('submission id is not a valid number');
    const res = await api.get(`/api/admin/submissions/${subId}`);
    return res.data;
}
export const approveSubmission = async (id) => {
    const { data } = await api.post(`/api/admin/submissions/${id}/approve`);
    return data;
};
export const rejectSubmission = async (id, reason) => {
    const { data } = await api.post(
        `/api/admin/submissions/${id}/reject`,
        { memo: reason ?? '사유 미기재' },
        { headers: { 'Content-Type': 'application/json' } },
    );
    return data;
};
export const downloadDocFile = async (id) => {
    const res = await api.get(`/api/admin/submissions/${id}/file`, { responseType: 'blob' });
    const dispo = res.headers['content-disposition'] || '';
    const m = dispo.match(/filename\*?=([^;]+)/i);
    const filename = m ? decodeURIComponent(m[1].replace(/^UTF-8''/, '').trim()) : `submission-${id}`;
    return { blob: res.data, filename: filename.replace(/["']/g, '') };
};
// 학생: 제출 요약(상태 포함)
export const getSubmissionSummary = async (submissionId) => {
    const { data } = await api.get(`/api/submissions/${submissionId}`);
    return data; // SubmissionSummaryDTO { submissionId, status, fileUrl, submittedAt }
};

/* ───────── 공통 설정 ───────── */
api.defaults.timeout = 90000000;
api.interceptors.response.use(
    (res) => res,
    (err) => Promise.reject(err)
);
// === Legacy aliases for backward-compat ===
export const getDepartments = getAdminDepartments;                 // 기존 관리자용 이름 유지
export const getDocTypesByDepartment = getAdminDocTypesByDepartment; // 기존 관리자용 이름 유지
export const downloadSubmissionFile = downloadDocFile;             // 기존 이름 유지
export function pickErrorMessage(err, fallback = '요청에 실패했습니다.') {
    const r = err?.response;
    return (
        r?.data?.message ||
        r?.data?.detail ||
        r?.data ||
        err?.message ||
        fallback
    );
}

export default api;
