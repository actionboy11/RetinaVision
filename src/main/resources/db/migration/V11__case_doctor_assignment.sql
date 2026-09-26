ALTER TABLE medical_case
    ADD COLUMN assigned_doctor_id INT NULL AFTER created_by,
    ADD INDEX idx_case_assigned_doctor_status (assigned_doctor_id, status);

UPDATE medical_case c
JOIN sys_user u ON u.id = c.created_by
SET c.assigned_doctor_id = c.created_by
WHERE u.role_code = 'DOCTOR'
  AND u.status = 1
  AND c.assigned_doctor_id IS NULL;
