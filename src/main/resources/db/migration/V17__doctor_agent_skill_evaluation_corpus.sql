INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 2,
       '[{"query":"我有多少名患者","arguments":{}},{"query":"我负责多少个病例","arguments":{}},{"query":"工作量总览","arguments":{}}]',
       '["查询我负责的病例","今天有哪些待审核结果"]',
       '调用工作量统计工具，只总结当前医生负责范围内的聚合数据。',
       '先给结论，再列关键指标，不推断诊断。',
       '{"empty":"当前没有符合条件的工作事项"}'
FROM agent_skill WHERE skill_code = 'DOCTOR_WORKLOAD_OVERVIEW';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 2,
       '[{"query":"查询我负责的病例","arguments":{"segmentationState":"ANY"}},{"query":"哪些病例还没有分割","arguments":{"segmentationState":"NOT_CREATED"}},{"query":"最近一个月右眼分割失败的病例","arguments":{"segmentationState":"FAILED","dateWindow":"LAST_30_DAYS","eyeSide":"RIGHT"}},{"query":"今天有哪些待审核结果","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REVIEW","dateWindow":"TODAY"}},{"query":"哪些结果已经审核但还没有签发","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REPORT"}}]',
       '["我有多少名患者","比较最近两次结果","为什么要做血管分割"]',
       '调用病例分页查询工具，按更新时间倒序返回当前医生有权访问的病例，每页最多十条。',
       '给出总数和当前页摘要，引导用户翻页或查看具体病例。',
       '{"empty":"没有找到符合当前条件的病例","expired":"查询上下文已过期，请重新查询病例列表"}'
FROM agent_skill WHERE skill_code = 'ASSIGNED_CASE_SEARCH';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 2,
       '[{"query":"查看病例 C20260926083238176 的摘要","arguments":{}},{"query":"查看匿名患者 PT-XE6V-93SR 的病例进度","arguments":{}},{"query":"查看第三个","arguments":{}}]',
       '["查询我负责的病例","比较最近两次结果"]',
       '定位当前医生可访问的病例并返回匿名结构化摘要。',
       '仅说明流程、质量和任务状态。',
       '{"notFound":"未找到可访问的病例"}'
FROM agent_skill WHERE skill_code = 'CASE_CLINICAL_SUMMARY';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 2,
       '[{"query":"比较它最近两次结果","arguments":{}},{"query":"查看这个病例的历史分析时间线","arguments":{}},{"query":"血管面积比例有什么变化","arguments":{}}]',
       '["为什么要做血管分割","列出失败病例"]',
       '查询同一病例、同一眼别、同类任务的时间线或结果比较。',
       '只解释结构化变化，明确质量和模型版本影响。',
       '{"insufficient":"当前病例没有足够的同类历史结果可供比较"}'
FROM agent_skill WHERE skill_code = 'CASE_FOLLOWUP_ANALYSIS';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 2,
       '[{"query":"为什么要做视网膜血管分割","arguments":{}},{"query":"血管面积占比是什么意思","arguments":{}},{"query":"图像质量低有什么影响","arguments":{}}]',
       '["列出我的病例","比较最近两次结果"]',
       '只根据检索到的医生受众知识片段回答并保留引用。',
       '使用医学解释性语言，不形成诊断结论。',
       '{"empty":"知识库未检索到足够依据"}'
FROM agent_skill WHERE skill_code = 'MEDICAL_KNOWLEDGE_QA';
