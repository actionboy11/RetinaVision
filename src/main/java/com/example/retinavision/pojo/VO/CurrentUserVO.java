package com.example.retinavision.pojo.VO;

import com.example.retinavision.enumeration.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserVO {
    private Integer id;
    private String username;
    private String realName;
    private UserRole roleCode;
}
