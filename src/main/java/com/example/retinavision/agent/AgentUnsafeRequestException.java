package com.example.retinavision.agent;

import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;

public class AgentUnsafeRequestException extends BaseException {
    public AgentUnsafeRequestException() {
        super(ErrorMessageSignal.FORBIDDEN, "智能助手仅支持当前账号权限范围内的只读查询");
    }
}
