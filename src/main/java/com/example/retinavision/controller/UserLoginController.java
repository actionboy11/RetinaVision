package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.UserRegisterDTO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.UserLoginService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class UserLoginController {

    private UserLoginService userLoginService;

    public UserLoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    @PostMapping("/register")
    public Result<Boolean> UserRegister(
            // Codex修复：前端 axios 以 JSON 请求体提交注册表单，后端必须使用 @RequestBody 才能正确绑定字段。
            @RequestBody UserRegisterDTO userRegisterDTO
    ){
        userLoginService.UserRegister(userRegisterDTO);
        return Result.success(true);
    }
}
