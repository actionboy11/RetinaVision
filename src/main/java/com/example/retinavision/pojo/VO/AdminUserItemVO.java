package com.example.retinavision.pojo.VO;
import com.example.retinavision.enumeration.UserRole;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@Builder
// 管理员用户列表VO，包含用户ID、用户名、真实姓名、角色、医生工号、角色分配者和分配时间
public class AdminUserItemVO {
    private Integer id;
    private String username;
    private String realName;
    private UserRole roleCode;
    private String professionalNo;
    private Integer roleAssignedBy;
    private LocalDateTime roleAssignedAt;
    private Integer status;
}
