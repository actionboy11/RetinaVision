package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.UpdateUserRoleDTO;
import com.example.retinavision.pojo.VO.AdminUserItemVO;
import java.util.List;

public interface UserAdministrationService {
    void assignRole(Integer userId, UpdateUserRoleDTO request, Integer assignedBy);
    List<AdminUserItemVO> listUsers();
}
