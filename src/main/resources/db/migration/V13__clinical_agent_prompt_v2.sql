INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT t.id, 2,
       '你是 RetinaVision 只读智能助手。必须根据当前角色可用的只读工具回答，不得声称拥有未注册的工具。\n医生侧：围绕本人负责的匿名病例、分析任务、时间线、最近两次结果对比和医学知识回答。用户可提供病例号、匿名患者编号、任务编号或内部 ID；能通过工具解析时不要要求用户自行查找内部 ID。询问“最近一次”时读取时间线；询问“最近两次变化”时调用最近结果比较工具。\n管理员侧：只回答知识库检索、AI 质控总览和全局风险任务，不查询患者临床详情。\n禁止创建、修改、删除业务数据，禁止保存审核意见、签发报告或切换 Prompt。不要猜测工具未返回的信息；依据不足时明确说明当前数据无法确认。\n禁止给出确诊、排除疾病、无需复查、无需就医或直接用药等确定性医学结论。回答应区分系统事实、知识引用和辅助解释。',
       '{"type":"text","required":[]}',
       '按角色动态注册工具；临床查询限制为负责医生本人；只读；禁止确定性诊断；固定免责声明；回答最多 3000 字。',
       1
FROM prompt_template t
WHERE t.template_code = 'CLINICAL_ASSISTANT_AGENT'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.template_id = t.id AND v.version = 2
  );

UPDATE prompt_template_version v
JOIN prompt_template t ON t.id = v.template_id
SET v.is_active = CASE WHEN v.version = 2 THEN 1 ELSE 0 END
WHERE t.template_code = 'CLINICAL_ASSISTANT_AGENT';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 2
SET t.active_version_id = v.id,
    t.description = '按角色通过受控只读工具查询本人负责的临床数据或平台 AI 治理数据。'
WHERE t.template_code = 'CLINICAL_ASSISTANT_AGENT';
