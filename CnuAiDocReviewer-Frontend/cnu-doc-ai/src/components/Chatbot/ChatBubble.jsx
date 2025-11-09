import React from 'react';
import styles from './Chatbot.module.css';

const ChatBubble = ({ sender, message }) => {
    const isBot = sender === 'bot';
    const renderWithLineBreaks = (text) =>
        text.split('\n').map((line, index) => (
            <React.Fragment key={index}>
                {line}
                <br />
            </React.Fragment>
        ));

    return (
        <div className={`${styles.bubbleWrapper} ${isBot ? styles.bot : styles.user}`}>
            <div className={styles.bubble}>
                {renderWithLineBreaks(message)}
            </div>
        </div>
    );
};
export default ChatBubble;
