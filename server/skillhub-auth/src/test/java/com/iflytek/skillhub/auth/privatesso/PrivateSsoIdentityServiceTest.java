package com.iflytek.skillhub.auth.privatesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.AccountDisabledException;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivateSsoIdentityServiceTest {

    @Mock
    private IdentityBindingService identityBindingService;

    private PrivateSsoIdentityService service;

    private final PrivateSsoProperties.Identity identityConfig = new PrivateSsoProperties.Identity();

    @BeforeEach
    void setUp() {
        service = new PrivateSsoIdentityService(identityBindingService);
    }

    @Test
    void resolveOrCreate_shouldDelegateToIdentityBindingService() {
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", "https://avatar.test/a.png");
        PlatformPrincipal expected = new PlatformPrincipal("usr_1", "张三", "zhangsan@company.com", "https://avatar.test/a.png", "private-sso", Set.of("USER"));
        when(identityBindingService.bindOrCreate(any(OAuthClaims.class), any(UserStatus.class))).thenReturn(expected);

        PlatformPrincipal result = service.resolveOrCreate(ssoUser, identityConfig);

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        verify(identityBindingService).bindOrCreate(claimsCaptor.capture(), any(UserStatus.class));
        OAuthClaims claims = claimsCaptor.getValue();
        assertThat(claims.provider()).isEqualTo("private-sso");
        assertThat(claims.subject()).isEqualTo("U10042");
        assertThat(claims.email()).isEqualTo("zhangsan@company.com");
        assertThat(claims.providerLogin()).isEqualTo("zhangsan");
        assertThat(claims.extra()).containsEntry("avatar_url", "https://avatar.test/a.png");
    }

    @Test
    void resolveOrCreate_shouldThrowWhenAccountDisabled() {
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        when(identityBindingService.bindOrCreate(any(OAuthClaims.class), any(UserStatus.class)))
                .thenThrow(new AccountDisabledException());

        assertThatThrownBy(() -> service.resolveOrCreate(ssoUser, identityConfig))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    void resolveOrCreate_shouldUseConfiguredProviderCode() {
        identityConfig.setProviderCode("my-company-sso");
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        PlatformPrincipal expected = new PlatformPrincipal("usr_1", "张三", "zhangsan@company.com", null, "my-company-sso", Set.of("USER"));
        when(identityBindingService.bindOrCreate(any(OAuthClaims.class), any(UserStatus.class))).thenReturn(expected);

        service.resolveOrCreate(ssoUser, identityConfig);

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        verify(identityBindingService).bindOrCreate(claimsCaptor.capture(), any(UserStatus.class));
        assertThat(claimsCaptor.getValue().provider()).isEqualTo("my-company-sso");
    }

    @Test
    void resolveOrCreate_shouldOmitAvatarUrlWhenNull() {
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        PlatformPrincipal expected = new PlatformPrincipal("usr_1", "张三", "zhangsan@company.com", null, "private-sso", Set.of("USER"));
        when(identityBindingService.bindOrCreate(any(OAuthClaims.class), any(UserStatus.class))).thenReturn(expected);

        service.resolveOrCreate(ssoUser, identityConfig);

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        verify(identityBindingService).bindOrCreate(claimsCaptor.capture(), any(UserStatus.class));
        assertThat(claimsCaptor.getValue().extra()).doesNotContainKey("avatar_url");
    }
}
