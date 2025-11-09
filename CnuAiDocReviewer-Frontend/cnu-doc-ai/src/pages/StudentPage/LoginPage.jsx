// src/pages/StudentPage/LoginPage.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './LoginPage.module.css';
import { login, getMyInfo } from '../../api/api';

const LoginPage = () => {
    const bg = `${process.env.PUBLIC_URL}/images/cnu.png`;
    const [memberId, setMemberId] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const nav = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault(); // ★ 새로고침 막기
        if (!memberId || !password) return;

        try {
            setLoading(true);
            await login(memberId, password);          // ★ POST /auth/login
            const me = await getMyInfo();             // ★ 세션 확인
            // 역할 따라 분기
            if (me?.role === 'ADMIN') nav('/admin/main');
            else nav('/student/main');
        } catch (err) {
            console.error(err);
            alert('로그인 실패: 아이디/비밀번호를 확인하세요.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.container} style={{ backgroundImage: `url(${bg})` }}>
            <div className={styles.loginBox}>
                <div className={styles.title}>포탈시스템 로그인</div>

                <form onSubmit={handleSubmit} className={styles.inputGroup}>
                    <input
                        type="text"
                        placeholder="ID"
                        className={styles.input}
                        value={memberId}
                        onChange={(e)=>setMemberId(e.target.value)}
                        autoComplete="username"
                    />
                    <input
                        type="password"
                        placeholder="PASSWORD"
                        className={styles.input}
                        value={password}
                        onChange={(e)=>setPassword(e.target.value)}
                        autoComplete="current-password"
                    />
                    <button type="submit" className={styles.loginButton} disabled={loading}>
                        {loading ? '로그인 중…' : '로그인'}
                    </button>
                </form>

                {/* 나머지 UI 그대로 */}
            </div>
        </div>
    );
};

export default LoginPage;
