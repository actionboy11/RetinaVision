CREATE TABLE agent_session_skill_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    skill_version_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_session_skill (session_id, skill_code),
    INDEX idx_agent_session_skill_version (skill_version_id),
    CONSTRAINT fk_agent_session_skill_session FOREIGN KEY (session_id) REFERENCES agent_chat_session(id),
    CONSTRAINT fk_agent_session_skill_binding_version FOREIGN KEY (skill_version_id) REFERENCES agent_skill_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
