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
        if (containsAny(value, "待审核") && containsAny(value, "哪些", "列表", "查看", "今天", "最近")) {
            Map<String, String> arguments = searchArguments(value);
            arguments.put("clinicalState", "PENDING_REVIEW");
            return new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.98, arguments);
        }
        if (containsAny(value, "待签发", "没有签发", "还没签发", "未签发")) {
            Map<String, String> arguments = searchArguments(value);
            arguments.put("clinicalState", "PENDING_REPORT");
            return new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.98, arguments);
        }
        if (containsAny(value, "多少名患者", "多少个患者", "多少病人", "多少个病人", "多少个病例",
                "工作量", "总览")) {
            return new AgentSkillRoute(AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW, 0.98, Map.of());
        }
        if (containsAny(value, "病例", "患者", "病人", "负责", "任务失败", "分割任务")) {
            return new AgentSkillRoute(AgentSkillCode.ASSIGNED_CASE_SEARCH, 0.9, searchArguments(value));
        }
        return new AgentSkillRoute(AgentSkillCode.MEDICAL_KNOWLEDGE_QA, 0.55, Map.of());
    }

    private Map<String, String> searchArguments(String value) {
        Map<String, String> arguments = new LinkedHashMap<>();
        if (containsAny(value, "还没有进行分割", "还没有分割", "还没做分割", "未进行分割", "没有创建分割")) {
            arguments.put("segmentationState", SegmentationState.NOT_CREATED.name());
        } else if (containsAny(value, "分割还没完成", "分割未完成", "未完成分割")) {
            arguments.put("segmentationState", SegmentationState.NOT_COMPLETED.name());
        } else if (containsAny(value, "分割失败", "失败的", "任务失败")) {
            arguments.put("segmentationState", SegmentationState.FAILED.name());
        } else if (containsAny(value, "进行中", "处理中")) {
            arguments.put("segmentationState", SegmentationState.IN_PROGRESS.name());
        } else if (containsAny(value, "分割成功", "已经分割", "已完成分割")) {
            arguments.put("segmentationState", SegmentationState.SUCCESS.name());
        } else {
            arguments.put("segmentationState", SegmentationState.ANY.name());
        }

        if (containsAny(value, "今天", "今日")) arguments.put("dateWindow", "TODAY");
        else if (containsAny(value, "最近七天", "近七天", "最近一周", "近一周")) {
            arguments.put("dateWindow", "LAST_7_DAYS");
        } else if (containsAny(value, "最近一个月", "近一个月", "最近30天", "近30天")) {
            arguments.put("dateWindow", "LAST_30_DAYS");
        }

        if (containsAny(value, "左眼")) arguments.put("eyeSide", "LEFT");
        else if (containsAny(value, "右眼")) arguments.put("eyeSide", "RIGHT");
        else if (containsAny(value, "双眼")) arguments.put("eyeSide", "BOTH");
        return arguments;
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
