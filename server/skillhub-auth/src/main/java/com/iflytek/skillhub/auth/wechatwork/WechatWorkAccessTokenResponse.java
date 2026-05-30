package com.iflytek.skillhub.auth.wechatwork;

/**
 * Access token response from WeChat Work API.
 */
public record WechatWorkAccessTokenResponse(
        int errorCode,
        String errorMessage,
        String accessToken,
        long expiresIn
) {
}
