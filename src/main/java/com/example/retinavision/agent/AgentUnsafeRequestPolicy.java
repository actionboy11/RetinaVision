package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AgentUnsafeRequestPolicy {
    private static final List<String> BYPASS_INTENTS = List.of(
            "忽略规则", "忽略权限", "绕过权限", "无视规则", "无视系统", "不遵守规则");
    private static final List<String> EXPLANATION_INTENTS = List.of(
            "为什么", "是什么", "什么意思", "请解释", "如何理解", "能否说明", "有什么作用");
    private static final List<String> EXPLICIT_WRITE_INTENTS = List.of(
            "保存审核", "签发这", "执行签发", "帮我签发");
    private static final List<String> READ_INTENTS = List.of(
            "查看", "展示", "输出", "读取", "获取", "给我", "列出");
    private static final List<String> PROTECTED_TARGETS = List.of(
            "其他患者", "别的患者", "所有患者", "其他医生", "未签发报告", "未审核报告",
            "任务日志", "文件路径", "原始mask", "maskurl");

    public void requireAllowed(String question, UserRole role) {
        if (role == null || role == UserRole.ADMIN || question == null || question.isBlank()) return;
        String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (containsAny(normalized, BYPASS_INTENTS)) reject();
        if (containsAny(normalized, EXPLANATION_INTENTS)) return;
        if (isWriteRequest(normalized)
                || containsAny(normalized, READ_INTENTS) && containsAny(normalized, PROTECTED_TARGETS)) {
            reject();
        }
    }

    private boolean containsAny(String value, List<String> candidates) {
        return candidates.stream().anyMatch(value::contains);
    }

    private boolean isWriteRequest(String value) {
        return containsAny(value, EXPLICIT_WRITE_INTENTS)
                || value.matches(".*(创建|删除|取消|重试|修改|改派).*(任务|病例|检查|医生).*");
    }

    private void reject() {
        throw new BaseException(ErrorMessageSignal.FORBIDDEN,
                "智能助手仅支持当前账号权限范围内的只读查询");
    }
}
