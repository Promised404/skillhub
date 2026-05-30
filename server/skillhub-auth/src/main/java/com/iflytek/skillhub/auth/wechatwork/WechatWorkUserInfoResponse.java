package com.iflytek.skillhub.auth.wechatwork;

import java.util.Map;

/**
 * User info response from WeChat Work API.
 */
public record WechatWorkUserInfoResponse(
        int errorCode,
        String errorMessage,
        String userId,
        String openId,
        String deviceId,
        Map<String, Object> raw
) {

    public WechatWorkUserInfoResponse {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }
}
