package com.example.retinavision.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentContextCommandParser {
    private static final Pattern INDEX = Pattern.compile("(?:查看|打开|看一下)第?([一二三四五六七八九十\\d]+)个");

    public boolean isSelectionCommand(String question) {
        return question != null && INDEX.matcher(question.trim()).find();
    }

    public Optional<AgentSkillRoute> parse(String question, AgentQueryContextSnapshot context) {
        if (context == null) return Optional.empty();
        String value = question == null ? "" : question.trim();
        if (value.matches("^(继续|下一页|再来一页)[。！!？?]?$")) {
            return Optional.of(command(context.currentSkill(), AgentContextCommand.NEXT_PAGE, null));
        }
        if (value.matches("^(上一页|返回上一页)[。！!？?]?$")) {
            return Optional.of(command(context.currentSkill(), AgentContextCommand.PREVIOUS_PAGE, null));
        }
        if (value.contains("只看失败")) {
            AgentSkillCode skill = context.referenceType() == AgentReferenceType.TASK
                    ? AgentSkillCode.DOCTOR_TASK_SEARCH : AgentSkillCode.ASSIGNED_CASE_SEARCH;
            return Optional.of(command(skill, AgentContextCommand.FILTER_FAILED, null));
        }
        Matcher matcher = INDEX.matcher(value);
        if (!matcher.find()) return Optional.empty();
        AgentSkillCode skill = switch (context.referenceType()) {
            case TASK -> AgentSkillCode.DOCTOR_TASK_SEARCH;
            case CLINICAL_QUEUE -> AgentSkillCode.DOCTOR_CLINICAL_QUEUE;
            case CASE -> AgentSkillCode.CASE_CLINICAL_SUMMARY;
        };
        return Optional.of(command(skill, AgentContextCommand.SELECT_INDEX, parseIndex(matcher.group(1))));
    }

    private AgentSkillRoute command(AgentSkillCode skill, AgentContextCommand command, Integer selectedIndex) {
        return new AgentSkillRoute(skill, 1, Map.of(), command, selectedIndex);
    }

    private int parseIndex(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) {
            return switch (value) {
                case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4; case "五" -> 5;
                case "六" -> 6; case "七" -> 7; case "八" -> 8; case "九" -> 9; case "十" -> 10;
                default -> -1;
            };
        }
    }
}
