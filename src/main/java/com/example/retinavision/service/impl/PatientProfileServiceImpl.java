package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.PatientProfileSource;
import com.example.retinavision.enumeration.PatientProfileStatus;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.PatientProfileMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.PatientAccountLinkDTO;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.LegacyPatientProfileVO;
import com.example.retinavision.pojo.VO.PatientProfileVO;
import com.example.retinavision.service.PatientProfileService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PatientProfileServiceImpl implements PatientProfileService {
    private static final int GENERATION_ATTEMPTS = 8;
    private final PatientProfileMapper profiles;
    private final UserRegisterMapper users;
    private final CaseMapper cases;
    private final SecurePatientNumberGenerator numbers;

    public PatientProfileServiceImpl(PatientProfileMapper profiles, UserRegisterMapper users,
                                     CaseMapper cases, SecurePatientNumberGenerator numbers) {
        this.profiles = profiles;
        this.users = users;
        this.cases = cases;
        this.numbers = numbers;
    }

    @Override
    @Transactional
    public PatientProfileEntity getOrCreateAccountProfile(Integer userId) {
        PatientProfileEntity current = profiles.selectByAccountUserId(userId);
        if (current != null) return current;
        return create(PatientProfileSource.ACCOUNT, userId, userId);
    }

    @Override
    public PatientProfileVO getMyProfile(CurrentUserVO user) {
        requireRole(user, UserRole.USER, "仅患者可以查看本人患者档案");
        return toVO(getOrCreateAccountProfile(user.getId()));
    }

    @Override
    @Transactional
    public PatientProfileVO createOffline(CurrentUserVO doctor) {
        requireRole(doctor, UserRole.DOCTOR, "仅医生可以创建线下患者");
        return toVO(create(PatientProfileSource.OFFLINE, null, doctor.getId()));
    }

    @Override
    public List<PatientProfileVO> listForDoctor(CurrentUserVO doctor) {
        requireRole(doctor, UserRole.DOCTOR, "仅医生可以查看患者列表");
        return profiles.selectAccessibleByDoctor(doctor.getId()).stream().map(this::toVO).toList();
    }

    @Override
    public PatientProfileEntity requireDoctorAccessible(Long patientId, Integer doctorId) {
        PatientProfileEntity profile = profiles.selectById(patientId);
        if (profile == null || profile.getStatus() != PatientProfileStatus.ACTIVE) notFound();
        boolean created = Objects.equals(profile.getCreatedBy(), doctorId);
        boolean assigned = cases.selectCount(new LambdaQueryWrapper<CaseEntity>()
                .eq(CaseEntity::getPatientId, patientId)
                .eq(CaseEntity::getAssignedDoctorId, doctorId)) > 0;
        if (!created && !assigned) notFound();
        return profile;
    }

    @Override
    public List<LegacyPatientProfileVO> listLegacy(CurrentUserVO administrator) {
        requireRole(administrator, UserRole.ADMIN, "仅管理员可以处理历史患者档案");
        return profiles.selectLegacyProfiles();
    }

    @Override
    @Transactional
    public PatientProfileVO linkLegacy(Long patientId, PatientAccountLinkDTO request,
                                       CurrentUserVO administrator) {
        requireRole(administrator, UserRole.ADMIN, "仅管理员可以处理历史患者档案");
        if (request == null || request.userId() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "请选择患者账号");
        }
        PatientProfileEntity legacy = profiles.selectById(patientId);
        UserEntity account = users.selectById(request.userId());
        if (legacy == null || legacy.getSource() != PatientProfileSource.LEGACY) notFound();
        if (account == null || account.getRoleCode() != UserRole.USER || account.getStatus() == null
                || account.getStatus() != 1) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "目标账号不是有效患者账号");
        }
        PatientProfileEntity existing = profiles.selectByAccountUserId(account.getId());
        if (existing != null && !Objects.equals(existing.getId(), legacy.getId())) {
            Long existingCaseCount = cases.selectCount(new LambdaQueryWrapper<CaseEntity>()
                    .eq(CaseEntity::getPatientId, existing.getId()));
            if (existingCaseCount != null && existingCaseCount > 0) {
                throw new BaseException(ErrorMessageSignal.CONFLICT,
                        "该患者账号的现有档案已经包含病例，不能自动合并");
            }
            profiles.deleteById(existing.getId());
        }
        legacy.setAccountUserId(account.getId());
        legacy.setUpdatedAt(LocalDateTime.now());
        profiles.updateById(legacy);
        return toVO(legacy);
    }

    private PatientProfileEntity create(PatientProfileSource source, Integer accountUserId, Integer createdBy) {
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String patientNo = numbers.next();
            if (profiles.selectByPatientNo(patientNo) != null) continue;
            LocalDateTime now = LocalDateTime.now();
            PatientProfileEntity profile = new PatientProfileEntity();
            profile.setPatientNo(patientNo);
            profile.setAccountUserId(accountUserId);
            profile.setSource(source);
            profile.setCreatedBy(createdBy);
            profile.setStatus(PatientProfileStatus.ACTIVE);
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            try {
                profiles.insert(profile);
                return profile;
            } catch (DuplicateKeyException ignored) {
                // Retry a generated-number collision. Account uniqueness is checked before this method.
            }
        }
        throw new BaseException(ErrorMessageSignal.CONFLICT, "匿名患者编号生成失败，请重试");
    }

    private PatientProfileVO toVO(PatientProfileEntity profile) {
        return new PatientProfileVO(profile.getId(), profile.getPatientNo(), profile.getSource(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }

    private void requireRole(CurrentUserVO user, UserRole role, String message) {
        if (user == null || user.getRoleCode() != role) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, message);
        }
    }

    private void notFound() {
        throw new BaseException(ErrorMessageSignal.NOT_FOUND, "资源不存在");
    }
}
