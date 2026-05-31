package com.iflytek.skillhub.auth.privatesso;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skillhub.auth.private-sso")
public class PrivateSsoProperties {

    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private String sm2PublicKey;
    private TwoFactor twoFactor = new TwoFactor();
    private Identity identity = new Identity();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public String getSm2PublicKey() { return sm2PublicKey; }
    public void setSm2PublicKey(String sm2PublicKey) { this.sm2PublicKey = sm2PublicKey; }

    public TwoFactor getTwoFactor() { return twoFactor; }
    public void setTwoFactor(TwoFactor twoFactor) { this.twoFactor = twoFactor; }

    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }

    public static class TwoFactor {
        private boolean enabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Identity {
        private String providerCode = "private-sso";
        private String initialStatus = "ACTIVE";
        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getInitialStatus() { return initialStatus; }
        public void setInitialStatus(String initialStatus) { this.initialStatus = initialStatus; }
    }
}
