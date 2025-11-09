import React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import styles from './AdminSidebar.module.css';
import useDepartments, { formatDeptLabel } from '../hooks/useDepartments';


const AdminSidebar = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { departments, loading } = useDepartments();

    const searchParams = new URLSearchParams(location.search);
    // 쿼리가 없을 때 기본값은 첫 부서로
    const currentCategory = searchParams.get('category')
        || (departments[0]?.id?.toString() ?? '');
    const pathType = location.pathname.split('/')[2];

    const onMenuClick = (type, deptId) => {
        navigate(`/admin/${type}?category=${deptId}`);
    };

    const renderMenuItems = (type) => {
        if (loading) return <ul><li className={styles.menuItem}>로딩...</li></ul>;
        return (
            <ul>
                {departments.map(dept => {
                    const key = String(dept.id);
                    const isActive = type === pathType && key === currentCategory;
                    return (
                        <li
                            key={`${type}-${key}`}
                            onClick={() => onMenuClick(type, key)}
                            className={`${styles.menuItem} ${isActive ? styles.active : ''}`}
                        >
                            {/* 전화번호 제거, 원하는 포맷으로 */}
                            ▶ {formatDeptLabel(dept)}
                        </li>
                    );
                })}
            </ul>
        );
    };

    return (
        <div className={styles.sidebar}>
            <div className={styles.title}>제출 서류 관리</div>
            <hr className={styles.divider} />
            <div className={styles.subtitle}>행정 서비스</div>
            <ul>{renderMenuItems('submissions')}</ul>

            <div className={styles.title}>서류 유형 관리</div>
            <hr className={styles.divider} />

            <div className={styles.subtitle}>서류 관리</div>
            <ul>{renderMenuItems('required')}</ul>

            <div className={styles.subtitle}>마감일 관리</div>
            <ul>{renderMenuItems('deadlines')}</ul>

        </div>
    );
};

export default AdminSidebar;
