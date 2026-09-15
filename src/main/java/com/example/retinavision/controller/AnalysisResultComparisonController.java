package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.AnalysisResultComparisonVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.ClinicalAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis-results")
public class AnalysisResultComparisonController {
    private final CaseAnalysisTimelineService timelineService;
    private final ClinicalAccessService accessService;

    public AnalysisResultComparisonController(CaseAnalysisTimelineService timelineService,
                                              ClinicalAccessService accessService) {
        this.timelineService = timelineService;
        this.accessService = accessService;
    }

    @GetMapping("/compare")
    public Result<AnalysisResultComparisonVO> compare(@RequestParam Long baselineResultId,
                                                      @RequestParam Long targetResultId,
                                                      Authentication authentication) {
        CurrentUserVO user = (CurrentUserVO) authentication.getPrincipal();
        accessService.assertCanAccessResult(user, baselineResultId);
        accessService.assertCanAccessResult(user, targetResultId);
        return Result.success(timelineService.compare(baselineResultId, targetResultId));
    }
}
