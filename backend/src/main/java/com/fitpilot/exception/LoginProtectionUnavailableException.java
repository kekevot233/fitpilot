package com.fitpilot.exception;

public class LoginProtectionUnavailableException extends RuntimeException {
    public LoginProtectionUnavailableException() {
        super("登录服务暂时不可用，请稍后重试。");
    }
}
