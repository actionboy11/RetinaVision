package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.DoctorOptionVO;
import com.example.retinavision.service.DoctorDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DoctorDirectoryServiceImpl implements DoctorDirectoryService {
    private final UserRegisterMapper users;

    public DoctorDirectoryServiceImpl(UserRegisterMapper users) {
        this.users = users;
    }

    @Override
    public List<DoctorOptionVO> listActiveDoctors() {
        return users.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getRoleCode, UserRole.DOCTOR)
                        .eq(UserEntity::getStatus, 1)
                        .orderByAsc(UserEntity::getRealName, UserEntity::getId))
                .stream()
                .map(user -> new DoctorOptionVO(user.getId(), displayName(user), user.getProfessionalNo()))
                .toList();
    }

    private String displayName(UserEntity user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : "医生 #" + user.getId();
    }
}
