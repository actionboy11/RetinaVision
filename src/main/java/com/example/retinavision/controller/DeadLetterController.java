package com.example.retinavision.controller;

import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.DeadLetterRecoveryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dead-letters")
public class DeadLetterController {

    private final DeadLetterRecoveryService recoveryService;

    public DeadLetterController(DeadLetterRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping("/recover")
    public Result<Integer> recover(@RequestParam(defaultValue = "10") int limit,
                                   Authentication authentication) {
        CurrentUserVO currentUser = (CurrentUserVO) authentication.getPrincipal();
        return Result.success(recoveryService.recover(currentUser.getId(), limit));
    }
}
