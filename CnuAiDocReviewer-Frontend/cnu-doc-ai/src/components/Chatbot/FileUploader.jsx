
import React, { useRef } from 'react';
import styles from './Chatbot.module.css';


const FileUploader = ({ onUpload }) => {
    const inputRef = useRef(null);

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file && onUpload) {
            onUpload(file);
        }
    };

    return (
        <div className={styles.uploader}>
            <input
                type="file"
                ref={inputRef}
                onChange={handleFileChange}
                accept="application/pdf,image/*"
                hidden
            />
            <button onClick={() => inputRef.current.click()} className={styles.uploadButton}>
                📎 파일 선택
            </button>
        </div>
    );
};

export default FileUploader;
