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

    // 获取系统状态信息，包括数据库连接状态、系统运行时间等
    @GetMapping("/status")
    public Result<SystemStatusVO> getStatus() {
        return Result.success(systemStatusService.getStatus());
    }
}
