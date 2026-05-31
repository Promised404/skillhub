package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.config.AuthMethodVisibilityProperties;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2AuthorizationRequestResolverTest {

    private SkillHubOAuth2AuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        OAuthLoginFlowService oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        resolver = newResolver(oauthLoginFlowService, new AuthMethodVisibilityProperties());
    }

    @Test
    void resolve_storesSanitizedReturnToInSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "/dashboard/publish?draft=1");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/publish?draft=1");
    }

    @Test
    void resolve_ignoresUnsafeReturnTo() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "https://evil.example");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void resolve_returnsNullWhenProviderIsHiddenByAllowlist() {
        AuthMethodVisibilityProperties visibilityProperties = new AuthMethodVisibilityProperties();
        visibilityProperties.setVisibleProviders(List.of("wechatwork"));
        OAuthLoginFlowService oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        resolver = newResolver(oauthLoginFlowService, visibilityProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "/dashboard");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "github");

        assertThat(authorizationRequest).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    private SkillHubOAuth2AuthorizationRequestResolver newResolver(
            OAuthLoginFlowService oauthLoginFlowService,
            AuthMethodVisibilityProperties visibilityProperties) {
        ClientRegistration github = ClientRegistration.withRegistrationId("github")
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read:user")
                .clientName("GitHub")
                .build();
        return new SkillHubOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(github),
                oauthLoginFlowService,
                visibilityProperties
        );
    }
}
