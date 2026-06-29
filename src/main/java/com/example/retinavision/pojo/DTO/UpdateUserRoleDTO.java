package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 更新用户角色DTO类
public class UpdateUserRoleDTO {
    private UserRole roleCode;
    private String professionalNo;
}
