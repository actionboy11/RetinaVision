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
import com.example.retinavision.service.LoginAttemptService;
import com.example.retinavision.service.JwtBlacklistService;
import com.example.retinavision.service.PatientProfileService;
import com.example.retinavision.utils.JwtUtil;
import com.example.retinavision.exception.BaseException;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;

@Service
public class UserLoginServiceImpl implements UserLoginService {

    private UserRegisterMapper userRegisterMapper;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final JwtBlacklistService jwtBlacklistService;
    private final PatientProfileService patientProfileService;

    public UserLoginServiceImpl(UserRegisterMapper userRegisterMapper,
                                PasswordEncoder passwordEncoder,
                                JwtUtil jwtUtil,
                                LoginAttemptService loginAttemptService,
                                JwtBlacklistService jwtBlacklistService,
                                PatientProfileService patientProfileService
    ) {
        this.userRegisterMapper = userRegisterMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.jwtBlacklistService = jwtBlacklistService;
        this.patientProfileService = patientProfileService;
    }


    // 用户注册
    @Override
    @Transactional
    public void UserRegister(UserRegisterDTO userRegisterDTO) {
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
        if(userRegisterMapper.existsByUsername( username)){
            throw new AccountNotFoundException(ErrorMessageSignal.CONFLICT, ErrorMessageContant.USER_ALREADY_EXISTS);
        }
        UserEntity  userEntity = new UserEntity();
        userEntity.setUsername(username);
        String encodePassword = passwordEncoder.encode(password);
        userEntity.setPasswordHash(encodePassword);
        userEntity.setRealName(realname);
        // 公开注册输入不可信：角色固定为 USER，医生/研究员身份只能由管理员授予。
        userEntity.setRoleCode(UserRole.USER);
        userEntity.setStatus(1);
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userRegisterMapper.insert(userEntity);
        patientProfileService.getOrCreateAccountProfile(userEntity.getId());

    }

    @Override
    public UserLoginVO UserLogin(UserLoginDTO userLoginDTO, String clientIp) {
        if (userLoginDTO.getUsername() == null || userLoginDTO.getPassword() == null){
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        String username = userLoginDTO.getUsername().trim();
        // 检查登录尝试次数是否超过限制
        loginAttemptService.assertAllowed(username, clientIp);
        UserEntity user=userRegisterMapper.selectOne(
                new QueryWrapper<UserEntity>().eq("username", username));
        if (user == null){
            // 记录登录失败次数，并抛出登录错误异常
            loginAttemptService.recordFailure(username, clientIp);
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(userLoginDTO.getPassword(),user.getPasswordHash())){
            loginAttemptService.recordFailure(username, clientIp);
            throw new AccountNotFoundException(ErrorMessageSignal.LOGIN_ERROR, ErrorMessageContant.USER_PASSWORD_ERROR);
        }
        if(user.getStatus()==0){
            throw new AccountNotFoundException(ErrorMessageSignal.FORBIDDEN, ErrorMessageContant.USER_NOT_ACTIVE);
        }
        //
        loginAttemptService.recordSuccess(username);
        String token = jwtUtil.generateToken(user.getId(),user.getUsername(),user.getRoleCode().name());
        return new UserLoginVO(token, new CurrentUserVO(user.getId(),user.getUsername(),user.getRealName(),user.getRoleCode(),user.getProfessionalNo()));

    }

    @Override
    public void logout(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BaseException(ErrorMessageSignal.UNAUTHORIZED, "Token 无效");
        }
        Claims claims = jwtUtil.parseToken(authorizationHeader.substring(7));
        if (!StringUtils.hasText(claims.getId())) {
            throw new BaseException(ErrorMessageSignal.UNAUTHORIZED, "Token 无效");
        }

        long remainingSeconds = jwtUtil.remainingLifetimeSeconds(claims);
        //Duration对象表示一段时间的长度，这里用于表示剩余的有效时间。
        if (remainingSeconds > 0) {
            // 将 JWT 的唯一标识符（claims.getId()）加入黑名单，并设置过期时间为剩余的有效时间。
            // 这样可以确保在 JWT 过期之前，用户无法再使用该 JWT 进行认证。
            jwtBlacklistService.blacklist(claims.getId(), Duration.ofSeconds(remainingSeconds));
        }
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

        return new CurrentUserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode(), user.getProfessionalNo());
    }

    private String normalizeBlank(String value) {
        //normalizeBlank() 方法用于处理输入的字符串值，去除前后空白字符，并将空字符串转换为 null。
        // 它首先使用 StringUtils.hasText() 方法检查字符串是否包含非空白字符，
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
