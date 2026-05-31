package com.iflytek.skillhub.auth.privatesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrivateSsoDirectAuthProviderTest {

    @Mock
    private PrivateSsoClient client;

    @Mock
    private PrivateSsoIdentityService identityService;

    private PrivateSsoDirectAuthProvider provider;
    private final PrivateSsoProperties.Identity identityConfig = new PrivateSsoProperties.Identity();

    @BeforeEach
    void setUp() {
        provider = new PrivateSsoDirectAuthProvider(client, identityService, identityConfig);
    }

    @Test
    void providerCode_returnsPrivateSso() {
        assertThat(provider.providerCode()).isEqualTo("private-sso");
    }

    @Test
    void displayName_returnsEnterpriseSSO() {
        assertThat(provider.displayName()).isEqualTo("Enterprise SSO");
    }

    @Test
    void authenticate_success() {
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        PlatformPrincipal expected = new PlatformPrincipal("usr_1", "张三", "zhangsan@company.com", null, "private-sso", Set.of("USER"));

        when(client.authenticate("zhangsan", "encrypted-pw", null)).thenReturn(ssoUser);
        when(identityService.resolveOrCreate(ssoUser, identityConfig)).thenReturn(expected);

        PlatformPrincipal result = provider.authenticate(new DirectAuthRequest("zhangsan", "encrypted-pw"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void authenticate_propagatesAuthFlowException() {
        when(client.authenticate("zhangsan", "wrong", null))
                .thenThrow(new AuthFlowException(org.springframework.http.HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials"));

        assertThatThrownBy(() -> provider.authenticate(new DirectAuthRequest("zhangsan", "wrong")))
                .isInstanceOf(AuthFlowException.class);
    }
}
