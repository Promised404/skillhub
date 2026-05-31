package com.iflytek.skillhub.auth.privatesso;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SsoAuthenticateRequest(
    String username,
    String password,
    String twoFactorCode
) {}
