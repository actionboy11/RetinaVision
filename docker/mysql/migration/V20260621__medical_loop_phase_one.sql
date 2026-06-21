USE retina_vision;

ALTER TABLE sys_user
    ADD COLUMN professional_no VARCHAR(64) UNIQUE NULL COMMENT '医生工号或执业标识' AFTER role_code,
    ADD COLUMN role_assigned_by BIGINT NULL COMMENT '最近一次角色授予管理员ID' AFTER professional_no,
    ADD COLUMN role_assigned_at DATETIME NULL COMMENT '最近一次角色授予时间' AFTER role_assigned_by;

ALTER TABLE image_file
    ADD COLUMN quality_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' AFTER file_size,
    ADD COLUMN quality_score DECIMAL(5,2) NULL AFTER quality_status,
    ADD COLUMN quality_result_id BIGINT NULL AFTER quality_score,
    ADD COLUMN quality_checked_at DATETIME NULL AFTER quality_result_id;

ALTER TABLE analysis_task
    ADD COLUMN quality_override TINYINT(1) NOT NULL DEFAULT 0 AFTER task_type,
    ADD COLUMN quality_override_reason VARCHAR(512) NULL AFTER quality_override;

CREATE TABLE analysis_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, result_id BIGINT NOT NULL, verdict VARCHAR(32) NOT NULL,
    issue_codes JSON NOT NULL, comment VARCHAR(2000) NULL, submitted_by BIGINT NOT NULL, created_at DATETIME NOT NULL,
    INDEX idx_feedback_result_id (result_id)
);
CREATE TABLE analysis_correction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, result_id BIGINT NOT NULL, version INT NOT NULL,
    corrected_result_json JSON NOT NULL, corrected_mask_object_key VARCHAR(512) NOT NULL,
    reason VARCHAR(1000) NOT NULL, status VARCHAR(32) NOT NULL, submitted_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_correction_result_version (result_id, version)
);
CREATE TABLE analysis_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, result_id BIGINT NOT NULL, correction_version INT NULL,
    status VARCHAR(32) NOT NULL, findings TEXT NULL, conclusion TEXT NULL, recommendation TEXT NULL,
    reviewer_id BIGINT NOT NULL, reviewer_name_snapshot VARCHAR(64) NOT NULL,
    professional_no_snapshot VARCHAR(64) NOT NULL, version INT NOT NULL, reviewed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, UNIQUE KEY uk_review_result_id (result_id)
);
CREATE TABLE analysis_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT, result_id BIGINT NOT NULL, version INT NOT NULL,
    status VARCHAR(32) NOT NULL, correction_version INT NULL, draft_json JSON NOT NULL,
    report_object_key VARCHAR(512) NULL, report_sha256 CHAR(64) NULL, created_by BIGINT NOT NULL,
    signed_by BIGINT NULL, signer_name_snapshot VARCHAR(64) NULL, professional_no_snapshot VARCHAR(64) NULL,
    signed_at DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_report_result_version (result_id, version)
);
