package com.iflytek.skillhub.auth.privatesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

class PrivateSsoClientTest {

    private MockWebServer mockWebServer;
    private PrivateSsoClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        PrivateSsoProperties props = new PrivateSsoProperties();
        props.setBaseUrl(mockWebServer.url("/").toString());
        props.setConnectTimeout(java.time.Duration.ofSeconds(5));
        props.setReadTimeout(java.time.Duration.ofSeconds(10));
        WebClient webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
        client = new PrivateSsoClient(webClient, props, mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void authenticate_shouldReturnSsoUser_onSuccess() throws Exception {
        SsoUser expected = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        mockWebServer.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(expected))
                .setHeader("Content-Type", "application/json"));

        SsoUser result = client.authenticate("zhangsan", "encrypted-pw", null);

        assertThat(result.uid()).isEqualTo("U10042");
        assertThat(result.displayName()).isEqualTo("张三");
        assertThat(result.email()).isEqualTo("zhangsan@company.com");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/sso/authenticate");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
    }

    @Test
    void authenticate_shouldIncludeTwoFactorCode_whenProvided() throws Exception {
        SsoUser expected = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        mockWebServer.enqueue(new MockResponse()
                .setBody(mapper.writeValueAsString(expected))
                .setHeader("Content-Type", "application/json"));

        client.authenticate("zhangsan", "encrypted-pw", "123456");

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"twoFactorCode\":\"123456\"");
    }

    @Test
    void authenticate_shouldThrowInvalidCredentials_on401InvalidCredentials() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"用户名或密码错误\"}"));

        assertThatThrownBy(() -> client.authenticate("zhangsan", "wrong", null))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(ex -> {
                    AuthFlowException afe = (AuthFlowException) ex;
                    assertThat(afe.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(afe.getMessageCode()).isEqualTo("error.auth.invalidCredentials");
                });
    }

    @Test
    void authenticate_shouldThrowInvalid2faCode_on401Invalid2fa() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"INVALID_2FA_CODE\",\"message\":\"验证码错误\"}"));

        assertThatThrownBy(() -> client.authenticate("zhangsan", "pw", "wrong-code"))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(ex -> {
                    AuthFlowException afe = (AuthFlowException) ex;
                    assertThat(afe.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(afe.getMessageCode()).isEqualTo("error.auth.invalid2faCode");
                });
    }

    @Test
    void authenticate_shouldThrowAccountDisabled_on403() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"ACCOUNT_DISABLED\",\"message\":\"账号已被禁用\"}"));

        assertThatThrownBy(() -> client.authenticate("zhangsan", "pw", null))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(ex -> {
                    AuthFlowException afe = (AuthFlowException) ex;
                    assertThat(afe.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(afe.getMessageCode()).isEqualTo("error.auth.accountDisabled");
                });
    }

    @Test
    void authenticate_shouldThrowSsoUnavailable_onNetworkError() {
        PrivateSsoProperties props = new PrivateSsoProperties();
        props.setBaseUrl("http://localhost:1");
        props.setConnectTimeout(java.time.Duration.ofSeconds(1));
        props.setReadTimeout(java.time.Duration.ofSeconds(1));
        WebClient webClient = WebClient.builder().baseUrl(props.getBaseUrl()).build();
        PrivateSsoClient failingClient = new PrivateSsoClient(webClient, props, mapper);

        assertThatThrownBy(() -> failingClient.authenticate("zhangsan", "pw", null))
                .isInstanceOf(AuthFlowException.class)
                .satisfies(ex -> {
                    AuthFlowException afe = (AuthFlowException) ex;
                    assertThat(afe.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(afe.getMessageCode()).isEqualTo("error.auth.ssoUnavailable");
                });
    }
}
