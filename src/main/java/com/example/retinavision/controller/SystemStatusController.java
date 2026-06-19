package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.SystemStatusVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public Result<SystemStatusVO> getStatus() {
        return Result.success(systemStatusService.getStatus());
    }
}
