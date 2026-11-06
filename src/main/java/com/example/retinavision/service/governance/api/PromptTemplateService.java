package com.example.retinavision.service.governance.api;

import com.example.retinavision.pojo.governance.entity.PromptTemplateEntity;
import com.example.retinavision.pojo.governance.entity.PromptTemplateVersionEntity;

import java.util.List;

public interface PromptTemplateService {
    PromptTemplateVersionEntity requireActiveVersion(String templateCode);

    PromptTemplateVersionEntity requireVersion(String templateCode, Long versionId);

    List<PromptTemplateEntity> listTemplates();

    List<PromptTemplateVersionEntity> listVersions(String templateCode);

    PromptTemplateEntity activateVersion(String templateCode, Long versionId);
}
