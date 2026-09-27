package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_query_context")
public class AgentQueryContextEntity {
    @TableId
    private Long sessionId;
    private Long skillVersionId;
    private String currentSkillCode;
    private String referenceType;
    private String currentFiltersJson;
    private Integer currentPage;
    private Integer pageSize;
    private Long total;
    private Integer selectedCaseId;
    private Long selectedTaskId;
    private String recentResultReferencesJson;
    private LocalDateTime expiresAt;
    private LocalDateTime updatedAt;
}
