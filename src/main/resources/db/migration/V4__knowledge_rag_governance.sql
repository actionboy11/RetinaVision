ALTER TABLE knowledge_document
    ADD COLUMN original_content MEDIUMTEXT NULL AFTER content_type,
    ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'MEDICAL_BASE' AFTER original_content,
    ADD COLUMN chunk_count INT NOT NULL DEFAULT 0 AFTER uploaded_by,
    ADD COLUMN failure_reason VARCHAR(500) NULL AFTER chunk_count,
    ADD COLUMN last_indexed_at DATETIME NULL AFTER failure_reason,
    ADD INDEX idx_knowledge_document_category_status (category, status);

UPDATE knowledge_document d
LEFT JOIN (
    SELECT document_id, COUNT(*) AS chunk_count
    FROM knowledge_chunk
    GROUP BY document_id
) c ON c.document_id = d.id
SET d.chunk_count = COALESCE(c.chunk_count, 0),
    d.last_indexed_at = CASE WHEN COALESCE(c.chunk_count, 0) > 0 THEN d.updated_at ELSE d.last_indexed_at END;
