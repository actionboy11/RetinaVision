package com.example.retinavision.controller;


import com.example.retinavision.pojo.DTO.CaseInsertDTO;
import com.example.retinavision.pojo.DTO.CaseListQueryDTO;
import com.example.retinavision.pojo.DTO.CaseUpdateDTO;
import com.example.retinavision.enumeration.EyeSide;
import com.example.retinavision.enumeration.TaskType;
import com.example.retinavision.pojo.VO.CaseAnalysisTimelineVO;
import com.example.retinavision.pojo.VO.CaseListItemVO;
import com.example.retinavision.pojo.VO.CaseTrendSummaryVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.PageResult;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.CaseAnalysisTimelineService;
import com.example.retinavision.service.CaseService;
import com.example.retinavision.service.ClinicalAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseService caseService;
    private final ClinicalAccessService accessService;
    private final CaseAnalysisTimelineService timelineService;

    public CaseController(CaseService caseService,
                          ClinicalAccessService accessService,
                          CaseAnalysisTimelineService timelineService) {
        this.caseService = caseService;
        this.accessService = accessService;
        this.timelineService = timelineService;
    }

    @GetMapping
    public Result<PageResult<CaseListItemVO>> list(CaseListQueryDTO queryDTO, Authentication authentication) {
        // 1. 这个方法承载 GET /api/cases 请求；JWT 是否有效已经由 JwtAuthenticationFilter 自动校验。
        // 2. Spring 会把 URL 上的 pageNo/pageSize/keyword/status/eyeSide 自动绑定到 CaseListQueryDTO。
        // 3. Controller 不写 SQL，只把查询参数交给 Service。
        CurrentUserVO currentUser = user(authentication);
        accessService.assertClinicalRole(currentUser);
        PageResult<CaseListItemVO> pageList = caseService.getCaseList(queryDTO, currentUser);
        return Result.success(pageList);
    }

    @PostMapping
    public  Result<CaseListItemVO> addCase(
            @RequestBody CaseInsertDTO caseInsertDTO,
            Authentication authentication)
    {
        // 补充：当前登录用户由 JwtAuthenticationFilter 放入 Authentication，这里只取用户 id 作为 createdBy。
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        accessService.assertClinicalRole(tokenUser);
        Integer userid = tokenUser.getId();
        CaseListItemVO caseListItemVO =caseService.addCase(caseInsertDTO,userid);
        return Result.success(caseListItemVO);
    }

    // 获取病例详情
    @GetMapping("/{caseId}")
    public  Result<CaseListItemVO> getCaseDetail(@PathVariable Integer caseId, Authentication authentication){
            //检验当前用户是否有权限访问该病例详情
            accessService.assertCanAccessCase(user(authentication), caseId.longValue());
            CaseListItemVO caseListItemVO =caseService.getCaseById(caseId);
            return Result.success(caseListItemVO);

    }

    @GetMapping("/{caseId}/analysis-timeline")
    public Result<CaseAnalysisTimelineVO> analysisTimeline(@PathVariable Integer caseId,
                                                          @RequestParam(required = false) EyeSide eyeSide,
                                                          @RequestParam(required = false) TaskType taskType,
                                                          @RequestParam(required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                          LocalDateTime startTime,
                                                          @RequestParam(required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                          LocalDateTime endTime,
                                                          Authentication authentication) {
        accessService.assertCanAccessCase(user(authentication), caseId.longValue());
        return Result.success(timelineService.timeline(caseId.longValue(), eyeSide, taskType, startTime, endTime));
    }

    @PostMapping("/{caseId}/trend-summary/ai-generate")
    public Result<CaseTrendSummaryVO> generateTrendSummary(@PathVariable Integer caseId,
                                                           @RequestParam(required = false) EyeSide eyeSide,
                                                           @RequestParam(required = false) TaskType taskType,
                                                           Authentication authentication) {
        accessService.assertCanAccessCase(user(authentication), caseId.longValue());
        return Result.success(timelineService.generateTrendSummary(caseId.longValue(), eyeSide, taskType));
    }
    // 更新病例信息
    @PutMapping("/{caseId}")
    public  Result<CaseListItemVO> updateCase(@PathVariable Integer caseId,
                                              @RequestBody CaseUpdateDTO  caseUpdateDTO, Authentication authentication){
        accessService.assertCanAccessCase(user(authentication), caseId.longValue());
        CaseListItemVO caseListItemVO =caseService.updateCase(caseId,caseUpdateDTO);
        return Result.success(caseListItemVO);
    }

    @DeleteMapping("/{caseId}")
    public  Result<Void> deleteCase(@PathVariable Integer caseId, Authentication authentication){
        accessService.assertCanAccessCase(user(authentication), caseId.longValue());
        caseService.deleteCase(caseId);
        return Result.success();
    }

    private CurrentUserVO user(Authentication authentication) {
        return (CurrentUserVO) authentication.getPrincipal();
    }
}
