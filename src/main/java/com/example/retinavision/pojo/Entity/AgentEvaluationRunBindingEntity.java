package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_evaluation_run_binding")
public class AgentEvaluationRunBindingEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long evaluationRunId;
    private String bindingType;
    private String bindingCode;
    private Long versionId;
    private String versionLabel;
    private LocalDateTime createdAt;
}
