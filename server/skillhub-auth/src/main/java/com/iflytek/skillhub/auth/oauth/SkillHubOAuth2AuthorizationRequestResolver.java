package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.config.AuthMethodVisibilityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 authorization request resolver that preserves a sanitized post-login redirect target in
 * the HTTP session.
 */
@Component
public class SkillHubOAuth2AuthorizationRequestResolver
        implements org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final OAuthLoginFlowService oauthLoginFlowService;
    private final AuthMethodVisibilityProperties authMethodVisibilityProperties;
    private final Map<String, ClientRegistration> clientRegistrations;

    public SkillHubOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                      OAuthLoginFlowService oauthLoginFlowService,
                                                      AuthMethodVisibilityProperties authMethodVisibilityProperties) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.authMethodVisibilityProperties = authMethodVisibilityProperties;
        this.clientRegistrations = indexClientRegistrations(clientRegistrationRepository);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = extractRegistrationId(request);
        if (registrationId != null && !isAllowedAndUsable(registrationId)) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        if (authorizationRequest != null) {
            oauthLoginFlowService.rememberReturnTo(request);
        }
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        if (!isAllowedAndUsable(clientRegistrationId)) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        if (authorizationRequest != null) {
            oauthLoginFlowService.rememberReturnTo(request);
        }
        return authorizationRequest;
    }

    private boolean isAllowedAndUsable(String clientRegistrationId) {
        return authMethodVisibilityProperties.allows(clientRegistrationId)
                && OAuthClientRegistrationPolicy.isUsable(clientRegistrations.get(clientRegistrationId));
    }

    private Map<String, ClientRegistration> indexClientRegistrations(
            ClientRegistrationRepository clientRegistrationRepository) {
        Map<String, ClientRegistration> registrations = new HashMap<>();
        if (clientRegistrationRepository instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof ClientRegistration registration) {
                    registrations.put(registration.getRegistrationId(), registration);
                }
            }
        }
        return registrations;
    }

    private String extractRegistrationId(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return null;
        }
        String prefix = request.getContextPath() + "/oauth2/authorization/";
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(prefix)) {
            return null;
        }
        String registrationId = requestUri.substring(prefix.length());
        int pathSeparator = registrationId.indexOf('/');
        if (pathSeparator >= 0) {
            registrationId = registrationId.substring(0, pathSeparator);
        }
        return registrationId.isBlank() ? null : registrationId;
    }
}
