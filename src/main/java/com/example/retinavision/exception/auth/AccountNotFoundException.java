package com.example.retinavision.exception.auth;

import com.example.retinavision.exception.shared.BaseException;

/**
 * 账号不存在异常
 */
public class AccountNotFoundException extends BaseException {

    public AccountNotFoundException() {
    }

    public AccountNotFoundException(int code, String message) {
        super(code, message);
    }


    public AccountNotFoundException(String msg) {
        super(msg);
    }

}
