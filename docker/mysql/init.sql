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
    created_by BIGINT NOT NULL,
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
