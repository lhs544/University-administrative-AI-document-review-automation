import React from 'react';
import styles from './Chatbot.module.css';

const ChatOptions = ({ options = [], onSelect }) => {
    return (
        <div className={styles.optionsContainer}>
            {options.map((opt, idx) => (
                <button key={idx} onClick={() => onSelect?.(opt)} className={styles.optionButton}>
                    {opt}
                </button>
            ))}
        </div>
    );
};

export default ChatOptions;
