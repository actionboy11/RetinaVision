package com.example.retinavision.pojo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.ImageStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 病例图像文件实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("image_file")
public class ImageFileEntity {
    
    /** 图像ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 所属病例ID，逻辑关联 medical_case.id */
    private Long caseId;
    
    /** 原始文件名 */
    private String originalFilename;
    
    /** 文件MIME类型，如 image/jpeg */
    private String fileType;
    
    /** 文件大小，单位字节 */
    private Long fileSize;
    
    /** 存储桶，本地存储可为空或 fixed 值 */
    private String storageBucket;
    
    /** 文件存储路径或对象Key */
    private String storageObjectKey;
    
    /** 预览地址，可为空，前端可走 /images/{id}/preview */
    private String previewUrl;
    
    /** 图像宽度 */
    private Integer imageWidth;
    
    /** 图像高度 */
    private Integer imageHeight;
    
    /** 状态：UPLOADED, BOUND_TASK, DELETED */
    private ImageStatus status;
    
    /** 上传用户ID，逻辑关联 sys_user.id */
    private Long uploadedBy;

    /** 上传时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime uploadedAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updatedAt;
    
    /** 软删除时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime deletedAt;
}
