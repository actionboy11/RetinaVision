package com.example.retinavision.service;

/**
 *  登录尝试服务接口，定义了检查登录尝试是否允许、记录失败和成功登录尝试的方法。
 */
public interface LoginAttemptService {

    void assertAllowed(String username, String clientIp);
    void recordFailure(String username, String clientIp);
    void recordSuccess(String username);
}
