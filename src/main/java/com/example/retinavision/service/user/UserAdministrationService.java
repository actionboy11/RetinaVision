package com.example.retinavision.service.user;

import com.example.retinavision.pojo.user.dto.UpdateUserRoleDTO;
import com.example.retinavision.pojo.user.vo.AdminUserItemVO;
import java.util.List;

public interface UserAdministrationService {
    void assignRole(Integer userId, UpdateUserRoleDTO request, Integer assignedBy);
    List<AdminUserItemVO> listUsers();
}
