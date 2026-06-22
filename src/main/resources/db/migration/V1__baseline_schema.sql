CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    role_code VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE medical_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_no VARCHAR(64) NOT NULL UNIQUE,
    patient_code VARCHAR(64) NOT NULL,
    patient_age INT,
    patient_gender VARCHAR(16),
    eye_side VARCHAR(16) NOT NULL,
    diagnosis_note VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    INDEX idx_created_by_status (created_by, status)
);

CREATE TABLE image_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_bucket VARCHAR(128),
    storage_object_key VARCHAR(512) NOT NULL,
    preview_url VARCHAR(512),
    image_width INT,
    image_height INT,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    INDEX idx_image_case_id (case_id)
);

CREATE TABLE analysis_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    case_id BIGINT NOT NULL,
    image_file_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    priority INT NOT NULL DEFAULT 5,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    error_message VARCHAR(1024),
    submitted_by BIGINT NOT NULL,
    submitted_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    canceled_at DATETIME,
    updated_at DATETIME NOT NULL,
    INDEX idx_task_case_id (case_id),
    INDEX idx_task_status_priority (status, priority, submitted_at)
);

CREATE TABLE analysis_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL UNIQUE,
    result_type VARCHAR(64) NOT NULL,
    result_json JSON NOT NULL,
    mask_bucket VARCHAR(128),
    mask_object_key VARCHAR(512),
    mask_preview_url VARCHAR(1024),
    report_bucket VARCHAR(128),
    report_object_key VARCHAR(512),
    report_download_url VARCHAR(1024),
    model_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    processing_time_ms INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    created_at DATETIME NOT NULL,
    INDEX idx_task_log_task_id (task_id)
);
