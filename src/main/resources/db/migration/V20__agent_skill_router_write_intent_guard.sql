INSERT INTO prompt_template_version
    (template_id, version, system_prompt, output_contract, safety_policy, is_active)
SELECT t.id, 2,
       '你是 RetinaVision 的 Skill 路由器。你只负责根据用户问题和提供的 Skill 目录选择一个 skillCode 并提取 arguments，不回答业务问题，不调用任何工具。\n只能选择目录中存在的 Skill 和参数枚举；信息不足时降低 confidence。\n当用户要求创建、取消、重试、修改、删除、保存审核、签发报告或其他写操作时，选择语义最接近的只读 Skill，但 confidence 必须不高于 0.2，arguments 返回空对象；不得把写操作伪装成只读查询。\n只返回 JSON object，字段必须为 skillCode、confidence、arguments。',
       '{"type":"json_object","required":["skillCode","confidence","arguments"],"properties":{"skillCode":{"type":"string"},"confidence":{"type":"number","minimum":0,"maximum":1},"arguments":{"type":"object"}}}',
       '无工具；只允许目录内只读 Skill；写操作必须返回不高于 0.2 的 confidence；不得生成业务答案或任意 URL。',
       1
FROM prompt_template t
WHERE t.template_code = 'AGENT_SKILL_ROUTER';

UPDATE prompt_template_version v
JOIN prompt_template t ON t.id = v.template_id
SET v.is_active = CASE WHEN v.version = 2 THEN 1 ELSE 0 END
WHERE t.template_code = 'AGENT_SKILL_ROUTER';

UPDATE prompt_template t
JOIN prompt_template_version v ON v.template_id = t.id AND v.version = 2
SET t.active_version_id = v.id,
    t.updated_at = CURRENT_TIMESTAMP
WHERE t.template_code = 'AGENT_SKILL_ROUTER';
