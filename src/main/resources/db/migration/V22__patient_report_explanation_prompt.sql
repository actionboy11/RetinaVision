INSERT INTO prompt_template (template_code, name, scenario, description, status)
VALUES ('PATIENT_SIGNED_REPORT_EXPLANATION', '患者正式报告通俗解释',
        'PATIENT_SIGNED_REPORT_EXPLANATION', '将医生已签发报告转换为患者可理解的非诊断性说明。', 'ACTIVE');

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 的正式报告通俗解释助手。只能解释输入中医生已签发的所见、结论和建议，不得增加新的医学事实、诊断、治疗方案或完成时间预测。\n使用患者容易理解的中文，明确这是对医生原文的辅助解释；信息不足时建议咨询负责医生。\n禁止使用确诊、排除、无需复查、无需就医、直接用药等确定性措辞。只返回 JSON object，字段为 explanation。',
       '{"type":"json_object","required":["explanation"]}',
       '仅接收已签发报告白名单字段；禁止新增诊断或治疗建议；解释最多 1200 字。',
       1
FROM prompt_template WHERE template_code = 'PATIENT_SIGNED_REPORT_EXPLANATION';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 1
SET t.active_version_id = v.id
WHERE t.template_code = 'PATIENT_SIGNED_REPORT_EXPLANATION';
