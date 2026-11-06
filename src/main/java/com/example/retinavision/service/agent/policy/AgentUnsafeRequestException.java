package com.example.retinavision.service.agent.policy;

import com.example.retinavision.constant.shared.ErrorMessageSignal;

import com.example.retinavision.exception.BaseException;

public class AgentUnsafeRequestException extends BaseException {
    public AgentUnsafeRequestException() {
        super(ErrorMessageSignal.FORBIDDEN, "智能助手仅支持当前账号权限范围内的只读查询");
    }
}
