package com.example.retinavision.exception;

public class BaseException extends RuntimeException {
    private int code;
    private String message;

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
        this.message = msg;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
