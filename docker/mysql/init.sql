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
    INDEX idx_created_by_status (created_by, status),
    INDEX idx_case_no (case_no)
    );