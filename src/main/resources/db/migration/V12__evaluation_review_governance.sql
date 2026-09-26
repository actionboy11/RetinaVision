ALTER TABLE prompt_evaluation_run
    CHANGE COLUMN doctor_decision review_decision VARCHAR(20) NULL,
    CHANGE COLUMN doctor_score review_score TINYINT NULL,
    CHANGE COLUMN doctor_note review_note VARCHAR(500) NULL;
