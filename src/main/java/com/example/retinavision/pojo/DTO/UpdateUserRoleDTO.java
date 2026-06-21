package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleDTO {
    private UserRole roleCode;
    private String professionalNo;
}
