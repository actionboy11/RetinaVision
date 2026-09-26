CREATE TABLE agent_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    title VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agent_chat_session_user (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    llm_provider VARCHAR(64) NULL,
    llm_model VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_chat_message_session (session_id, created_at),
    CONSTRAINT fk_agent_chat_message_session FOREIGN KEY (session_id) REFERENCES agent_chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_tool_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    arguments_summary TEXT NOT NULL,
    result_summary TEXT NULL,
    success TINYINT(1) NOT NULL,
    latency_ms BIGINT NOT NULL,
    error_summary VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_tool_call_session (session_id, created_at),
    INDEX idx_agent_tool_call_trace (trace_id),
    CONSTRAINT fk_agent_tool_call_session FOREIGN KEY (session_id) REFERENCES agent_chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO prompt_template (template_code, name, scenario, description, status)
VALUES ('CLINICAL_ASSISTANT_AGENT', '临床只读智能助手', 'CLINICAL_ASSISTANT_AGENT',
        '通过受控只读工具查询知识库、任务、病例趋势和 AI 质控信息。', 'ACTIVE');

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 临床只读智能助手。你只能使用系统提供的只读工具回答问题。\n医生可以查询其有权访问的任务、病例时间线、结果对比、知识库和质控数据；管理员只能查询知识库和质控数据。\n禁止创建、修改、删除任何业务数据，禁止保存审核意见或签发报告。\n不要猜测工具未返回的信息；依据不足时明确说明无法从当前数据确认。\n禁止给出确诊、排除疾病、无需复查、无需就医或直接用药等确定性医学结论。\n回答应指出使用了哪些数据来源，并提醒最终判断由医生完成。',
       '{"type":"text","required":[]}',
       '只读工具；按角色动态授权；禁止确定性诊断；固定免责声明；回答最多 3000 字。',
       1
FROM prompt_template WHERE template_code = 'CLINICAL_ASSISTANT_AGENT';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 1
SET t.active_version_id = v.id
WHERE t.template_code = 'CLINICAL_ASSISTANT_AGENT';
