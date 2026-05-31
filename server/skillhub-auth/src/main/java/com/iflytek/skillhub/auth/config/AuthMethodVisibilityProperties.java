package com.iflytek.skillhub.auth.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.methods")
public class AuthMethodVisibilityProperties {

    private List<String> visibleProviders = new ArrayList<>();

    public List<String> getVisibleProviders() {
        return visibleProviders;
    }

    public void setVisibleProviders(List<String> visibleProviders) {
        this.visibleProviders = normalizeProviders(visibleProviders);
    }

    public boolean allows(String provider) {
        if (visibleProviders == null || visibleProviders.isEmpty()) {
            return true;
        }
        if (provider == null || provider.isBlank()) {
            return false;
        }
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        return visibleProviders.contains(normalizedProvider);
    }

    private List<String> normalizeProviders(List<String> configuredProviders) {
        if (configuredProviders == null || configuredProviders.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String candidate : configuredProviders) {
            if (candidate == null) {
                continue;
            }
            String[] fragments = candidate.split(",");
            for (String fragment : fragments) {
                String provider = fragment.trim();
                if (!provider.isEmpty()) {
                    normalized.add(provider.toLowerCase(Locale.ROOT));
                }
            }
        }
        return normalized;
    }
}
