ALTER TABLE agent_query_context
    ADD COLUMN reference_type VARCHAR(32) NOT NULL DEFAULT 'CASE' AFTER current_skill_code;

UPDATE agent_query_context
SET reference_type = 'CASE'
WHERE reference_type IS NULL OR reference_type = '';

INSERT INTO prompt_template (template_code, name, scenario, description, status)
VALUES ('AGENT_SKILL_ROUTER', 'Agent Skill 路由', 'AGENT_SKILL_ROUTER',
        '根据当前角色可用的 Skill 目录选择只读业务能力并提取参数。', 'ACTIVE');

INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT id, 1,
       '你是 RetinaVision 的 Skill 路由器。你只负责根据用户问题和提供的 Skill 目录选择一个 skillCode 并提取 arguments，不回答业务问题，不调用任何工具。\n只能选择目录中存在的 Skill 和参数枚举；信息不足时降低 confidence。\n只返回 JSON object，字段必须为 skillCode、confidence、arguments。',
       '{"type":"json_object","required":["skillCode"],"properties":{"skillCode":{"type":"string"},"confidence":{"type":"number"},"arguments":{"type":"object"}}}',
       '无工具；只允许目录内 Skill；不得生成业务答案或任意 URL。',
       1
FROM prompt_template WHERE template_code = 'AGENT_SKILL_ROUTER';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 1
SET t.active_version_id = v.id
WHERE t.template_code = 'AGENT_SKILL_ROUTER';

INSERT INTO agent_skill (skill_code, name, description, status) VALUES
('DOCTOR_TASK_SEARCH', '医生任务查询', '查询当前医生负责病例的分析任务、状态和脱敏日志', 'ACTIVE'),
('DOCTOR_CLINICAL_QUEUE', '医生临床队列', '查询当前医生的待审核结果和已审核待签发报告', 'ACTIVE');

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '[{"query":"查询我的任务","arguments":{"taskType":"VESSEL_SEGMENTATION","status":"ANY"}},{"query":"哪些分割任务失败了","arguments":{"taskType":"VESSEL_SEGMENTATION","status":"FAILED"}},{"query":"查询图像质检任务","arguments":{"taskType":"IMAGE_QUALITY_CHECK","status":"ANY"}},{"query":"最近七天有哪些正在处理的任务","arguments":{"taskType":"VESSEL_SEGMENTATION","status":"RUNNING","dateWindow":"LAST_7_DAYS"}}]',
       '["查询我负责的病例","今天有哪些待审核结果","为什么要做血管分割","重试失败任务"]',
       '使用 DIRECT 执行模式查询当前医生负责病例下的分析任务；默认任务类型为血管分割。',
       '返回精确总数、当前页结构化任务和受控详情动作，不推断诊断。',
       '{"empty":"没有找到符合当前条件的任务","expired":"任务查询上下文已过期，请重新查询任务列表"}'
FROM agent_skill WHERE skill_code = 'DOCTOR_TASK_SEARCH';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 1,
       '[{"query":"今天有哪些待审核结果","arguments":{"queueType":"PENDING_REVIEW","dateWindow":"TODAY"}},{"query":"哪些结果已经审核但还没有签发","arguments":{"queueType":"PENDING_REPORT"}},{"query":"打开第一个待签发报告","arguments":{"queueType":"PENDING_REPORT"}}]',
       '["查询我的任务","列出负责病例","签发这份报告","保存医生审核"]',
       '使用 DIRECT 执行模式查询当前医生的待审核或待签发队列，不执行审核和签发。',
       '返回精确总数、当前页结构化待办和受控工作台动作。',
       '{"empty":"当前没有符合条件的临床待办","expired":"临床队列上下文已过期，请重新查询"}'
FROM agent_skill WHERE skill_code = 'DOCTOR_CLINICAL_QUEUE';
