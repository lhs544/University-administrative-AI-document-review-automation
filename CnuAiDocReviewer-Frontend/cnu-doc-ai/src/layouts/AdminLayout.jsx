// src/layouts/AdminLayout.jsx
import React, { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import AdminSidebar from '../components/AdminSidebar';
import { getMyInfo, getDepartments } from '../api/api';

const AdminLayout = () => {
    const nav = useNavigate();
    const location = useLocation();

    // 1) 관리자 접근 가드
    const [checking, setChecking] = useState(true);
    useEffect(() => {
        let mounted = true;
        (async () => {
            try {
                const me = await getMyInfo();           // 세션 확인
                if (!mounted) return;
                if (!me || me.role !== 'ADMIN') {       // 역할 체크
                    nav('/');                             // 권한 없으면 로그인으로
                    return;
                }
            } catch {
                nav('/');                               // 미로그인 → 로그인
                return;
            } finally {
                if (mounted) setChecking(false);
            }
        })();
        return () => { mounted = false; };
    }, [nav]);

    // 2) 기본 부서 쿼리(category) 세팅
    const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
    const hasCategory = !!searchParams.get('category');

    useEffect(() => {
        let mounted = true;
        (async () => {
            if (hasCategory) return;
            // 현재 경로 유지하며 ?category=첫부서 로 보정
            const list = await getDepartments();
            if (!mounted) return;
            const first = list?.[0]?.id;
            if (first) {
                nav(`${location.pathname}?category=${first}`, { replace: true });
            }
        })();
        return () => { mounted = false; };
    }, [hasCategory, location.pathname, nav]);

    if (checking) return <div style={{ padding: 20 }}>관리자 권한 확인 중…</div>;

    return (
        <div style={{ display: 'flex', minHeight: '100vh' }}>
            <AdminSidebar />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                {/* App에서 이미 <Header/>를 전역으로 렌더링한다면 여기서는 제거 */}
                <div style={{ padding: 20, flex: 1 }}>
                    <Outlet />
                </div>
            </div>
        </div>
    );
};

export default AdminLayout;
