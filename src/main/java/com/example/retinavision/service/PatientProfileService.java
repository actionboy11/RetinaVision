package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.PatientAccountLinkDTO;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.LegacyPatientProfileVO;
import com.example.retinavision.pojo.VO.PatientProfileVO;

import java.util.List;

public interface PatientProfileService {
    PatientProfileEntity getOrCreateAccountProfile(Integer userId);
    PatientProfileVO getMyProfile(CurrentUserVO user);
    PatientProfileVO createOffline(CurrentUserVO doctor);
    List<PatientProfileVO> listForDoctor(CurrentUserVO doctor);
    PatientProfileEntity requireDoctorAccessible(Long patientId, Integer doctorId);
    List<LegacyPatientProfileVO> listLegacy(CurrentUserVO administrator);
    PatientProfileVO linkLegacy(Long patientId, PatientAccountLinkDTO request, CurrentUserVO administrator);
}
