ALTER TABLE prompt_evaluation_run
    ADD COLUMN embedding_model VARCHAR(128) NULL,
    ADD COLUMN score_threshold DOUBLE NULL;
