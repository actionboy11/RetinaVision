CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    source VARCHAR(255) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_knowledge_document_status (status),
    INDEX idx_knowledge_document_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_text TEXT NOT NULL,
    char_length INT NOT NULL,
    qdrant_point_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_knowledge_chunk_point (qdrant_point_id),
    INDEX idx_knowledge_chunk_document (document_id),
    CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_knowledge_chat_session_user (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    citations_json JSON NULL,
    llm_provider VARCHAR(32),
    llm_model VARCHAR(64),
    created_at DATETIME NOT NULL,
    INDEX idx_knowledge_chat_message_session (session_id, created_at),
    CONSTRAINT fk_knowledge_chat_message_session FOREIGN KEY (session_id) REFERENCES knowledge_chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
