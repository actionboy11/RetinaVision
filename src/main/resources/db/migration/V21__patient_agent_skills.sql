INSERT INTO agent_skill (skill_code, name, description) VALUES
('MY_CASE_LIST', '我的检查列表', '分页查询当前患者本人的检查和通俗状态'),
('MY_CASE_PROGRESS', '我的检查进度', '查询当前患者本人指定检查的四阶段进度'),
('MY_SIGNED_REPORT', '我的正式报告', '查询并通俗解释当前患者本人的已签发报告'),
('PATIENT_KNOWLEDGE_QA', '患者医学知识', '检索患者可见的医学知识并返回可核验引用');

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["我有哪些检查","列出我的检查申请","哪些图像需要重新上传","继续"]',
       '["医生有哪些待审核任务","删除这个病例"]',
       '只查询当前患者本人病例，按最近更新时间倒序分页返回，每页最多十条。',
       '使用通俗状态，不展示评分、阈值、模型或技术日志。',
       '{"empty":"当前没有符合条件的检查","expired":"查询上下文已过期，请重新查询检查列表"}'
FROM agent_skill WHERE skill_code = 'MY_CASE_LIST';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["我最近一次检查到哪一步了","查看第二个检查的进度","医生处理完了吗"]',
       '["预测什么时候完成","创建血管分割任务"]',
       '查询当前患者本人指定病例的上传、质量检查、医生处理和正式报告阶段。',
       '只说明当前阶段、最近更新时间和下一处理角色，不预测完成时间。',
       '{"notFound":"资源不存在","empty":"当前还没有检查记录"}'
FROM agent_skill WHERE skill_code = 'MY_CASE_PROGRESS';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["查看我的最新正式报告","解释这份报告","查看第二个检查的报告"]',
       '["查看医生草稿","修改报告结论","给我诊断"]',
       '只查询当前患者本人已签发报告；解释仅使用允许的医生结构化字段。',
       '明确区分医生原文与 AI 通俗解释，不补充诊断或治疗方案。',
       '{"empty":"当前没有已签发报告","unavailable":"报告解释暂不可用，医生原文仍可查看"}'
FROM agent_skill WHERE skill_code = 'MY_SIGNED_REPORT';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '["为什么要做眼底图像质量检测","血管分割是什么意思","如何理解报告里的术语"]',
       '["查看我的检查进度","解释我的正式报告","替我开药"]',
       '只根据 PUBLIC 或 PATIENT 受众的知识片段回答，并保留可核验引用。',
       '使用患者可理解的非诊断性语言，依据不足时明确说明。',
       '{"empty":"知识库暂时没有足够依据"}'
FROM agent_skill WHERE skill_code = 'PATIENT_KNOWLEDGE_QA';

UPDATE agent_skill s
JOIN agent_skill_version v ON v.skill_id = s.id AND v.version = 1
SET s.active_version_id = v.id
WHERE s.skill_code IN (
    'MY_CASE_LIST', 'MY_CASE_PROGRESS', 'MY_SIGNED_REPORT', 'PATIENT_KNOWLEDGE_QA'
);
