package com.iflytek.skillhub.auth.privatesso;

import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import org.springframework.http.HttpStatus;

public class PrivateSsoDirectAuthProvider implements DirectAuthProvider {

    private final PrivateSsoClient client;
    private final PrivateSsoIdentityService identityService;
    private final PrivateSsoProperties.Identity identityConfig;

    public PrivateSsoDirectAuthProvider(PrivateSsoClient client,
                                        PrivateSsoIdentityService identityService,
                                        PrivateSsoProperties.Identity identityConfig) {
        this.client = client;
        this.identityService = identityService;
        this.identityConfig = identityConfig;
    }

    @Override
    public String providerCode() {
        return identityConfig.getProviderCode();
    }

    @Override
    public String displayName() {
        return "Enterprise SSO";
    }

    @Override
    public PlatformPrincipal authenticate(DirectAuthRequest request) {
        try {
            SsoUser ssoUser = client.authenticate(request.username(), request.password(), request.twoFactorCode());
            return identityService.resolveOrCreate(ssoUser, identityConfig);
        } catch (AuthFlowException e) {
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                identityService.disableUserByLoginName(identityConfig.getProviderCode(), request.username());
            }
            throw e;
        }
    }
}
