package com.iflytek.skillhub.auth.wechatwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.config.AuthMethodVisibilityProperties;
import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

class WechatWorkLoginFlowServiceTest {

    @Test
    void buildAuthorizationRedirect_storesStateAndReturnToAndBuildsQrConnectUrl() {
        WechatWorkAuthProperties properties = enabledProperties();
        WechatWorkLoginFlowService service = newService(
                properties,
                mock(WechatWorkApiClient.class),
                mock(IdentityBindingService.class),
                mock(PlatformSessionService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        String redirect = service.buildAuthorizationRedirect(request, " /dashboard/review ");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        String state = (String) session.getAttribute("skillhub.oauth.wechatwork.state");
        assertThat(state).isNotBlank();
        assertThat(state.length()).isGreaterThan(20);
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/review");

        var queryParams = UriComponentsBuilder.fromUriString(redirect).build(true).getQueryParams();
        assertThat(redirect).startsWith("https://open.work.weixin.qq.com/wwopen/sso/qrConnect?");
        assertThat(queryParams.getFirst("appid")).isEqualTo("corp-fr24");
        assertThat(queryParams.getFirst("agentid")).isEqualTo("100001");
        assertThat(queryParams.getFirst("state")).isEqualTo(state);
        assertThat(queryParams.getFirst("redirect_uri"))
                .isEqualTo("https://login.fr24.example/api/v1/auth/wechatwork/callback");
    }

    @Test
    void buildAuthorizationRedirect_trimsTrailingSlashFromCallbackBaseUrl() {
        WechatWorkAuthProperties properties = enabledProperties();
        properties.setCallbackBaseUrl("https://login.example.com/");
        WechatWorkLoginFlowService service = newService(
                properties,
                mock(WechatWorkApiClient.class),
                mock(IdentityBindingService.class),
                mock(PlatformSessionService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("ignored-host");
        request.setServerPort(8080);

        String redirect = service.buildAuthorizationRedirect(request, "/dashboard");

        var queryParams = UriComponentsBuilder.fromUriString(redirect).build(true).getQueryParams();
        assertThat(queryParams.getFirst("redirect_uri"))
                .isEqualTo("https://login.example.com/api/v1/auth/wechatwork/callback");
    }

    @Test
    void buildAuthorizationRedirect_rejectsMissingCallbackBaseUrl() {
        WechatWorkAuthProperties properties = enabledProperties();
        properties.setCallbackBaseUrl(" ");
        WechatWorkLoginFlowService service = newService(
                properties,
                mock(WechatWorkApiClient.class),
                mock(IdentityBindingService.class),
                mock(PlatformSessionService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.buildAuthorizationRedirect(request, "/dashboard"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessage("WechatWork callback base URL is required");
    }

    @Test
    void buildAuthorizationRedirect_rejectsWhenProviderIsHiddenByAllowlist() {
        AuthMethodVisibilityProperties visibilityProperties = new AuthMethodVisibilityProperties();
        visibilityProperties.setVisibleProviders(java.util.List.of("github"));
        WechatWorkLoginFlowService service = new WechatWorkLoginFlowService(
                enabledProperties(),
                visibilityProperties,
                mock(WechatWorkApiClient.class),
                mock(IdentityBindingService.class),
                mock(PlatformSessionService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.buildAuthorizationRedirect(request, "/dashboard"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessage("WechatWork login is hidden by authentication method policy");
    }

    @Test
    void completeCallback_rejectsStateMismatch() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("skillhub.oauth.wechatwork.state", "expected-state");

        assertThatThrownBy(() -> service.completeCallback(request, "different-state", "code-1"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessageContaining("state");

        assertThat(request.getSession(false).getAttribute("skillhub.oauth.wechatwork.state")).isNull();
        verifyNoInteractions(apiClient, identityBindingService, platformSessionService);
    }

    @Test
    void completeCallback_rejectsMissingOrReusedState() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest missingStateRequest = new MockHttpServletRequest();
        assertThatThrownBy(() -> service.completeCallback(missingStateRequest, "state-1", "code-1"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessageContaining("state");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("skillhub.oauth.wechatwork.state", "state-2");
        WechatWorkUserInfoResponse userInfo = new WechatWorkUserInfoResponse(
                0,
                "ok",
                "wx-user-2",
                null,
                null,
                Map.of("UserId", "wx-user-2")
        );
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr-2",
                "wx-user-2",
                null,
                null,
                "wechatwork",
                Set.of("USER")
        );
        when(apiClient.resolveUserInfo("code-2")).thenReturn(userInfo);
        when(identityBindingService.bindOrCreate(org.mockito.ArgumentMatchers.any(OAuthClaims.class), eq(UserStatus.ACTIVE)))
                .thenReturn(principal);

        service.completeCallback(request, "state-2", "code-2");

        assertThatThrownBy(() -> service.completeCallback(request, "state-2", "code-2"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessageContaining("state");
    }

    @Test
    void completeCallback_rejectsMissingCode() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("skillhub.oauth.wechatwork.state", "state-3");

        assertThatThrownBy(() -> service.completeCallback(request, "state-3", " "))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessage("WechatWork callback code is missing");

        verifyNoInteractions(apiClient, identityBindingService, platformSessionService);
    }

    @Test
    void completeCallback_propagatesSanitizedApiFailureWithoutLeakingSensitiveCode() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("skillhub.oauth.wechatwork.state", "state-sensitive");
        String sensitiveCode = "code-sensitive-20260530";

        when(apiClient.resolveUserInfo(sensitiveCode))
                .thenThrow(new WechatWorkAuthException("Failed to request WechatWork user info"));

        assertThatThrownBy(() -> service.completeCallback(request, "state-sensitive", sensitiveCode))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessage("Failed to request WechatWork user info")
                .hasMessageNotContaining(sensitiveCode);

        verifyNoInteractions(identityBindingService, platformSessionService);
    }

    @Test
    void completeCallback_requiresUserIdForBrowserLogin() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("skillhub.oauth.wechatwork.state", "state-userid");
        WechatWorkUserInfoResponse userInfo = new WechatWorkUserInfoResponse(
                0,
                "ok",
                null,
                "openid-only-user",
                null,
                Map.of("OpenId", "openid-only-user")
        );
        when(apiClient.resolveUserInfo("code-openid")).thenReturn(userInfo);

        assertThatThrownBy(() -> service.completeCallback(request, "state-userid", "code-openid"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessage("WechatWork callback user id is required for employee login");

        verifyNoInteractions(identityBindingService, platformSessionService);
    }

    @Test
    void completeCallback_successBindsUserEstablishesSessionAndReturnsStoredReturnTo() {
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService platformSessionService = mock(PlatformSessionService.class);
        WechatWorkLoginFlowService service = newService(
                enabledProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute("skillhub.oauth.wechatwork.state", "state-ok");
        session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, "/dashboard/review");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("UserId", "wx-user-1");
        raw.put("DeviceId", null);
        WechatWorkUserInfoResponse userInfo = new WechatWorkUserInfoResponse(
                0,
                "ok",
                "wx-user-1",
                "openid-1",
                null,
                raw
        );
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr-1",
                "wechat-user",
                null,
                null,
                "wechatwork",
                Set.of("USER")
        );

        when(apiClient.resolveUserInfo("code-ok")).thenReturn(userInfo);
        when(identityBindingService.bindOrCreate(org.mockito.ArgumentMatchers.any(OAuthClaims.class), eq(UserStatus.ACTIVE)))
                .thenReturn(principal);

        String target = service.completeCallback(request, "state-ok", "code-ok");

        assertThat(target).isEqualTo("/dashboard/review");
        assertThat(session.getAttribute("skillhub.oauth.wechatwork.state")).isNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();

        ArgumentCaptor<OAuthClaims> claimsCaptor = ArgumentCaptor.forClass(OAuthClaims.class);
        verify(identityBindingService).bindOrCreate(claimsCaptor.capture(), eq(UserStatus.ACTIVE));
        OAuthClaims claims = claimsCaptor.getValue();
        assertThat(claims.provider()).isEqualTo("wechatwork");
        assertThat(claims.subject()).isEqualTo("wx-user-1");
        assertThat(claims.providerLogin()).isEqualTo("wx-user-1");
        assertThat(claims.email()).isNull();
        assertThat(claims.emailVerified()).isFalse();
        assertThat(claims.extra()).containsEntry("UserId", "wx-user-1").containsEntry("DeviceId", null);
        assertThat(claims.extra()).isNotSameAs(userInfo.raw());

        verify(platformSessionService).establishSession(principal, request);
    }

    private WechatWorkAuthProperties enabledProperties() {
        WechatWorkAuthProperties properties = new WechatWorkAuthProperties();
        properties.setEnabled(true);
        properties.setCorpId("corp-fr24");
        properties.setAgentId("100001");
        properties.setCallbackBaseUrl("https://login.fr24.example");
        return properties;
    }

    private WechatWorkLoginFlowService newService(WechatWorkAuthProperties properties,
                                                  WechatWorkApiClient apiClient,
                                                  IdentityBindingService identityBindingService,
                                                  PlatformSessionService platformSessionService) {
        return new WechatWorkLoginFlowService(
                properties,
                new AuthMethodVisibilityProperties(),
                apiClient,
                identityBindingService,
                platformSessionService
        );
    }
}
