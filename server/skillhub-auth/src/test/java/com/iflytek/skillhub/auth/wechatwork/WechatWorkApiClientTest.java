package com.iflytek.skillhub.auth.wechatwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WechatWorkApiClientTest {

    @Test
    void resolveUserInfo_returnsEmployeeUserWhenApiCallsSucceed() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=test-corp&corpsecret=test-secret"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","access_token":"token-123","expires_in":7200}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-123&code=code-1"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","UserId":"alice","OpenId":"openid-alice","DeviceId":"device-1"}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        WechatWorkApiClient client = new WechatWorkApiClient(
                properties(true),
                restClientBuilder,
                fixedClock()
        );

        WechatWorkUserInfoResponse response = client.resolveUserInfo("code-1");

        assertThat(response.userId()).isEqualTo("alice");
        assertThat(response.openId()).isEqualTo("openid-alice");
        assertThat(response.deviceId()).isEqualTo("device-1");
        assertThat(response.raw()).containsEntry("UserId", "alice");
        server.verify();
    }

    @Test
    void resolveUserInfo_deniesOpenIdOnlyUserWhenEmployeeLoginOnlyEnabled() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=test-corp&corpsecret=test-secret"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","access_token":"token-123","expires_in":7200}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-123&code=code-2"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","OpenId":"openid-only","DeviceId":"device-2"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        WechatWorkApiClient client = new WechatWorkApiClient(
                properties(true),
                restClientBuilder,
                fixedClock()
        );

        assertThatThrownBy(() -> client.resolveUserInfo("code-2"))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessageContaining("employee");
        server.verify();
    }

    @Test
    void resolveUserInfo_rejectsBlankCode() {
        WechatWorkApiClient client = new WechatWorkApiClient(
                properties(true),
                RestClient.builder(),
                fixedClock()
        );

        assertThatThrownBy(() -> client.resolveUserInfo("   "))
                .isInstanceOf(WechatWorkAuthException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void resolveUserInfo_reusesCachedAccessTokenBeforeExpiry() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=test-corp&corpsecret=test-secret"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","access_token":"token-abc","expires_in":7200}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-abc&code=first-code"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","UserId":"user-1"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(
                        "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-abc&code=second-code"))
                .andRespond(withSuccess(
                        """
                        {"errcode":0,"errmsg":"ok","UserId":"user-2"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        WechatWorkApiClient client = new WechatWorkApiClient(
                properties(true),
                restClientBuilder,
                fixedClock()
        );

        WechatWorkUserInfoResponse first = client.resolveUserInfo("first-code");
        WechatWorkUserInfoResponse second = client.resolveUserInfo("second-code");

        assertThat(first.userId()).isEqualTo("user-1");
        assertThat(second.userId()).isEqualTo("user-2");
        server.verify();
    }

    private WechatWorkAuthProperties properties(boolean employeeLoginOnly) {
        WechatWorkAuthProperties properties = new WechatWorkAuthProperties();
        properties.setCorpId("test-corp");
        properties.setCorpSecret("test-secret");
        properties.setEmployeeLoginOnly(employeeLoginOnly);
        return properties;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC);
    }
}
