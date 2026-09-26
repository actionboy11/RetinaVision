package com.example.retinavision.agent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultAgentSkillRouter implements AgentSkillRouter {
    private static final Pattern INDEX = Pattern.compile("查看第([一二三四五六七八九十\\d]+)个");

    @Override
    public AgentSkillRoute route(String question, AgentSkillCode currentSkill) {
        String value = question == null ? "" : question.trim();
        if (value.matches("^(继续|下一页|再来一页)[。！!？?]?$")) {
            return command(currentSkill, AgentContextCommand.NEXT_PAGE, null);
        }
        if (value.matches("^(上一页|返回上一页)[。！!？?]?$")) {
            return command(currentSkill, AgentContextCommand.PREVIOUS_PAGE, null);
        }
        if (value.contains("只看失败")) {
            return command(AgentSkillCode.ASSIGNED_CASE_SEARCH, AgentContextCommand.FILTER_FAILED, null);
        }
        Matcher index = INDEX.matcher(value);
        if (index.find()) {
            return command(AgentSkillCode.CASE_CLINICAL_SUMMARY, AgentContextCommand.SELECT_INDEX,
                    parseIndex(index.group(1)));
        }
        if (containsAny(value, "最近两次", "历史结果", "时间线", "有什么变化", "结果比较")) {
            return new AgentSkillRoute(AgentSkillCode.CASE_FOLLOWUP_ANALYSIS, 0.98, Map.of());
        }
        if (containsAny(value, "为什么", "是什么意思", "有什么作用", "医学知识", "如何理解")) {
            return new AgentSkillRoute(AgentSkillCode.MEDICAL_KNOWLEDGE_QA, 0.92, Map.of());
        }
        if (containsAny(value, "多少名患者", "多少个患者", "多少病人", "多少个病人", "多少个病例",
                "工作量", "待审核", "待签发")) {
            return new AgentSkillRoute(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW, 0.98, Map.of());
        }
        if (containsAny(value, "病例", "患者", "病人", "负责")) {
            Map<String, String> arguments = new LinkedHashMap<>();
            if (containsAny(value, "还没有进行分割", "还没有分割", "还没做分割", "未进行分割", "没有创建分割")) {
                arguments.put("segmentationState", SegmentationState.NOT_CREATED.name());
            } else if (containsAny(value, "分割还没完成", "分割未完成", "未完成分割")) {
                arguments.put("segmentationState", SegmentationState.NOT_COMPLETED.name());
            } else if (containsAny(value, "分割失败", "失败的")) {
                arguments.put("segmentationState", SegmentationState.FAILED.name());
            } else {
                arguments.put("segmentationState", SegmentationState.ANY.name());
            }
            return new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.9, arguments);
        }
        return new AgentSkillRoute(AgentSkillCode.MEDICAL_KNOWLEDGE_QA, 0.55, Map.of());
    }

    private AgentSkillRoute command(AgentSkillCode skill, AgentContextCommand command, Integer selectedIndex) {
        AgentSkillCode resolved = skill == null ? AgentSkillCode.ASSIGNED_CASE_SEARCH : skill;
        return new AgentSkillRoute(resolved, 1, Map.of(), command, selectedIndex);
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private int parseIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return switch (value) {
                case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4; case "五" -> 5;
                case "六" -> 6; case "七" -> 7; case "八" -> 8; case "九" -> 9; case "十" -> 10;
                default -> -1;
            };
        }
    }
}
