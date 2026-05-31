package com.iflytek.skillhub.auth.privatesso;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrivateSsoPropertiesTest {

    @Test
    void defaults() {
        PrivateSsoProperties props = new PrivateSsoProperties();
        assertThat(props.getBaseUrl()).isNull();
        assertThat(props.getConnectTimeout().toSeconds()).isEqualTo(5);
        assertThat(props.getReadTimeout().toSeconds()).isEqualTo(10);
        assertThat(props.getSm2PublicKey()).isNull();
        assertThat(props.getTwoFactor().isEnabled()).isFalse();
        assertThat(props.getIdentity().getProviderCode()).isEqualTo("private-sso");
        assertThat(props.getIdentity().getInitialStatus()).isEqualTo("ACTIVE");
    }
}
