package com.iflytek.skillhub.auth.wechatwork;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Coordinates WeChat Work browser login from authorization redirect through callback completion.
 */
@Service
public class WechatWorkLoginFlowService {

    private static final String WECHATWORK_QR_CONNECT_URL = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";
    private static final String WECHATWORK_CALLBACK_PATH = "/api/v1/auth/wechatwork/callback";
    private static final String SESSION_STATE_ATTRIBUTE = "skillhub.oauth.wechatwork.state";
    private static final int STATE_BYTES = 32;

    private final WechatWorkAuthProperties properties;
    private final WechatWorkApiClient apiClient;
    private final IdentityBindingService identityBindingService;
    private final PlatformSessionService platformSessionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public WechatWorkLoginFlowService(WechatWorkAuthProperties properties,
                                      WechatWorkApiClient apiClient,
                                      IdentityBindingService identityBindingService,
                                      PlatformSessionService platformSessionService) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.identityBindingService = identityBindingService;
        this.platformSessionService = platformSessionService;
    }

    public String buildAuthorizationRedirect(HttpServletRequest request, String returnTo) {
        assertEnabled();

        HttpSession session = request.getSession(true);
        String state = generateState();
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);

        session.setAttribute(SESSION_STATE_ATTRIBUTE, state);
        if (sanitizedReturnTo == null) {
            session.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        } else {
            session.setAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE, sanitizedReturnTo);
        }

        String callbackUrl = resolveCallbackUrl(request);
        return UriComponentsBuilder.fromHttpUrl(WECHATWORK_QR_CONNECT_URL)
                .queryParam("appid", properties.getCorpId())
                .queryParam("agentid", properties.getAgentId())
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public String completeCallback(HttpServletRequest request, String state, String code) {
        assertEnabled();

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new WechatWorkAuthException("WechatWork callback state is missing");
        }

        String expectedState = extractAndClearState(session);
        if (!StringUtils.hasText(state)) {
            throw new WechatWorkAuthException("WechatWork callback state is missing");
        }
        if (!state.equals(expectedState)) {
            throw new WechatWorkAuthException("WechatWork callback state validation failed");
        }
        if (!StringUtils.hasText(code)) {
            throw new WechatWorkAuthException("WechatWork callback code is missing");
        }

        String returnTo = consumeReturnTo(session);
        WechatWorkUserInfoResponse userInfo = apiClient.resolveUserInfo(code);
        String subject = requireUserId(userInfo.userId());
        Map<String, Object> extra = new LinkedHashMap<>(userInfo.raw());
        OAuthClaims claims = new OAuthClaims(
                "wechatwork",
                subject,
                null,
                false,
                subject,
                extra
        );

        PlatformPrincipal principal = identityBindingService.bindOrCreate(claims, UserStatus.ACTIVE);
        platformSessionService.establishSession(principal, request);
        return returnTo;
    }

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new WechatWorkAuthException("WechatWork login is disabled");
        }
    }

    private String extractAndClearState(HttpSession session) {
        Object raw = session.getAttribute(SESSION_STATE_ATTRIBUTE);
        session.removeAttribute(SESSION_STATE_ATTRIBUTE);
        if (!(raw instanceof String sessionState) || !StringUtils.hasText(sessionState)) {
            throw new WechatWorkAuthException("WechatWork callback state is missing");
        }
        return sessionState;
    }

    private String consumeReturnTo(HttpSession session) {
        Object raw = session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        session.removeAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE);
        if (raw instanceof String returnTo) {
            String sanitized = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
            if (sanitized != null) {
                return sanitized;
            }
        }
        return OAuthLoginRedirectSupport.DEFAULT_TARGET_URL;
    }

    private String resolveCallbackUrl(HttpServletRequest request) {
        String configuredBaseUrl = properties.getCallbackBaseUrl();
        if (StringUtils.hasText(configuredBaseUrl)) {
            return trimTrailingSlash(configuredBaseUrl.trim()) + WECHATWORK_CALLBACK_PATH;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme(request.getScheme())
                .host(request.getServerName());
        int port = request.getServerPort();
        if (port > 0) {
            builder.port(port);
        }
        return builder.path(WECHATWORK_CALLBACK_PATH).build().toUriString();
    }

    private String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String generateState() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String requireUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WechatWorkAuthException("WechatWork callback user id is missing");
        }
        return userId;
    }
}
