package com.example.retinavision.service;

import com.example.retinavision.pojo.Entity.PromptTemplateEntity;
import com.example.retinavision.pojo.Entity.PromptTemplateVersionEntity;

import java.util.List;

public interface PromptTemplateService {
    PromptTemplateVersionEntity requireActiveVersion(String templateCode);

    List<PromptTemplateEntity> listTemplates();

    List<PromptTemplateVersionEntity> listVersions(String templateCode);

    PromptTemplateEntity activateVersion(String templateCode, Long versionId);
}
