ALTER TABLE prompt_template_version
    ADD COLUMN released_at DATETIME NULL;

UPDATE prompt_template_version v
JOIN prompt_template t ON t.active_version_id = v.id
SET v.released_at = CURRENT_TIMESTAMP;

CREATE TABLE prompt_evaluation_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    baseline_version_id BIGINT NOT NULL,
    candidate_version_id BIGINT NOT NULL,
    sample_version VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json LONGTEXT NULL,
    automated_pass TINYINT(1) NULL,
    doctor_decision VARCHAR(20) NULL,
    doctor_score TINYINT NULL,
    doctor_note VARCHAR(500) NULL,
    reviewed_by INT NULL,
    reviewed_at DATETIME NULL,
    created_by INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    failure_reason VARCHAR(255) NULL,
    INDEX idx_prompt_eval_candidate (template_code, candidate_version_id, status, created_at),
    INDEX idx_prompt_eval_created (created_at),
    CONSTRAINT fk_prompt_eval_baseline FOREIGN KEY (baseline_version_id) REFERENCES prompt_template_version(id),
    CONSTRAINT fk_prompt_eval_candidate FOREIGN KEY (candidate_version_id) REFERENCES prompt_template_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT v.template_id, 2,
       CONCAT(v.system_prompt, '\n请按医生报告初稿的写作习惯，用简洁、客观、可复核的语言表达；不要把分割指标推断为疾病诊断。'),
       v.output_contract, v.safety_policy, 0
FROM prompt_template_version v
JOIN prompt_template t ON t.id = v.template_id
WHERE t.template_code = 'REPORT_DRAFT_GENERATION' AND v.version = 1;
