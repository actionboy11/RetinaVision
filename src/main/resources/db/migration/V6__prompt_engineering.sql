CREATE TABLE prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    scenario VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    active_version_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prompt_template_code (template_code),
    INDEX idx_prompt_template_scenario_status (scenario, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prompt_template_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    version INT NOT NULL,
    system_prompt TEXT NOT NULL,
    output_contract TEXT NOT NULL,
    safety_policy TEXT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 0,
    created_by INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prompt_template_version (template_id, version),
    INDEX idx_prompt_version_active (template_id, is_active),
    CONSTRAINT fk_prompt_version_template FOREIGN KEY (template_id) REFERENCES prompt_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE llm_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenario VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version INT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    success TINYINT(1) NOT NULL,
    latency_ms BIGINT NOT NULL,
    error_summary VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_llm_call_log_created (created_at),
    INDEX idx_llm_call_log_filter (scenario, template_code, success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO prompt_template (template_code, name, scenario, description, status)
VALUES
    ('REPORT_DRAFT_GENERATION', 'AI 报告草稿', 'REPORT_DRAFT_GENERATION', '根据结构化分析结果生成医生可编辑的辅助报告草稿。', 'ACTIVE'),
    ('RAG_KNOWLEDGE_CHAT', 'RAG 知识问答', 'RAG_KNOWLEDGE_CHAT', '根据知识库检索片段生成带依据的知识解释。', 'ACTIVE'),
    ('CASE_TREND_SUMMARY', '病例趋势摘要', 'CASE_TREND_SUMMARY', '根据病例结构化时间线生成辅助性趋势摘要。', 'ACTIVE');

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 的医学报告草稿助手。你只能根据输入的结构化 AI 结果生成医生可编辑的辅助报告初稿和结果解释。\n请使用“辅助分析、建议复核、结合临床”的报告语气。findings 只描述模型输出和可复核现象，conclusion 只给辅助性总结，recommendation 只给复核、随访或结合临床建议。\n禁止使用确定性诊断措辞，包括但不限于：诊断为、确诊、排除、明确患有、无需复查。\n不要宣称完成诊断，不要替代医生审核，不要输出 Markdown。\n只返回 JSON object，字段必须为 findings、conclusion、recommendation、explanation、disclaimer。\ndisclaimer 必须是：AI辅助分析，不等同于独立医学诊断。',
       '{"type":"json_object","required":["findings","conclusion","recommendation","explanation","disclaimer"]}',
       '固定免责声明；禁止确定性诊断措辞；单字段最多 1000 字。',
       1
FROM prompt_template WHERE template_code = 'REPORT_DRAFT_GENERATION';

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 医疗知识助手，只能根据给定知识片段回答资料检索、概念解释和辅助理解问题。\n不得替代医生诊断，不得给出确诊、排除疾病、无需复查、无需就医或直接用药等确定性医学结论。\n如果资料不足，请说明知识库未检索到足够依据。只返回 JSON object，字段为 answer。',
       '{"type":"json_object","required":["answer"]}',
       '回答必须基于检索片段；无依据时不调用模型；固定免责声明；回答最多 1500 字。',
       1
FROM prompt_template WHERE template_code = 'RAG_KNOWLEDGE_CHAT';

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 的随访趋势摘要助手，只能根据输入的结构化时间线数据生成辅助性趋势摘要。\n不要进行诊断，不要使用确诊、排除、无需复查等确定性诊断措辞。\n只返回 JSON object，字段为 summary 和 recommendation。',
       '{"type":"json_object","required":["summary","recommendation"]}',
       '仅处理结构化趋势；禁止确定性诊断措辞；单字段最多 1000 字。',
       1
FROM prompt_template WHERE template_code = 'CASE_TREND_SUMMARY';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 1
SET t.active_version_id = v.id;

ALTER TABLE prompt_template
    ADD CONSTRAINT fk_prompt_template_active_version
        FOREIGN KEY (active_version_id) REFERENCES prompt_template_version(id);
