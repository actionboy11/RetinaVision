package com.example.retinavision.service.auth.api;

import com.example.retinavision.pojo.auth.dto.UserLoginDTO;
import com.example.retinavision.pojo.auth.dto.UserRegisterDTO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;
import com.example.retinavision.pojo.auth.vo.UserLoginVO;
import org.springframework.stereotype.Service;

@Service
public interface UserLoginService {

    public void UserRegister( UserRegisterDTO userRegisterDTO);

    UserLoginVO UserLogin(UserLoginDTO userLoginDTO, String clientIp);

    void logout(String authorizationHeader);

    CurrentUserVO getCurrentUser(Integer userId);
}
