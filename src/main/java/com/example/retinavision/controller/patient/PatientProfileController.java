package com.example.retinavision.controller.patient;

import com.example.retinavision.pojo.patient.dto.PatientAccountLinkDTO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.patient.vo.LegacyPatientProfileVO;
import com.example.retinavision.pojo.patient.vo.PatientProfileVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.patient.api.PatientProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PatientProfileController {
    private final PatientProfileService service;

    public PatientProfileController(PatientProfileService service) {
        this.service = service;
    }

    @GetMapping("/patients/me")
    public Result<PatientProfileVO> me(Authentication authentication) {
        return Result.success(service.getMyProfile(user(authentication)));
    }

    @PostMapping("/patients/offline")
    public Result<PatientProfileVO> createOffline(Authentication authentication) {
        return Result.success(service.createOffline(user(authentication)));
    }

    @GetMapping("/patients")
    public Result<List<PatientProfileVO>> list(Authentication authentication) {
        return Result.success(service.listForDoctor(user(authentication)));
    }

    @GetMapping("/admin/patient-profiles/legacy")
    public Result<List<LegacyPatientProfileVO>> legacy(Authentication authentication) {
        return Result.success(service.listLegacy(user(authentication)));
    }

    @PutMapping("/admin/patient-profiles/{patientId}/account-link")
    public Result<PatientProfileVO> link(@PathVariable Long patientId,
                                         @RequestBody PatientAccountLinkDTO request,
                                         Authentication authentication) {
        return Result.success(service.linkLegacy(patientId, request, user(authentication)));
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }
}
