package com.example.retinavision.pojo.analysis.vo;

import com.example.retinavision.enumeration.analysis.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RetryTaskVO {
    private Long id;
    private String taskNo;
    @JsonProperty("status")
    private TaskStatus taskStatus;
    private Integer retryCount;
}
