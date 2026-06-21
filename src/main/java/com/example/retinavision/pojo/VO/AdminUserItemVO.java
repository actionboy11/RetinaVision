package com.example.retinavision.pojo.VO;
import com.example.retinavision.enumeration.UserRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data @Builder public class AdminUserItemVO { private Integer id;private String username;private String realName;private UserRole roleCode;private String professionalNo;private Integer roleAssignedBy;private LocalDateTime roleAssignedAt;private Integer status; }
