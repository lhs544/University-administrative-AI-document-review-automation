import { useEffect, useState } from 'react';
import { getDepartments } from '../api/api';

// 부서 id → 왼쪽 라벨 매핑 (DB 안 바꿔도 됨)
export const LEFT_LABEL_BY_ID = {
    1: '학생',
    2: '수업',
    3: '국제',
};

// (보조) 부서명 기준 매핑도 필요하면
export const LEFT_LABEL_BY_NAME = {
    '학생과': '학생',
    '학사지원과': '수업',
    '국제교류본부': '국제',
};
// 표시용 라벨 생성기
// 표시용 라벨 생성기
export function formatDeptLabel(dept) {
    if (!dept) return '';
    const left =
        LEFT_LABEL_BY_ID[dept.id] ??
        LEFT_LABEL_BY_NAME[dept.name] ??
        '';
    return left ? `${left} | ${dept.name}` : dept.name;
}


export default function useDepartments() {
    const [departments, setDepartments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError]   = useState(null);

    useEffect(() => {
        let mounted = true;
        (async () => {
            try {
                const list = await getDepartments();
                if (mounted) setDepartments(list ?? []);
            } catch (e) {
                if (mounted) setError(e);
                console.error(e);
            } finally {
                if (mounted) setLoading(false);
            }
        })();
        return () => { mounted = false; };
    }, []);

    return { departments, loading, error };
}
