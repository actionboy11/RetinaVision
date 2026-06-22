-- 每个字段先查询 MySQL 元数据，再动态执行 DDL，
-- 从而同时兼容空数据库、旧六表数据库和已经手工迁移过一部分的数据库。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'professional_no') = 0,
              'ALTER TABLE sys_user ADD COLUMN professional_no VARCHAR(64) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_assigned_by') = 0,
              'ALTER TABLE sys_user ADD COLUMN role_assigned_by BIGINT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_assigned_at') = 0,
              'ALTER TABLE sys_user ADD COLUMN role_assigned_at DATETIME NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_file' AND COLUMN_NAME = 'quality_status') = 0,
              'ALTER TABLE image_file ADD COLUMN quality_status VARCHAR(32) NOT NULL DEFAULT ''NOT_CHECKED''', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_file' AND COLUMN_NAME = 'quality_score') = 0,
              'ALTER TABLE image_file ADD COLUMN quality_score DECIMAL(6,2) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_file' AND COLUMN_NAME = 'quality_result_id') = 0,
              'ALTER TABLE image_file ADD COLUMN quality_result_id BIGINT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_file' AND COLUMN_NAME = 'quality_task_id') = 0,
              'ALTER TABLE image_file ADD COLUMN quality_task_id BIGINT NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'image_file' AND COLUMN_NAME = 'quality_checked_at') = 0,
              'ALTER TABLE image_file ADD COLUMN quality_checked_at DATETIME NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'analysis_task' AND COLUMN_NAME = 'quality_override') = 0,
              'ALTER TABLE analysis_task ADD COLUMN quality_override TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'analysis_task' AND COLUMN_NAME = 'quality_override_reason') = 0,
              'ALTER TABLE analysis_task ADD COLUMN quality_override_reason VARCHAR(512) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

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
