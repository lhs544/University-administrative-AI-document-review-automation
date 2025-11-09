import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import styles from './Header.module.css';

const Header = () => {
    const location = useLocation();
    const isAdminPage = location.pathname.startsWith("/admin");
    // /admin/... 경로일 때만 true

    return (
        <div className={styles.header}>
            {isAdminPage ? (
                <Link to="/admin/main">
                    <img
                        src="/images/cnu_logo.png"
                        alt="충남대학교 로고"
                    />
                </Link>
            ) : (
                <img
                    src="/images/cnu_logo.png"
                    alt="충남대학교 로고"
                />
            )}
        </div>
    );
};

export default Header;
