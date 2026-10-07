package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.PromptTemplateMapper;
import com.example.retinavision.mapper.PromptTemplateVersionMapper;
import com.example.retinavision.mapper.PromptEvaluationRunMapper;
import com.example.retinavision.pojo.Entity.PromptEvaluationRunEntity;
import com.example.retinavision.llm.LlmProperties;
import com.example.retinavision.llm.PromptEvaluationService;
import com.example.retinavision.llm.RagEvaluationService;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.QdrantProperties;
import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;
import com.example.retinavision.service.PromptTemplateService;
import com.example.retinavision.agent.evaluation.AgentEvaluationEligibilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {
    private final PromptTemplateMapper templates;
    private final PromptTemplateVersionMapper versions;
    private final PromptEvaluationRunMapper evaluations;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantProperties qdrantProperties;
    private final AgentEvaluationEligibilityService agentEvaluationEligibility;

    @Autowired
    public PromptTemplateServiceImpl(PromptTemplateMapper templates, PromptTemplateVersionMapper versions,
                                     PromptEvaluationRunMapper evaluations, LlmProperties llmProperties,
                                     EmbeddingProperties embeddingProperties, QdrantProperties qdrantProperties,
                                     AgentEvaluationEligibilityService agentEvaluationEligibility) {
        this.templates = templates;
        this.versions = versions;
        this.evaluations = evaluations;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
        this.qdrantProperties = qdrantProperties;
        this.agentEvaluationEligibility = agentEvaluationEligibility;
    }

    public PromptTemplateServiceImpl(PromptTemplateMapper templates, PromptTemplateVersionMapper versions,
                                     PromptEvaluationRunMapper evaluations, LlmProperties llmProperties,
                                     EmbeddingProperties embeddingProperties, QdrantProperties qdrantProperties) {
        this(templates, versions, evaluations, llmProperties, embeddingProperties, qdrantProperties, null);
    }

    @Override
    public PromptTemplateVersionEntity requireActiveVersion(String templateCode) {
        PromptTemplateEntity template = requireTemplate(templateCode);
        if (!"ACTIVE".equals(template.getStatus()) || template.getActiveVersionId() == null) {
            throw unavailable(templateCode, "未配置启用版本");
        }
        PromptTemplateVersionEntity version = versions.selectById(template.getActiveVersionId());
        if (version == null || !template.getId().equals(version.getTemplateId()) || !Boolean.TRUE.equals(version.getActive())) {
            throw unavailable(templateCode, "启用版本无效");
        }
        version.setTemplateCode(template.getTemplateCode());
        return version;
    }

    @Override
    public PromptTemplateVersionEntity requireVersion(String templateCode, Long versionId) {
        PromptTemplateEntity template = requireTemplate(templateCode);
        PromptTemplateVersionEntity version = versions.selectById(versionId);
        if (version == null || !template.getId().equals(version.getTemplateId())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选 Prompt 版本不属于该模板");
        }
        version.setTemplateCode(template.getTemplateCode());
        return version;
    }

    @Override
    public List<PromptTemplateEntity> listTemplates() {
        return templates.selectList(new LambdaQueryWrapper<PromptTemplateEntity>()
                .orderByAsc(PromptTemplateEntity::getTemplateCode));
    }

    @Override
    public List<PromptTemplateVersionEntity> listVersions(String templateCode) {
        PromptTemplateEntity template = requireTemplate(templateCode);
        List<PromptTemplateVersionEntity> result = versions.selectList(
                new LambdaQueryWrapper<PromptTemplateVersionEntity>()
                        .eq(PromptTemplateVersionEntity::getTemplateId, template.getId())
                        .orderByDesc(PromptTemplateVersionEntity::getVersion));
        result.forEach(version -> version.setTemplateCode(templateCode));
        return result;
    }

    @Override
    @Transactional
    public PromptTemplateEntity activateVersion(String templateCode, Long versionId) {
        PromptTemplateEntity template = requireTemplate(templateCode);
        PromptTemplateVersionEntity selected = versions.selectById(versionId);
        if (selected == null || !template.getId().equals(selected.getTemplateId())) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "所选 Prompt 版本不属于该模板");
        }
        if (selected.getId().equals(template.getActiveVersionId())) {
            return template;
        }
        if ("AGENT_SKILL_ROUTER".equals(templateCode)) {
            if (agentEvaluationEligibility == null) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                        "候选路由 Prompt 版本须先通过 Agent 评测");
            }
            agentEvaluationEligibility.requirePromptEligible(templateCode, versionId);
        }
        if (("REPORT_DRAFT_GENERATION".equals(templateCode)
                || RagEvaluationService.TEMPLATE_CODE.equals(templateCode)) && selected.getReleasedAt() == null) {
            PromptEvaluationRunEntity latest = evaluations.selectOne(
                    new LambdaQueryWrapper<PromptEvaluationRunEntity>()
                            .eq(PromptEvaluationRunEntity::getTemplateCode, templateCode)
                            .eq(PromptEvaluationRunEntity::getCandidateVersionId, selected.getId())
                            .orderByDesc(PromptEvaluationRunEntity::getId)
                            .last("LIMIT 1"));
            if (latest == null || !template.getActiveVersionId().equals(latest.getBaselineVersionId())
                    || !"COMPLETED".equals(latest.getStatus())
                    || !Boolean.TRUE.equals(latest.getAutomatedPass())
                    || !"APPROVED".equals(latest.getReviewDecision())
                    || !llmProperties.getProvider().equals(latest.getProvider())
                    || !llmProperties.getModel().equals(latest.getModel())
                    || (RagEvaluationService.TEMPLATE_CODE.equals(templateCode)
                            && (!embeddingProperties.getModel().equals(latest.getEmbeddingModel())
                                    || !Double.valueOf(qdrantProperties.getScoreThreshold())
                                            .equals(latest.getScoreThreshold())))
                    || !(RagEvaluationService.TEMPLATE_CODE.equals(templateCode)
                            ? RagEvaluationService.SAMPLE_VERSION : PromptEvaluationService.SAMPLE_VERSION)
                            .equals(latest.getSampleVersion())) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                        "候选 Prompt 版本须先通过当前版本对照评测并经管理员批准");
            }
        }
        versions.update(null, new LambdaUpdateWrapper<PromptTemplateVersionEntity>()
                .eq(PromptTemplateVersionEntity::getTemplateId, template.getId())
                .set(PromptTemplateVersionEntity::getActive, false));
        selected.setActive(true);
        if (selected.getReleasedAt() == null) {
            selected.setReleasedAt(LocalDateTime.now());
        }
        versions.updateById(selected);
        template.setActiveVersionId(selected.getId());
        template.setUpdatedAt(LocalDateTime.now());
        templates.updateById(template);
        return template;
    }

    private PromptTemplateEntity requireTemplate(String templateCode) {
        PromptTemplateEntity template = templates.selectOne(new LambdaQueryWrapper<PromptTemplateEntity>()
                .eq(PromptTemplateEntity::getTemplateCode, templateCode));
        if (template == null) {
            throw unavailable(templateCode, "模板不存在");
        }
        return template;
    }

    private BaseException unavailable(String templateCode, String reason) {
        return new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE,
                "Prompt 模板 " + templateCode + " " + reason);
    }
}
