package com.iflytek.skillhub.auth.privatesso;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.HashMap;
import java.util.Map;

public class PrivateSsoIdentityService {

    private final IdentityBindingService identityBindingService;

    public PrivateSsoIdentityService(IdentityBindingService identityBindingService) {
        this.identityBindingService = identityBindingService;
    }

    public void disableUserByLoginName(String providerCode, String loginName) {
        identityBindingService.disableUserByProviderLogin(providerCode, loginName);
    }

    public PlatformPrincipal resolveOrCreate(SsoUser ssoUser, PrivateSsoProperties.Identity identityConfig) {
        Map<String, Object> extra = new HashMap<>();
        if (ssoUser.avatarUrl() != null) {
            extra.put("avatar_url", ssoUser.avatarUrl());
        }

        OAuthClaims claims = new OAuthClaims(
                identityConfig.getProviderCode(),
                ssoUser.uid(),
                ssoUser.email(),
                ssoUser.email() != null,
                ssoUser.username(),
                extra
        );

        UserStatus initialStatus = UserStatus.valueOf(identityConfig.getInitialStatus());
        return identityBindingService.bindOrCreate(claims, initialStatus);
    }
}
