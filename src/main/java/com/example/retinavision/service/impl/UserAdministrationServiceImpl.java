package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.DTO.UpdateUserRoleDTO;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.UserAdministrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import com.example.retinavision.pojo.VO.AdminUserItemVO;

@Service
// 用户管理服务实现类，提供用户角色分配和用户列表查询功能
public class UserAdministrationServiceImpl implements UserAdministrationService {
    private final UserRegisterMapper userMapper;
    private final CaseMapper caseMapper;

    public UserAdministrationServiceImpl(UserRegisterMapper userMapper, CaseMapper caseMapper) {
        this.userMapper = userMapper;
        this.caseMapper = caseMapper;
    }

    @Override
    @Transactional
    // 分配用户角色，包括医生、研究者和普通用户
    public void assignRole(Integer userId, UpdateUserRoleDTO request, Integer assignedBy) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "用户不存在");
        }
        if (request == null || request.getRoleCode() == null || request.getRoleCode() == UserRole.ADMIN) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "仅可授予 USER、DOCTOR 或 RESEARCHER 角色");
        }
        if (user.getRoleCode() == UserRole.DOCTOR && request.getRoleCode() != UserRole.DOCTOR) {
            Long assignedCases = caseMapper.selectCount(new LambdaQueryWrapper<CaseEntity>()
                    .eq(CaseEntity::getAssignedDoctorId, userId)
                    .isNull(CaseEntity::getDeletedAt));
            if (assignedCases != null && assignedCases > 0) {
                throw new BaseException(ErrorMessageSignal.CONFLICT, "该医生仍有负责病例，不能变更角色");
            }
        }

        String professionalNo = StringUtils.hasText(request.getProfessionalNo())
                ? request.getProfessionalNo().trim() : null;
        if (request.getRoleCode() == UserRole.DOCTOR && professionalNo == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "授予医生角色时必须填写医生工号或执业标识");
        }
        if (professionalNo != null) {
            //ne , "id", userId) 确保在更新时不会与自身冲突 相当于 SQL 中的 WHERE professional_no = ? AND id != ?
            UserEntity existing = userMapper.selectOne(new QueryWrapper<UserEntity>()
                    .eq("professional_no", professionalNo)
                    .ne("id", userId));
            if (existing != null) {
                throw new BaseException(ErrorMessageSignal.CONFLICT, "医生工号或执业标识已被使用");
            }
        }

        user.setRoleCode(request.getRoleCode());
        user.setProfessionalNo(request.getRoleCode() == UserRole.DOCTOR ? professionalNo : null);
        user.setRoleAssignedBy(assignedBy);
        user.setRoleAssignedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // 获取所有用户列表，包括用户ID、用户名、真实姓名、角色、医生工号、角色分配者和分配时间
    @Override public List<AdminUserItemVO> listUsers() {
        return userMapper.selectList(null).stream().map(user -> AdminUserItemVO.builder()
                .id(user.getId()).username(user.getUsername()).realName(user.getRealName())
                .roleCode(user.getRoleCode()).professionalNo(user.getProfessionalNo())
                .roleAssignedBy(user.getRoleAssignedBy()).roleAssignedAt(user.getRoleAssignedAt())
                .status(user.getStatus()).build()).toList();
    }
}
