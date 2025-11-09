import React, { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import styles from './StudentLayout.module.css';
import { getMyInfo } from '../api/api';

const StudentLayout = () => {
    const nav = useNavigate();
    const location = useLocation();

    // ── 1) 로그인 가드
    const [checking, setChecking] = useState(true);
    useEffect(() => {
        let mounted = true;
        (async () => {
            try {
                await getMyInfo(); // 세션 확인
            } catch (e) {
                if (mounted) nav('/'); // 미로그인 → 로그인 페이지
                return;
            } finally {
                if (mounted) setChecking(false);
            }
        })();
        return () => { mounted = false; };
    }, [nav]);

    // ── 2) URL 쿼리에서 activeDeptId 읽기
    const search = useMemo(() => new URLSearchParams(location.search), [location.search]);
    const activeDeptId = search.get('category') ? String(search.get('category')) : null;

    // ── 3) 사이드바 클릭: URL 쿼리 업데이트(+ 하이라이트 유지)
    const handleMenuClick = (dept) => {
        const deptId = String(dept.id);
        // 현재 페이지 유지하면서 쿼리만 바꾸고 싶으면 location.pathname 사용
        nav(`${location.pathname}?category=${deptId}`, { replace: false });
    };

    if (checking) return <div className={styles.loading}>로그인 확인 중…</div>;

    return (
        <div className={styles.layoutWrapper}>
            <div className={styles.bodyWrapper}>
                <Sidebar onMenuClick={handleMenuClick} activeDeptId={activeDeptId} />
                <main className={styles.mainContent}>
                    <Outlet />
                </main>
            </div>
        </div>
    );
};

export default StudentLayout;
