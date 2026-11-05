package com.example.retinavision.pojo.auth.dto;

import com.example.retinavision.enumeration.user.UserRole;
import lombok.Data;


@Data
public class UserRegisterDTO {
    private String username;
    private String password;
    private String realName;
    private UserRole roleCode;

}
