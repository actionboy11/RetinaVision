CREATE DATABASE IF NOT EXISTS retina_vision
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE retina_vision;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    role_code VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS medical_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_no VARCHAR(64) NOT NULL UNIQUE,
    patient_code VARCHAR(64) NOT NULL,
    patient_age INT,
    patient_gender VARCHAR(16),
    eye_side VARCHAR(16) NOT NULL,
    diagnosis_note VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL COMMENT '创建用户ID，逻辑关联 sys_user.id',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    INDEX idx_created_by_status (created_by, status),
    INDEX idx_case_no (case_no)
);

CREATE TABLE IF NOT EXISTS image_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL COMMENT '所属病例ID，逻辑关联 medical_case.id',
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_bucket VARCHAR(128),
    storage_object_key VARCHAR(512) NOT NULL,
    preview_url VARCHAR(512),
    image_width INT,
    image_height INT,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by BIGINT NOT NULL COMMENT '上传用户ID，逻辑关联 sys_user.id',
    uploaded_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    INDEX idx_image_case_id (case_id),
    INDEX idx_image_status (status),
    INDEX idx_image_uploaded_by (uploaded_by),
    INDEX idx_image_uploaded_at (uploaded_at)
);

CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    case_id BIGINT NOT NULL COMMENT '所属病例ID，逻辑关联 medical_case.id',
    image_file_id BIGINT NOT NULL COMMENT '分析图像ID，逻辑关联 image_file.id',
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    priority INT NOT NULL DEFAULT 5,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    error_message VARCHAR(1024) NULL,
    submitted_by BIGINT NOT NULL COMMENT '提交用户ID，逻辑关联 sys_user.id',
    submitted_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    canceled_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_task_no (task_no),
    INDEX idx_task_case_id (case_id),
    INDEX idx_task_image_file_id (image_file_id),
    INDEX idx_task_status (status),
    INDEX idx_task_task_type (task_type),
    INDEX idx_task_submitted_by (submitted_by),
    INDEX idx_task_submitted_at (submitted_at),
    INDEX idx_task_status_priority (status, priority, submitted_at)
);

CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '所属任务ID，逻辑关联 analysis_task.id',
    result_type VARCHAR(64) NOT NULL,
    result_json JSON NOT NULL,
    mask_bucket VARCHAR(128) NULL,
    mask_object_key VARCHAR(512) NULL,
    mask_preview_url VARCHAR(1024) NULL,
    report_bucket VARCHAR(128) NULL,
    report_object_key VARCHAR(512) NULL,
    report_download_url VARCHAR(1024) NULL,
    model_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    processing_time_ms INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_result_task_id (task_id),
    INDEX idx_result_result_type (result_type),
    INDEX idx_result_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS task_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '所属任务ID，逻辑关联 analysis_task.id',
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    message VARCHAR(1024) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id BIGINT NULL COMMENT '操作用户ID，逻辑关联 sys_user.id',
    created_at DATETIME NOT NULL,
    INDEX idx_task_log_task_id (task_id),
    INDEX idx_task_log_created_at (created_at),
    INDEX idx_task_log_operator_id (operator_id)
);
