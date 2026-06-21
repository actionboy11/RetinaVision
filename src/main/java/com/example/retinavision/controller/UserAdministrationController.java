package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.UpdateUserRoleDTO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.UserAdministrationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import com.example.retinavision.pojo.VO.AdminUserItemVO;
import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService userAdministrationService;

    public UserAdministrationController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @GetMapping
    public Result<List<AdminUserItemVO>> listUsers() {
        return Result.success(userAdministrationService.listUsers());
    }

    @PutMapping("/{userId}/role")
    public Result<Boolean> assignRole(@PathVariable Integer userId,
                                      @RequestBody UpdateUserRoleDTO request,
                                      Authentication authentication) {
        CurrentUserVO administrator = (CurrentUserVO) authentication.getPrincipal();
        userAdministrationService.assignRole(userId, request, administrator.getId());
        return Result.success(true);
    }
}
