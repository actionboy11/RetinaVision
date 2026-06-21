package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.UserLoginDTO;
import com.example.retinavision.pojo.DTO.UserRegisterDTO;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.UserLoginVO;
import org.springframework.stereotype.Service;

@Service
public interface UserLoginService {

    public void UserRegister( UserRegisterDTO userRegisterDTO);

    UserLoginVO UserLogin(UserLoginDTO userLoginDTO, String clientIp);

    void logout(String authorizationHeader);

    CurrentUserVO getCurrentUser(Integer userId);
}
