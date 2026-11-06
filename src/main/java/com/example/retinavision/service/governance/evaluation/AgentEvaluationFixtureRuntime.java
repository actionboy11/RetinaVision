package com.example.retinavision.service.governance.evaluation;

import com.example.retinavision.constant.shared.ErrorMessageSignal;
import com.example.retinavision.pojo.agent.AgentAction;
import com.example.retinavision.pojo.agent.AgentPagination;
import com.example.retinavision.pojo.agent.AgentSkillCode;
import com.example.retinavision.pojo.agent.AgentSkillDefinition;
import com.example.retinavision.pojo.agent.AgentSkillRoute;
import com.example.retinavision.pojo.agent.AgentSkillRuntimeVersion;
import com.example.retinavision.pojo.agent.AgentStructuredData;
import com.example.retinavision.pojo.agent.DoctorAgentSkillResult;
import com.example.retinavision.pojo.agent.PatientAgentSkillResult;
import com.example.retinavision.pojo.agent.PatientReportExplanationResult;
import com.example.retinavision.pojo.governance.evaluation.AgentEvaluationCitation;
import com.example.retinavision.pojo.governance.evaluation.AgentEvaluationExecution;
import com.example.retinavision.application.agent.routing.api.AgentSkillRouter;
import com.example.retinavision.application.agent.routing.api.AgentSkillVersionResolver;
import com.example.retinavision.application.agent.orchestration.DoctorAgentSkillOrchestrator;
import com.example.retinavision.application.agent.orchestration.PatientAgentSkillOrchestrator;

import com.example.retinavision.enumeration.user.UserRole;
import com.example.retinavision.exception.shared.BaseException;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.service.patient.PatientReportExplanationService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AgentEvaluationFixtureRuntime {
    private final AgentSkillRouter router;
    private final Map<AgentSkillCode, Integer> skillVersions;
    private final FixtureDoctorAgentQueryService doctorQueries = new FixtureDoctorAgentQueryService();
    private final FixturePatientAgentQueryService patientQueries = new FixturePatientAgentQueryService();
    private final InMemoryAgentQueryContextService doctorContexts = new InMemoryAgentQueryContextService();
    private final InMemoryPatientAgentQueryContextService patientContexts = new InMemoryPatientAgentQueryContextService();

    public AgentEvaluationFixtureRuntime(AgentSkillRouter router, Map<AgentSkillCode, Integer> skillVersions) {
        this.router = router;
        this.skillVersions = skillVersions == null ? Map.of() : Map.copyOf(skillVersions);
    }

    public AgentEvaluationExecution execute(Long sessionId, UserRole role, String question) {
        AtomicReference<AgentSkillRoute> routed = new AtomicReference<>();
        AgentSkillRouter recordingRouter = new AgentSkillRouter() {
            @Override
            public AgentSkillRoute route(String input, AgentSkillCode currentSkill) {
                AgentSkillRoute route = router.route(input, currentSkill);
                routed.set(route);
                return route;
            }

            @Override
            public AgentSkillRoute route(String input, AgentSkillCode currentSkill,
                                         List<AgentSkillDefinition> availableSkills) {
                AgentSkillRoute route = router.route(input, currentSkill, availableSkills);
                routed.set(route);
                return route;
            }
        };
        AgentSkillVersionResolver versions = (ignored, code) ->
                new AgentSkillRuntimeVersion((long) code.ordinal() + 1,
                        skillVersions.getOrDefault(code, 1));
        CurrentUserVO user = role == UserRole.DOCTOR
                ? new CurrentUserVO(9001, "eval-doctor", "评测医生", UserRole.DOCTOR)
                : new CurrentUserVO(9002, "eval-patient", "评测患者", role);

        if (role == UserRole.DOCTOR) {
            DoctorAgentSkillOrchestrator orchestrator = new DoctorAgentSkillOrchestrator(
                    recordingRouter, doctorQueries, doctorContexts,
                    new FixtureAgentClinicalReferenceService(), new FixtureCaseAnalysisTimelineService(), versions);
            var result = orchestrator.handle(sessionId, question, user);
            if (result.isPresent()) {
                DoctorAgentSkillResult value = result.orElseThrow();
                return execution(value.skillCode(), routed.get(), value.answer(), value.data(),
                        value.pagination(), value.actions(), List.of());
            }
            AgentSkillRoute route = routed.get();
            if (route != null && route.skillCode() == AgentSkillCode.MEDICAL_KNOWLEDGE_QA) {
                return knowledge(route, "EVAL-CHUNK-DOCTOR-001", "CLINICAL");
            }
        } else if (role == UserRole.USER) {
            PatientReportExplanationService explanations = report ->
                    PatientReportExplanationResult.available("这是匿名评测报告的通俗解释。");
            PatientAgentSkillOrchestrator orchestrator = new PatientAgentSkillOrchestrator(
                    recordingRouter, patientQueries, patientContexts, explanations, versions);
            var result = orchestrator.handle(sessionId, question, user);
            if (result.isPresent()) {
                PatientAgentSkillResult value = result.orElseThrow();
                return execution(value.skillCode(), routed.get(), value.answer(), value.data(),
                        value.pagination(), value.actions(), List.of());
            }
            AgentSkillRoute route = routed.get();
            if (route != null && route.skillCode() == AgentSkillCode.PATIENT_KNOWLEDGE_QA) {
                return knowledge(route, "EVAL-CHUNK-PATIENT-001", "PATIENT");
            }
        }
        throw new BaseException(ErrorMessageSignal.FORBIDDEN, "无权使用该 Skill");
    }

    private AgentEvaluationExecution knowledge(AgentSkillRoute route, String chunkId, String audience) {
        AgentEvaluationCitation citation = new AgentEvaluationCitation(chunkId, "匿名医学知识", audience);
        return new AgentEvaluationExecution(route.skillCode(), route.arguments(),
                "以下回答基于固定匿名知识片段。",
                new AgentStructuredData("KNOWLEDGE", Map.of("answer", "匿名知识回答")),
                null, List.of(), List.of(citation));
    }

    private AgentEvaluationExecution execution(AgentSkillCode skillCode, AgentSkillRoute route,
                                                String answer, AgentStructuredData data,
                                                AgentPagination pagination, List<AgentAction> actions,
                                                List<AgentEvaluationCitation> citations) {
        Map<String, String> arguments = route == null ? Map.of() : route.arguments();
        return new AgentEvaluationExecution(skillCode, arguments, answer, data, pagination,
                actions == null ? List.of() : actions, citations);
    }
}
