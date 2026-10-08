package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentSkillRegistry {
    private static final Set<AgentSkillCode> DOCTOR_SKILLS = EnumSet.of(
            AgentSkillCode.DOCTOR_WORKLOAD_OVERVIEW,
            AgentSkillCode.ASSIGNED_CASE_SEARCH,
            AgentSkillCode.CASE_CLINICAL_SUMMARY,
            AgentSkillCode.CASE_FOLLOWUP_ANALYSIS,
            AgentSkillCode.DOCTOR_TASK_SEARCH,
            AgentSkillCode.DOCTOR_CLINICAL_QUEUE,
            AgentSkillCode.MEDICAL_KNOWLEDGE_QA);
    private static final Set<AgentSkillCode> PATIENT_SKILLS = EnumSet.of(
            AgentSkillCode.MY_CASE_LIST,
            AgentSkillCode.MY_CASE_PROGRESS,
            AgentSkillCode.MY_SIGNED_REPORT,
            AgentSkillCode.PATIENT_KNOWLEDGE_QA);
    private static final Set<String> FREE_TEXT_ARGUMENTS = Set.of(
            "taskReference", "caseReference", "resultId", "version");

    public AgentSkillExecutionMode executionMode(AgentSkillCode code) {
        return code == AgentSkillCode.MEDICAL_KNOWLEDGE_QA
                || code == AgentSkillCode.PATIENT_KNOWLEDGE_QA
                ? AgentSkillExecutionMode.TOOL_CALLING : AgentSkillExecutionMode.DIRECT;
    }

    public boolean isAvailable(AgentSkillCode code, UserRole role) {
        if (code == null || role == null) return false;
        if (code == AgentSkillCode.MEDICAL_KNOWLEDGE_QA) {
            return role == UserRole.DOCTOR || role == UserRole.ADMIN;
        }
        if (role == UserRole.USER) return PATIENT_SKILLS.contains(code);
        return role == UserRole.DOCTOR && DOCTOR_SKILLS.contains(code);
    }

    public Map<String, List<String>> argumentSchema(AgentSkillCode code) {
        return switch (code) {
            case DOCTOR_TASK_SEARCH -> Map.of(
                    "taskType", names(TaskType.values()),
                    "status", names(DoctorTaskStatusFilter.values()),
                    "dateWindow", names(DoctorDateWindow.values()),
                    "taskReference", List.of("string"));
            case DOCTOR_CLINICAL_QUEUE -> Map.of(
                    "queueType", names(DoctorClinicalQueueType.values()),
                    "dateWindow", names(DoctorDateWindow.values()));
            case ASSIGNED_CASE_SEARCH -> Map.of(
                    "segmentationState", names(SegmentationState.values()),
                    "clinicalState", names(DoctorClinicalState.values()),
                    "dateWindow", names(DoctorDateWindow.values()),
                    "eyeSide", List.of("LEFT", "RIGHT", "BOTH"));
            case CASE_CLINICAL_SUMMARY, CASE_FOLLOWUP_ANALYSIS ->
                    Map.of("caseReference", List.of("string"));
            case MY_CASE_LIST -> Map.of(
                    "reuploadOnly", List.of("TRUE", "FALSE"),
                    "signedReportOnly", List.of("TRUE", "FALSE"));
            case MY_CASE_PROGRESS -> Map.of("caseReference", List.of("string"));
            case MY_SIGNED_REPORT -> Map.of(
                    "caseReference", List.of("string"),
                    "resultId", List.of("string"),
                    "version", List.of("string"),
                    "mode", List.of("LIST", "VIEW", "EXPLAIN"));
            default -> Map.of();
        };
    }

    public Map<String, String> validateAndNormalize(AgentSkillCode code, UserRole role,
                                                     Map<String, String> arguments) {
        if (!isAvailable(code, role)) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权使用该 Skill");
        }
        return normalizeArguments(code, arguments);
    }

    public Map<String, String> normalizeArguments(AgentSkillCode code,
                                                   Map<String, String> arguments) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, List<String>> schema = argumentSchema(code);
        if (arguments != null) {
            arguments.forEach((key, value) -> {
                if (!schema.containsKey(key)) {
                    throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 参数不受支持: " + key);
                }
                if (!FREE_TEXT_ARGUMENTS.contains(key) && !schema.get(key).contains(value)) {
                    throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "Skill 参数值无效: " + key);
                }
                if (value != null && !value.isBlank()) result.put(key, value);
            });
        }
        if (code == AgentSkillCode.DOCTOR_TASK_SEARCH) {
            result.putIfAbsent("taskType", TaskType.VESSEL_SEGMENTATION.name());
            result.putIfAbsent("status", DoctorTaskStatusFilter.ANY.name());
            result.putIfAbsent("dateWindow", DoctorDateWindow.ANY.name());
        } else if (code == AgentSkillCode.DOCTOR_CLINICAL_QUEUE) {
            result.putIfAbsent("dateWindow", DoctorDateWindow.ANY.name());
        }
        return Map.copyOf(result);
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
