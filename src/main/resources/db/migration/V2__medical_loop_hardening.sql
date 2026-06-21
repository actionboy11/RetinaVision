ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS professional_no VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS role_assigned_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS role_assigned_at DATETIME NULL;

ALTER TABLE image_file
    ADD COLUMN IF NOT EXISTS quality_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED',
    ADD COLUMN IF NOT EXISTS quality_score DECIMAL(6,2) NULL,
    ADD COLUMN IF NOT EXISTS quality_result_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS quality_task_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS quality_checked_at DATETIME NULL;

ALTER TABLE analysis_task
    ADD COLUMN IF NOT EXISTS quality_override TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quality_override_reason VARCHAR(512) NULL;

CREATE TABLE IF NOT EXISTS analysis_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    issue_codes JSON NOT NULL,
    comment VARCHAR(2000),
    submitted_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_feedback_result_id (result_id)
);

CREATE TABLE IF NOT EXISTS analysis_correction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    version INT NOT NULL,
    corrected_result_json JSON NOT NULL,
    corrected_mask_object_key VARCHAR(512) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_correction_result_version (result_id, version)
);

CREATE TABLE IF NOT EXISTS analysis_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    correction_version INT,
    status VARCHAR(32) NOT NULL,
    findings TEXT,
    conclusion TEXT,
    recommendation TEXT,
    reviewer_id BIGINT NOT NULL,
    reviewer_name_snapshot VARCHAR(64) NOT NULL,
    professional_no_snapshot VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    reviewed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_review_result_id (result_id)
);

CREATE TABLE IF NOT EXISTS analysis_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    correction_version INT,
    draft_json JSON NOT NULL,
    report_object_key VARCHAR(512),
    report_sha256 CHAR(64),
    created_by BIGINT NOT NULL,
    signed_by BIGINT,
    signer_name_snapshot VARCHAR(64),
    professional_no_snapshot VARCHAR(64),
    signed_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_report_result_version (result_id, version)
);

UPDATE image_file image
LEFT JOIN (
    SELECT image_file_id, MAX(id) AS quality_task_id
    FROM analysis_task
    WHERE task_type = 'IMAGE_QUALITY_CHECK'
    GROUP BY image_file_id
) latest ON latest.image_file_id = image.id
SET image.quality_task_id = latest.quality_task_id
WHERE image.quality_task_id IS NULL AND latest.quality_task_id IS NOT NULL;
