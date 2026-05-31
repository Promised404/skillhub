package com.iflytek.skillhub.auth.privatesso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsoUser(
    String uid,
    String username,
    String displayName,
    String email,
    String avatarUrl
) {}
