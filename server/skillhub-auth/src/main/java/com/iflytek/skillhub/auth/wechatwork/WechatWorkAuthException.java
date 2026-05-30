package com.iflytek.skillhub.auth.wechatwork;

/**
 * Exception for WeChat Work authentication and API integration failures.
 */
public class WechatWorkAuthException extends RuntimeException {

    public WechatWorkAuthException(String message) {
        super(message);
    }

    public WechatWorkAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
