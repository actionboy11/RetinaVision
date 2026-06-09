package com.example.retinavision.pojo.DTO;

import com.example.retinavision.enumeration.UserRole;
import lombok.Data;


@Data
public class UserRegisterDTO {
    private String username;
    private String password;
    private String realName;
    private UserRole roleCode;

}
