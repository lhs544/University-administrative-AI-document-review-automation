// src/components/Sidebar/Sidebar.jsx
import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import styles from './Sidebar.module.css';
import useDepartments, { formatDeptLabel } from '../hooks/useDepartments';

const Sidebar = ({ onMenuClick, activeDeptId }) => {
    const { departments, loading, error } = useDepartments();
    const location = useLocation();
    const navigate = useNavigate();

    // URL 쿼리에서 category 추출 (fallback)
    const search = new URLSearchParams(location.search);
    const categoryFromUrl = search.get('category');

    if (loading) {
        return (
            <aside className={styles.sidebar}>
                <div className={styles.title}>제출 서류 목록</div>
                <hr className={styles.divider} />
                <div className={styles.menu}>불러오는 중…</div>
            </aside>
        );
    }
    if (error) {
        return (
            <aside className={styles.sidebar}>
                <div className={styles.title}>제출 서류 목록</div>
                <hr className={styles.divider} />
                <div className={styles.menu}>부서 목록을 불러오지 못했습니다.</div>
            </aside>
        );
    }

    return (
        <aside className={styles.sidebar}>
            <div className={styles.title}>제출 서류 목록</div>
            <hr className={styles.divider} />
            <ul className={styles.menu}>
                {departments.map((dept) => {
                    const isActive = String(activeDeptId ?? categoryFromUrl ?? '') === String(dept.id);
                    const handleClick = () => {
                        onMenuClick?.(dept);
                        navigate(`/student/main?category=${dept.id}`);
                    };
                    return (
                        <li
                            key={dept.id}
                            onClick={handleClick}
                            className={`${styles.menuItem} ${isActive ? styles.active : ''}`}
                            title={dept.name}
                        >
                            ▶ {formatDeptLabel(dept)}
                        </li>
                    );
                })}
            </ul>
        </aside>
    );
};

export default Sidebar;
