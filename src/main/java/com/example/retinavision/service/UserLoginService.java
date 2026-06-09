package com.example.retinavision.service;

import com.example.retinavision.pojo.DTO.UserRegisterDTO;
import org.springframework.stereotype.Service;

@Service
public interface UserLoginService {

    public void UserRegister( UserRegisterDTO userRegisterDTO);
}
