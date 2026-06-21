package com.example.retinavision.exception;
import com.example.retinavision.constant.ErrorMessageSignal;
public class ConflictException extends BaseException {
    public ConflictException(String message) { super(ErrorMessageSignal.CONFLICT, message); }
}
