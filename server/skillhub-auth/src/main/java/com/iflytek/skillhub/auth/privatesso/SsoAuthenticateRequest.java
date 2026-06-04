package com.iflytek.skillhub.auth.privatesso;

public record SsoAuthenticateRequest(
    String username,
    String password,
    String twoFactorCode
) {}
