package com.example.retinavision.pojo.Entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.retinavision.enumeration.UserRole;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_user")   // 指定表名
public class UserEntity {
    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;
    private String username;
    private String passwordHash;
    private String realName;
    private UserRole roleCode;
    private String professionalNo;
    private Integer roleAssignedBy;
    private LocalDateTime roleAssignedAt;
    private Integer status;
    //统一格式 yyyy-MM-dd HH:mm:ss
    //数据库中存储格式为 yyyy-MM-dd HH:mm:ss
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime updatedAt;

}
