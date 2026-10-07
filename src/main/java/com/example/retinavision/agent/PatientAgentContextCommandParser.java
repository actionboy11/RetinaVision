package com.example.retinavision.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PatientAgentContextCommandParser {
    private static final Pattern INDEX = Pattern.compile("(?:查看|打开|看一下)第?([一二三四五六七八九十\\d]+)个");

    public boolean isContextCommand(String question) {
        if (question == null) return false;
        String value = question.trim();
        return value.matches("^(继续|下一页|再来一页|上一页|返回上一页)[。！!？?]?$")
                || INDEX.matcher(value).find() || value.contains("这份报告")
                || value.contains("只看需要重新上传");
    }

    public Optional<AgentSkillRoute> parse(String question, PatientAgentQueryContextSnapshot context) {
        if (context == null) return Optional.empty();
        String value = question == null ? "" : question.trim();
        if (value.matches("^(继续|下一页|再来一页)[。！!？?]?$") ) {
            return Optional.of(command(context.currentSkill(), AgentContextCommand.NEXT_PAGE, null, Map.of()));
        }
        if (value.matches("^(上一页|返回上一页)[。！!？?]?$") ) {
            return Optional.of(command(context.currentSkill(), AgentContextCommand.PREVIOUS_PAGE, null, Map.of()));
        }
        if (value.contains("只看需要重新上传")) {
            return Optional.of(command(AgentSkillCode.MY_CASE_LIST, AgentContextCommand.NONE, null,
                    Map.of("reuploadOnly", "TRUE")));
        }
        if (value.contains("解释这份报告")) {
            return Optional.of(command(AgentSkillCode.MY_SIGNED_REPORT, AgentContextCommand.NONE, null,
                    Map.of("mode", "EXPLAIN")));
        }
        Matcher matcher = INDEX.matcher(value);
        if (!matcher.find()) return Optional.empty();
        int index = parseIndex(matcher.group(1));
        if (context.currentSkill() == AgentSkillCode.MY_SIGNED_REPORT || value.contains("报告")) {
            return Optional.of(command(AgentSkillCode.MY_SIGNED_REPORT, AgentContextCommand.SELECT_INDEX,
                    index, Map.of("mode", "VIEW")));
        }
        return Optional.of(command(AgentSkillCode.MY_CASE_PROGRESS, AgentContextCommand.SELECT_INDEX,
                index, Map.of()));
    }

    private AgentSkillRoute command(AgentSkillCode skill, AgentContextCommand command,
                                    Integer index, Map<String, String> arguments) {
        return new AgentSkillRoute(skill, 1, arguments, command, index);
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
