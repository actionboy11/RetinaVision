CREATE TABLE agent_skill (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    active_version_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_skill_code (skill_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_skill_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_id BIGINT NOT NULL,
    version INT NOT NULL,
    routing_examples_json TEXT NOT NULL,
    routing_negative_examples_json TEXT NULL,
    workflow_prompt TEXT NOT NULL,
    answer_style VARCHAR(1000) NULL,
    error_prompts_json TEXT NULL,
    created_by INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_skill_version (skill_id, version),
    CONSTRAINT fk_agent_skill_version_skill FOREIGN KEY (skill_id) REFERENCES agent_skill(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE agent_skill
    ADD CONSTRAINT fk_agent_skill_active_version
        FOREIGN KEY (active_version_id) REFERENCES agent_skill_version(id);

CREATE TABLE agent_query_context (
    session_id BIGINT PRIMARY KEY,
    skill_version_id BIGINT NULL,
    current_skill_code VARCHAR(64) NULL,
    current_filters_json TEXT NULL,
    current_page INT NOT NULL DEFAULT 1,
    page_size INT NOT NULL DEFAULT 10,
    total BIGINT NOT NULL DEFAULT 0,
    selected_case_id INT NULL,
    selected_task_id BIGINT NULL,
    recent_result_references_json TEXT NULL,
    expires_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_query_context_session FOREIGN KEY (session_id) REFERENCES agent_chat_session(id),
    CONSTRAINT fk_agent_query_context_skill_version FOREIGN KEY (skill_version_id) REFERENCES agent_skill_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_skill_execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id INT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    skill_version INT NOT NULL,
    confidence DECIMAL(5,4) NULL,
    success TINYINT(1) NOT NULL,
    latency_ms BIGINT NOT NULL,
    error_type VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_skill_execution_session (session_id, created_at),
    INDEX idx_agent_skill_execution_skill (skill_code, created_at),
    CONSTRAINT fk_agent_skill_execution_session FOREIGN KEY (session_id) REFERENCES agent_chat_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE agent_skill_evaluation_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_version_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    route_passed_count INT NOT NULL DEFAULT 0,
    parameter_passed_count INT NOT NULL DEFAULT 0,
    routing_accuracy DECIMAL(6,5) NOT NULL DEFAULT 0,
    parameter_accuracy DECIMAL(6,5) NOT NULL DEFAULT 0,
    safety_passed TINYINT(1) NOT NULL DEFAULT 0,
    failure_samples_json TEXT NULL,
    created_by INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    INDEX idx_agent_skill_eval_version (skill_version_id, created_at),
    CONSTRAINT fk_agent_skill_eval_version FOREIGN KEY (skill_version_id) REFERENCES agent_skill_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE agent_chat_session ADD COLUMN skill_version_id BIGINT NULL;
ALTER TABLE agent_chat_session ADD CONSTRAINT fk_agent_session_skill_version
    FOREIGN KEY (skill_version_id) REFERENCES agent_skill_version(id);
ALTER TABLE agent_chat_message ADD COLUMN structured_content_json LONGTEXT NULL;

INSERT INTO agent_skill (skill_code, name, description) VALUES
('DOCTOR_WORKLOAD_OVERVIEW', '医生工作量总览', '统计当前医生负责的患者、病例、分割和审核工作量'),
('ASSIGNED_CASE_SEARCH', '负责病例筛选', '按自然语言条件分页查询当前医生负责的病例'),
('CASE_CLINICAL_SUMMARY', '病例临床摘要', '读取当前医生负责病例的匿名结构化摘要'),
('CASE_FOLLOWUP_ANALYSIS', '病例随访比较', '查询病例分析时间线并比较最近两次同类结果'),
('MEDICAL_KNOWLEDGE_QA', '医学知识检索', '检索医生可见的医学知识并返回可核验引用');

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["我有多少名患者","我负责多少个病例","今天有哪些待处理事项"]',
       '["列出具体病例","查看第三个病例"]',
       '调用工作量统计工具，只总结当前医生负责范围内的聚合数据。',
       '先给结论，再列关键指标，不推断诊断。',
       '{"empty":"当前没有符合条件的工作事项"}'
FROM agent_skill WHERE skill_code = 'DOCTOR_WORKLOAD_OVERVIEW';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["查询我负责的病例摘要","哪些病例还没有进行分割","只看失败的","继续"]',
       '["我有多少名患者","比较最近两次结果"]',
       '调用病例分页查询工具，默认按更新时间倒序且每页最多十条。',
       '给出总数和当前页摘要，引导用户翻页或查看具体病例。',
       '{"empty":"没有找到符合当前条件的病例","expired":"查询上下文已过期，请重新查询病例列表"}'
FROM agent_skill WHERE skill_code = 'ASSIGNED_CASE_SEARCH';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["查看第三个病例","查看病例C2026","这个患者进行到哪一步"]',
       '["列出全部病例","比较最近两次结果"]',
       '定位当前医生可访问的病例并返回匿名结构化摘要。',
       '仅说明流程、质量和任务状态。',
       '{"notFound":"未找到可访问的病例"}'
FROM agent_skill WHERE skill_code = 'CASE_CLINICAL_SUMMARY';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["比较它最近两次结果","查看这个病例的历史分析时间线","血管面积比例有什么变化"]',
       '["为什么要做血管分割","列出失败病例"]',
       '查询同一病例、同一眼别、同类任务的时间线或结果比较。',
       '只解释结构化变化，明确质量和模型版本影响。',
       '{"insufficient":"当前病例没有足够的同类历史结果可供比较"}'
FROM agent_skill WHERE skill_code = 'CASE_FOLLOWUP_ANALYSIS';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["为什么要做视网膜血管分割","血管面积占比是什么意思","图像质量低有什么影响"]',
       '["列出我的病例","查看任务状态"]',
       '只根据检索到的医生受众知识片段回答并保留引用。',
       '使用医学解释性语言，不形成诊断结论。',
       '{"empty":"知识库未检索到足够依据"}'
FROM agent_skill WHERE skill_code = 'MEDICAL_KNOWLEDGE_QA';

UPDATE agent_skill s
JOIN agent_skill_version v ON v.skill_id = s.id AND v.version = 1
SET s.active_version_id = v.id;
