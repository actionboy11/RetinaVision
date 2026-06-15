package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.AccountNotFoundException;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.UserLoginDTO;
import com.example.retinavision.pojo.DTO.UserRegisterDTO;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.UserLoginVO;
import com.example.retinavision.service.UserLoginService;
import com.example.retinavision.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserLoginServiceImpl implements UserLoginService {

    private UserRegisterMapper userRegisterMapper;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;

    public UserLoginServiceImpl(UserRegisterMapper userRegisterMapper,
                                PasswordEncoder passwordEncoder,
                                JwtUtil jwtUtil
    ) {
        this.userRegisterMapper = userRegisterMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }


    // 用户注册
    @Override
    public void UserRegister(UserRegisterDTO userRegisterDTO) {
            System.out.println("用户注册");
        String username = normalizeBlank(userRegisterDTO.getUsername());
        String password = normalizeBlank(userRegisterDTO.getPassword());
        String realname = normalizeBlank(userRegisterDTO.getRealName());
        if (username == null || password == null || realname == null){
            throw new AccountNotFoundException(ErrorMessageSignal.PARAM_ERROR, "用户名、密码、真实姓名不能为空");
        }
        if (username.length() < 4 || username.length() > 20){
            throw new AccountNotFoundException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.USERNAME_LENGTH_ERROR);
        }
        if (password.length() < 6 || password.length() > 20){
            throw new AccountNotFoundException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.PASSWORD_LENGTH_ERROR);
        }
        if(userRegisterDTO.getRoleCode()== UserRole.ADMIN){
            throw new AccountNotFoundException(ErrorMessageSignal.FORBIDDEN, ErrorMessageContant.USER_NOT_AUTHORIZED);
        }
        if(userRegisterMapper.existsByUsername( username)){
            throw new AccountNotFoundException(ErrorMessageSignal.CONFLICT, ErrorMessageContant.USER_ALREADY_EXISTS);
        }
        UserEntity  userEntity = new UserEntity();
        userEntity.setUsername(username);
        String encodePassword = passwordEncoder.encode(password);
        userEntity.setPasswordHash(encodePassword);
        userEntity.setRealName(realname);
        userEntity.setRoleCode(userRegisterDTO.getRoleCode());
        userEntity.setStatus(1);
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userRegisterMapper.insert(userEntity);

    }

    @Override
    public UserLoginVO UserLogin(UserLoginDTO userLoginDTO) {
        if (userLoginDTO.getUsername() == null || userLoginDTO.getPassword() == null){
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        UserEntity user=userRegisterMapper.selectOne(
                new QueryWrapper<UserEntity>().eq("username",userLoginDTO.getUsername()));
        if (user == null){
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(userLoginDTO.getPassword(),user.getPasswordHash())){
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        if(user.getStatus()==0){
            throw new AccountNotFoundException(ErrorMessageSignal.FORBIDDEN, ErrorMessageContant.USER_NOT_ACTIVE);
        }

        String token = jwtUtil.generateToken(user.getId(),user.getUsername(),user.getRoleCode().name());
        return new UserLoginVO(token, new CurrentUserVO(user.getId(),user.getUsername(),user.getRealName(),user.getRoleCode()));

    }

    @Override
    public CurrentUserVO getCurrentUser(Integer userId) {
        UserEntity user = userRegisterMapper.selectById(userId);

        if (user == null) {
            throw new AccountNotFoundException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.USER_NOT_FOUND);
        }

        if (user.getStatus() == 0) {
            throw new AccountNotFoundException(ErrorMessageSignal.FORBIDDEN, ErrorMessageContant.USER_NOT_ACTIVE);
        }

        return new CurrentUserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode());
    }

    private String normalizeBlank(String value) {
        //normalizeBlank() 方法用于处理输入的字符串值，去除前后空白字符，并将空字符串转换为 null。
        // 它首先使用 StringUtils.hasText() 方法检查字符串是否包含非空白字符，
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
