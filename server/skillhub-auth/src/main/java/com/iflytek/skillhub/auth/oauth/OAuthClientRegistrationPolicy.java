package com.iflytek.skillhub.auth.oauth;

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Determines whether a configured OAuth2 client is usable as a public login entry point.
 */
public final class OAuthClientRegistrationPolicy {

    private OAuthClientRegistrationPolicy() {
    }

    public static boolean isUsable(OAuth2ClientProperties.Registration registration) {
        return registration != null
                && isRealSecret(registration.getClientId())
                && isRealSecret(registration.getClientSecret());
    }

    public static boolean isUsable(ClientRegistration registration) {
        return registration != null
                && isRealSecret(registration.getClientId())
                && isRealSecret(registration.getClientSecret());
    }

    private static boolean isRealSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !normalized.contains("placeholder")
                && !normalized.equals("changeme")
                && !normalized.equals("change-me")
                && !normalized.equals("dummy");
    }
}
