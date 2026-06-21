package com.example.retinavision.pojo.Entity;
import com.baomidou.mybatisplus.annotation.*;
import com.example.retinavision.enumeration.ReportStatus;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("analysis_report") public class AnalysisReportEntity {
 @TableId(value="id",type=IdType.AUTO) private Long id; private Long resultId; private Integer version;
 private ReportStatus status; private Integer correctionVersion; private String draftJson; private String reportObjectKey;
 private String reportSha256; private Integer createdBy; private Integer signedBy; private String signerNameSnapshot;
 private String professionalNoSnapshot; private LocalDateTime signedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
