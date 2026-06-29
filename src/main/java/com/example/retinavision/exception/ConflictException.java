package com.example.retinavision.exception;
import com.example.retinavision.constant.ErrorMessageSignal;
// 冲突异常类
public class ConflictException extends BaseException {
    // 构造函数，接收错误信息参数
    public ConflictException(String message) {
        super(ErrorMessageSignal.CONFLICT, message);
    }
}
