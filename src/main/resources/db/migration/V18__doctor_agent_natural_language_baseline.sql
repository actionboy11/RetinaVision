-- Expand doctor Agent evaluation coverage with common spoken expressions.
-- Versions remain inactive until an administrator runs and passes evaluation.

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 3,
       '[{"query":"我有多少名患者","arguments":{}},{"query":"我负责多少个病例","arguments":{}},{"query":"看一下我的工作量","arguments":{}},{"query":"名下患者有多少","arguments":{}},{"query":"现在管着几个病人","arguments":{}},{"query":"我手头有多少病例","arguments":{}},{"query":"给我一个工作量总览","arguments":{}},{"query":"我的患者总数是多少","arguments":{}}]',
       '["查询我负责的病例","今天有哪些待审核结果","打开第三个病例","为什么要做血管分割"]',
       '调用工作量统计工具，只总结当前医生负责范围内的聚合数据。',
       '先给结论，再列关键指标，不推断诊断。',
       '{"empty":"当前没有符合条件的工作事项"}'
FROM agent_skill WHERE skill_code = 'DOCTOR_WORKLOAD_OVERVIEW';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 3,
       '[{"query":"查询我负责的病例","arguments":{"segmentationState":"ANY"}},{"query":"把我负责的患者列出来","arguments":{"segmentationState":"ANY"}},{"query":"列出我的病例","arguments":{"segmentationState":"ANY"}},{"query":"哪些病例还没有进行分割","arguments":{"segmentationState":"NOT_CREATED"}},{"query":"没跑血管分割的病例","arguments":{"segmentationState":"NOT_CREATED"}},{"query":"还没做血管分割的患者","arguments":{"segmentationState":"NOT_CREATED"}},{"query":"哪些病例的分割还没完成","arguments":{"segmentationState":"NOT_COMPLETED"}},{"query":"分割正在处理的病例","arguments":{"segmentationState":"IN_PROGRESS"}},{"query":"正在分割的病例","arguments":{"segmentationState":"IN_PROGRESS"}},{"query":"分割失败的病例","arguments":{"segmentationState":"FAILED"}},{"query":"最近有哪些任务失败","arguments":{"segmentationState":"FAILED"}},{"query":"分割成功的病例","arguments":{"segmentationState":"SUCCESS"}},{"query":"最近一个月右眼分割失败的病例","arguments":{"segmentationState":"FAILED","dateWindow":"LAST_30_DAYS","eyeSide":"RIGHT"}},{"query":"最近七天左眼病例","arguments":{"segmentationState":"ANY","dateWindow":"LAST_7_DAYS","eyeSide":"LEFT"}},{"query":"今天的双眼病例","arguments":{"segmentationState":"ANY","dateWindow":"TODAY","eyeSide":"BOTH"}},{"query":"今天有哪些待审核结果","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REVIEW","dateWindow":"TODAY"}},{"query":"列出待审核病例","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REVIEW"}},{"query":"哪些结果已经审核但还没有签发","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REPORT"}},{"query":"已审核但PDF还没出的病例","arguments":{"segmentationState":"ANY","clinicalState":"PENDING_REPORT"}},{"query":"继续","arguments":{}},{"query":"上一页","arguments":{}},{"query":"只看失败的","arguments":{}}]',
       '["我有多少名患者","查看第三个病例","比较最近两次结果","血管面积占比是什么意思"]',
       '调用病例分页查询工具，按更新时间倒序返回当前医生有权访问的病例，每页最多十条。',
       '给出总数和当前页摘要，引导用户翻页或查看具体病例。',
       '{"empty":"没有找到符合当前条件的病例","expired":"查询上下文已过期，请重新查询病例列表"}'
FROM agent_skill WHERE skill_code = 'ASSIGNED_CASE_SEARCH';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 3,
       '[{"query":"查看病例 C20260926083238176 的摘要","arguments":{}},{"query":"查看匿名患者 PT-XE6V-93SR 的病例进度","arguments":{}},{"query":"查看第三个","arguments":{}},{"query":"查看第3个病例","arguments":{}},{"query":"打开第三个病例","arguments":{}},{"query":"看一下第二个病例","arguments":{}},{"query":"查看第十个","arguments":{}}]',
       '["查询我负责的病例","我有多少名患者","比较它最近两次结果","什么是血管分割"]',
       '定位当前医生可访问的病例并返回匿名结构化摘要。',
       '仅说明流程、质量和任务状态。',
       '{"notFound":"未找到可访问的病例","expired":"查询上下文已过期，请重新查询病例列表"}'
FROM agent_skill WHERE skill_code = 'CASE_CLINICAL_SUMMARY';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 3,
       '[{"query":"比较它最近两次结果","arguments":{}},{"query":"查看这个病例的历史分析时间线","arguments":{}},{"query":"血管面积比例有什么变化","arguments":{}},{"query":"对比这个病例前后两次结果","arguments":{}},{"query":"这个病例的趋势怎么样","arguments":{}},{"query":"和上一次结果相比有什么变化","arguments":{}}]',
       '["查询我负责的病例","哪些病例还没有分割","为什么要做血管分割","查看第三个病例"]',
       '查询同一病例、同一眼别、同类任务的时间线或结果比较。',
       '只解释结构化变化，明确质量和模型版本影响。',
       '{"insufficient":"当前病例没有足够的同类历史结果可供比较"}'
FROM agent_skill WHERE skill_code = 'CASE_FOLLOWUP_ANALYSIS';

INSERT INTO agent_skill_version
    (skill_id, version, routing_examples_json, routing_negative_examples_json,
     workflow_prompt, answer_style, error_prompts_json)
SELECT id, 3,
       '[{"query":"为什么要做视网膜血管分割","arguments":{}},{"query":"血管面积占比是什么意思","arguments":{}},{"query":"图像质量低有什么影响","arguments":{}},{"query":"质量评分应该怎么理解","arguments":{}},{"query":"什么是眼底血管分割","arguments":{}},{"query":"FAIL评分代表什么","arguments":{}},{"query":"血管分割有什么作用","arguments":{}}]',
       '["查询我负责的病例","查看第三个病例","今天有哪些待审核结果","比较最近两次结果"]',
       '只根据检索到的医生受众知识片段回答并保留引用。',
       '使用医学解释性语言，不形成诊断结论。',
       '{"empty":"知识库未检索到足够依据"}'
FROM agent_skill WHERE skill_code = 'MEDICAL_KNOWLEDGE_QA';
