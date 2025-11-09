import React from 'react';
import styles from './Chatbot.module.css';
import ChatBubble from './ChatBubble';
import FileUploader from './FileUploader';
import useChatScenario from '../../hooks/useChatScenario';

const Chatbot = ({ onClose }) => {
    const {
        chatHistory,
        handleUserInput,
        handleFileUpload,
    } = useChatScenario();

    return (
        <div className={styles.chatbotPanel}>
            <div className={styles.chatHeader}>
                <div>CNU 챗봇</div>
                <button className={styles.closeButton} onClick={onClose}>✕</button>
            </div>

            <div className={styles.chatBody}>
                {chatHistory.map((chat, idx) => {
                    const isBot = chat.from === 'bot';
                    const hasOptions = !!chat.options;

                    // 마스코트 포함 스타일 (옵션 버튼 있는 경우 또는 단순 bot 메시지인 경우)
                    if (isBot) {
                        return (
                            <div className={styles.botGreetingBox} key={idx}>
                                <img
                                    src="/images/chat_mascot.png"
                                    alt="챗봇 마스코트"
                                    className={styles.botMascot}
                                />
                                <div className={styles.botMessageBox}>
                                    {chat.isHtml ? (
                                        <div
                                            className={styles.greetingText}
                                            dangerouslySetInnerHTML={{ __html: chat.message }}
                                        />
                                    ) : (
                                        <div className={styles.greetingText}>{chat.message}</div>
                                    )}

                                    {chat.options && (
                                        <div className={styles.optionsContainer}>
                                            {chat.options.map((opt, i) => (
                                                <button key={i} className={styles.optionButton} onClick={() => handleUserInput(opt)}>
                                                    {opt}
                                                </button>
                                            ))}
                                        </div>
                                    )}

                                    {chat.uploadEnabled && (
                                        <div className={styles.uploader}>
                                            <FileUploader onUpload={handleFileUpload} />
                                        </div>
                                    )}
                                </div>
                            </div>
                        );
                    }

                    // 유저 메시지 (오른쪽 말풍선)
                    return (
                        <ChatBubble key={idx} sender="user" message={chat.message} />
                    );
                })}

            </div>

            <div className={styles.chatInput}>
                <input type="text" placeholder="메시지를 입력하세요." disabled />
                <button disabled>전송</button>
            </div>
        </div>
    );
};

export default Chatbot;
