package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.enumeration.ImageQualityStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图像文件列表项视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFileItemVO {
    
    /** 图像ID */
    private Long id;
    
    /** 所属病例ID */
    private Long caseId;
    
    /** 原始文件名 */
    private String originalFilename;
    
    /** 文件MIME类型，如 image/jpeg */
    private String fileType;
    
    /** 文件大小，单位字节 */
    private Long fileSize;
    
    /** 存储桶 */
    private String storageBucket;
    
    /** 文件存储路径或对象Key */
    private String storageObjectKey;
    
    /** 预览地址 */
    private String previewUrl;
    
    /** 图像宽度 */
    private Integer imageWidth;
    
    /** 图像高度 */
    private Integer imageHeight;
    
    /** 状态：UPLOADED, BOUND_TASK, DELETED */
    private ImageStatus status;
    private ImageQualityStatus qualityStatus;
    private Double qualityScore;
    private Long qualityResultId;
    private LocalDateTime qualityCheckedAt;
    
    /** 上传用户ID */
    private Long uploadedBy;
    
    /** 上传用户名 */
    private String uploadedByName;
    
    /** 上传时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime uploadedAt;
}
