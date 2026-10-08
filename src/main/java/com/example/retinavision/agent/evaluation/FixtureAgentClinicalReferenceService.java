package com.example.retinavision.agent.evaluation;

import com.example.retinavision.enumeration.CaseWorkflowStatus;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.PatientGender;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.service.AgentClinicalReferenceService;

import java.time.LocalDateTime;

public class FixtureAgentClinicalReferenceService implements AgentClinicalReferenceService {
    @Override
    public CaseListItemVO resolveCase(String reference, CurrentUserVO doctor) {
        int number = parseNumber(reference);
        return CaseListItemVO.builder()
                .id(10_000 + number)
                .caseNo("EVAL-C-%03d".formatted(number))
                .patientId(20_000L + number)
                .patientNo("PT-EVAL-%03d".formatted(number))
                .patientAge(30 + number)
                .patientGender(number % 2 == 0 ? PatientGender.FEMALE : PatientGender.MALE)
                .eyeSide(number % 2 == 0 ? EyeSide.RIGHT : EyeSide.LEFT)
                .workflowStatus(CaseWorkflowStatus.IN_REVIEW)
                .updatedAt(LocalDateTime.of(2026, 10, 7, 12, 0).minusMinutes(number))
                .build();
    }

    @Override
    public TaskEntity resolveTask(String reference, CurrentUserVO doctor) {
        throw new UnsupportedOperationException("评测运行时不解析真实任务实体");
    }

    private int parseNumber(String reference) {
        if (reference == null || reference.isBlank()) return 1;
        String digits = reference.replaceAll("\\D", "");
        if (digits.isBlank()) return 1;
        int value = Integer.parseInt(digits);
        return value >= 10_000 ? value - 10_000 : Math.max(1, value);
    }
}
