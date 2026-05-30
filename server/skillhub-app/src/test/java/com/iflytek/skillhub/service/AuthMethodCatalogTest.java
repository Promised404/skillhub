package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.bootstrap.PassiveSessionAuthenticator;
import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.wechatwork.WechatWorkAuthProperties;
import com.iflytek.skillhub.config.AuthMethodVisibilityProperties;
import com.iflytek.skillhub.config.AuthSessionBootstrapProperties;
import com.iflytek.skillhub.config.DirectAuthProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;

class AuthMethodCatalogTest {

    @Test
    void listMethodsShouldUseProviderDisplayNamesForCompatibleAuthMethods() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise Password";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                throw new UnsupportedOperationException("not used in catalog test");
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise SSO";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            List.of(directProvider),
            List.of(bootstrapProvider)
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "local-password:Local Account",
                "direct-private-sso:Enterprise Password",
                "bootstrap-private-sso:Enterprise SSO"
            );
    }

    @Test
    void listMethodsShouldFallBackToProviderCodeWhenDisplayNameIsNotOverridden() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                return mock(PlatformPrincipal.class);
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            List.of(directProvider),
            List.of(bootstrapProvider)
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "direct-private-sso:private-sso",
                "bootstrap-private-sso:private-sso"
            );
    }

    @Test
    void listMethodsShouldExposeWechatWorkOnlyWhenBrowserLoginConfigIsComplete() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        AuthMethodVisibilityProperties visibilityProperties = new AuthMethodVisibilityProperties();
        visibilityProperties.setVisibleProviders(List.of("wechatwork"));

        WechatWorkAuthProperties completeWechatWork = new WechatWorkAuthProperties();
        completeWechatWork.setEnabled(true);
        completeWechatWork.setCorpId("corp-fr24");
        completeWechatWork.setAgentId("100001");
        completeWechatWork.setCorpSecret("secret");
        completeWechatWork.setCallbackBaseUrl("https://login.fr24.example");

        AuthMethodCatalog completeCatalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            completeWechatWork,
            visibilityProperties,
            List.of(),
            List.of()
        );

        WechatWorkAuthProperties missingCallbackWechatWork = new WechatWorkAuthProperties();
        missingCallbackWechatWork.setEnabled(true);
        missingCallbackWechatWork.setCorpId("corp-fr24");
        missingCallbackWechatWork.setAgentId("100001");
        missingCallbackWechatWork.setCorpSecret("secret");
        missingCallbackWechatWork.setCallbackBaseUrl(" ");

        AuthMethodCatalog missingCallbackCatalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            missingCallbackWechatWork,
            visibilityProperties,
            List.of(),
            List.of()
        );

        assertThat(completeCatalog.listMethods(null))
            .extracting(method -> method.id())
            .contains("oauth-wechatwork");
        assertThat(missingCallbackCatalog.listMethods(null))
            .extracting(method -> method.id())
            .doesNotContain("oauth-wechatwork");
    }
}
