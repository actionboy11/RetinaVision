package com.example.retinavision.controller;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.pojo.DTO.UpdateActivePromptVersionDTO;
import com.example.retinavision.pojo.Entity.LlmCallLogEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.LlmCallLogVO;
import com.example.retinavision.pojo.VO.PromptTemplateVO;
import com.example.retinavision.pojo.VO.PromptTemplateVersionVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.LlmCallLogService;
import com.example.retinavision.service.PromptTemplateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class PromptManagementController {
    private final PromptTemplateService templates;
    private final LlmCallLogService logs;

    public PromptManagementController(PromptTemplateService templates, LlmCallLogService logs) {
        this.templates = templates;
        this.logs = logs;
    }

    @GetMapping("/prompt-templates")
    public Result<List<PromptTemplateVO>> templates(Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(templates.listTemplates().stream().map(this::toTemplate).toList());
    }

    @GetMapping("/prompt-templates/{templateCode}/versions")
    public Result<List<PromptTemplateVersionVO>> versions(@PathVariable String templateCode,
                                                           Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(templates.listVersions(templateCode).stream().map(this::toVersion).toList());
    }

    @PutMapping("/prompt-templates/{templateCode}/active-version")
    public Result<Boolean> activateVersion(@PathVariable String templateCode,
                                           @RequestBody UpdateActivePromptVersionDTO request,
                                           Authentication authentication) {
        requireAdmin(authentication);
        if (request == null || request.versionId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择要启用的 Prompt 版本");
        }
        templates.activateVersion(templateCode, request.versionId());
        return Result.success(true);
    }

    @GetMapping("/llm-call-logs")
    public Result<List<LlmCallLogVO>> callLogs(
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Authentication authentication) {
        requireAdmin(authentication);
        return Result.success(logs.list(scenario, templateCode, success, startTime, endTime)
                .stream().map(this::toLog).toList());
    }

    private PromptTemplateVO toTemplate(PromptTemplateEntity template) {
        Integer activeVersion = templates.listVersions(template.getTemplateCode()).stream()
                .filter(version -> template.getActiveVersionId() != null
                        && template.getActiveVersionId().equals(version.getId()))
                .map(PromptTemplateVersionEntity::getVersion)
                .findFirst()
                .orElse(null);
        return new PromptTemplateVO(template.getTemplateCode(), template.getName(), template.getScenario(),
                template.getDescription(), template.getStatus(), activeVersion, template.getUpdatedAt());
    }

    private PromptTemplateVersionVO toVersion(PromptTemplateVersionEntity version) {
        return new PromptTemplateVersionVO(version.getId(), version.getTemplateCode(), version.getVersion(),
                version.getSystemPrompt(), version.getOutputContract(), version.getSafetyPolicy(), version.getActive(),
                version.getCreatedBy(), version.getCreatedAt());
    }

    private LlmCallLogVO toLog(LlmCallLogEntity log) {
        return new LlmCallLogVO(log.getId(), log.getScenario(), log.getTemplateCode(), log.getTemplateVersion(),
                log.getProvider(), log.getModel(), log.getSuccess(), log.getLatencyMs(), log.getErrorSummary(),
                log.getCreatedAt());
    }

    private void requireAdmin(Authentication authentication) {
        CurrentUserVO user = (CurrentUserVO) authentication.getPrincipal();
        if (user.getRoleCode() != UserRole.ADMIN) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权管理 Prompt 模板");
        }
    }
}
