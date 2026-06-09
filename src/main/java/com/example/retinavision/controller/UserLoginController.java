package com.example.retinavision.controller;

import com.example.retinavision.pojo.DTO.UserLoginDTO;
import com.example.retinavision.pojo.DTO.UserRegisterDTO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.UserLoginVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.UserLoginService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class UserLoginController {

    private final UserLoginService userLoginService;

    public UserLoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    @PostMapping("/register")
    public Result<Boolean> userRegister(
            // Codex修复：前端 axios 以 JSON 请求体提交注册表单，后端必须使用 @RequestBody 才能正确绑定字段。
            @RequestBody UserRegisterDTO userRegisterDTO
    ) {
        userLoginService.UserRegister(userRegisterDTO);
        return Result.success(true);
    }

    @PostMapping("/login")
    public Result<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO) {
        UserLoginVO userLoginVO = userLoginService.UserLogin(userLoginDTO);
        return Result.success(userLoginVO);
    }

    @GetMapping("/me")
    //authentication 是 Spring Security 提供的接口，用于获取当前用户的信息。
    // // ← Spring MVC 检测到 Authentication 类型参数              │
    // → 触发 AuthenticationArgumentResolver -->从 ThreadLocal 中取出之前存入的对象
    public Result<CurrentUserVO> getCurrentUser(Authentication authentication) {
        CurrentUserVO tokenUser = (CurrentUserVO) authentication.getPrincipal();
        CurrentUserVO currentUser = userLoginService.getCurrentUser(tokenUser.getId());
        return Result.success(currentUser);
    }

    @PostMapping("/logout")
    public Result<Boolean> logout() {
        return Result.success(true);
    }
}
