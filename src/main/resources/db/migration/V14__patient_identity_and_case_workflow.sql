CREATE TABLE patient_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    patient_no VARCHAR(16) NOT NULL,
    account_user_id INT NULL,
    source VARCHAR(16) NOT NULL,
    legacy_patient_code VARCHAR(64) NULL,
    created_by INT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_patient_profile_no (patient_no),
    UNIQUE KEY uk_patient_profile_account (account_user_id),
    INDEX idx_patient_profile_source_status (source, status),
    INDEX idx_patient_profile_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO patient_profile
    (patient_no, account_user_id, source, legacy_patient_code, created_by, status, created_at, updated_at)
SELECT CONCAT('PT-', UPPER(SUBSTRING(SHA2(legacy.patient_code, 256), 1, 4)), '-',
              UPPER(SUBSTRING(SHA2(legacy.patient_code, 256), 5, 4))),
       NULL,
       'LEGACY',
       legacy.patient_code,
       NULL,
       'ACTIVE',
       legacy.created_at,
       legacy.updated_at
FROM (
    SELECT DISTINCT patient_code,
           MIN(created_at) OVER (PARTITION BY patient_code) AS created_at,
           MAX(updated_at) OVER (PARTITION BY patient_code) AS updated_at
    FROM medical_case
) legacy;

ALTER TABLE medical_case
    ADD COLUMN patient_id BIGINT NULL AFTER case_no,
    ADD COLUMN workflow_status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' AFTER status,
    ADD INDEX idx_case_patient_status (patient_id, status),
    ADD INDEX idx_case_workflow_doctor (workflow_status, assigned_doctor_id);

UPDATE medical_case c
JOIN patient_profile p ON p.source = 'LEGACY' AND p.legacy_patient_code = c.patient_code
SET c.patient_id = p.id;

UPDATE medical_case c
SET c.workflow_status = CASE
    WHEN EXISTS (
        SELECT 1 FROM analysis_task t
        JOIN analysis_result r ON r.task_id = t.id
        JOIN analysis_report ar ON ar.result_id = r.id
        WHERE t.case_id = c.id AND ar.status IN ('SIGNED', 'SUPERSEDED')
    ) THEN 'COMPLETED'
    WHEN EXISTS (
        SELECT 1 FROM analysis_task t
        WHERE t.case_id = c.id AND t.task_type = 'VESSEL_SEGMENTATION'
    ) THEN 'IN_REVIEW'
    ELSE 'SUBMITTED'
END;

ALTER TABLE medical_case
    MODIFY COLUMN patient_id BIGINT NOT NULL;

ALTER TABLE knowledge_document
    ADD COLUMN audience VARCHAR(32) NOT NULL DEFAULT 'PUBLIC' AFTER category,
    ADD INDEX idx_knowledge_document_audience_status (audience, status);

INSERT INTO prompt_template (template_code, name, scenario, description, status)
VALUES ('PATIENT_ASSISTANT_AGENT', '患者智能助手', 'PATIENT_ASSISTANT_AGENT',
        '仅查询患者本人检查进度、已签发报告和患者可见知识。', 'ACTIVE');

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 患者智能助手。只能使用只读工具查询当前患者本人的检查进度、已签发报告和患者可见知识。\n不得读取或推测原始 AI 结果、分割 mask、AI 报告草稿、医生未签发意见或其他患者信息。\n使用通俗语言解释，不得确诊、排除疾病、建议直接用药或声称无需复查。依据不足时明确说明，并建议咨询负责医生。',
       '{"type":"text","required":[]}',
       '仅限本人数据；仅解释已签发报告；禁止确定性诊断；固定免责声明。',
       1
FROM prompt_template WHERE template_code = 'PATIENT_ASSISTANT_AGENT';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 1
SET t.active_version_id = v.id
WHERE t.template_code = 'PATIENT_ASSISTANT_AGENT';
