package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_evaluation_dataset")
public class AgentEvaluationDatasetEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String datasetCode;
    private String name;
    private String targetRole;
    private Integer version;
    private String status;
    private Integer expectedCaseCount;
    private String description;
    private LocalDateTime createdAt;
}
