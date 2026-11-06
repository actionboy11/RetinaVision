package com.example.retinavision.service.patient;

import com.example.retinavision.pojo.patient.dto.PatientAccountLinkDTO;
import com.example.retinavision.pojo.patient.entity.PatientProfileEntity;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.patient.vo.LegacyPatientProfileVO;
import com.example.retinavision.pojo.patient.vo.PatientProfileVO;

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
