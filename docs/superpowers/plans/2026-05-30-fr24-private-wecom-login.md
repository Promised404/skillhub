# FR24 Private WeCom Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add FR24-branded private deployment support with PC-browser WeCom scan login as the employee-facing sign-in path.

**Architecture:** Keep WeCom scan login outside Spring Security's standard OAuth2 provider chain because WeCom returns a callback code that must be exchanged through enterprise application APIs. Add a focused WeCom auth service in `skillhub-auth`, expose browser redirects through `skillhub-app`, reuse `IdentityBindingService` and `PlatformSessionService`, and let the existing auth-method catalog drive frontend rendering. Add a small shared frontend brand module so FR24 branding is centralized and the default multi-auth UI still works for non-FR24 deployments.

**Tech Stack:** Spring Boot 3.2.3, Java 21, Spring Security session auth, RestClient, JUnit 5/MockMvc/Mockito, React 19, TypeScript, TanStack Query, i18next, Tailwind CSS, Vitest.

---

## File Structure

Backend files:

- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthProperties.java`: configuration for WeCom login.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkApiClient.java`: calls WeCom token and user-info APIs and caches access tokens.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAccessTokenResponse.java`: response DTO for `gettoken`.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkUserInfoResponse.java`: response DTO for `getuserinfo`.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthException.java`: authentication-flow exception for WeCom failures.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowService.java`: owns state, redirect URL creation, callback completion, and SkillHub session creation.
- Create `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/package-info.java`: package documentation.
- Create `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AuthMethodVisibilityProperties.java`: configurable provider allowlist for login methods.
- Create `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/WechatWorkAuthController.java`: HTTP redirect endpoints under `/api/v1/auth/wechatwork`.
- Modify `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java`: include WeCom and apply method visibility filtering.
- Modify `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java`: permit WeCom authorize/callback endpoints.
- Modify `server/skillhub-app/src/main/resources/application.yml`: document safe defaults and environment variable bindings.
- Modify `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AuthControllerTest.java`: cover auth-method catalog behavior.
- Create `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/WechatWorkAuthControllerTest.java`: cover redirect/callback controller flow.
- Create `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowServiceTest.java`: cover state and callback service behavior.

Frontend files:

- Create `web/src/shared/lib/brand.ts`: central FR24 brand constants.
- Create `web/public/wechatwork-logo.svg`: stable local icon for WeCom login button.
- Modify `web/src/features/auth/login-button.tsx`: support WeCom icon and optional provider list.
- Modify `web/src/pages/login.tsx`: render single-method enterprise SSO UI when WeCom is the only exposed method.
- Modify `web/src/app/layout.tsx`: show FR24 SkillHub brand in header/footer.
- Modify `web/src/pages/landing.tsx`: show FR24 SkillHub in first viewport.
- Modify `web/src/i18n/locales/en.json`: add FR24 and WeCom login copy.
- Modify `web/src/i18n/locales/zh.json`: add FR24 and WeCom login copy.
- Modify `web/src/pages/login.test.tsx`: cover enterprise-only login UI.
- Modify `web/src/features/auth/login-button.test.ts`: cover WeCom method rendering.
- Modify `web/src/app/layout.test.ts`: cover FR24 shell branding.

---

### Task 1: Backend Configuration and Auth Method Catalog

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthProperties.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/package-info.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AuthMethodVisibilityProperties.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java`
- Modify: `server/skillhub-app/src/main/resources/application.yml`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AuthControllerTest.java`

- [ ] **Step 1: Write failing catalog tests**

Add these test methods to `AuthControllerTest`:

```java
@Test
void methodsShouldExposeWechatWorkWhenEnabled() throws Exception {
    mockMvc.perform(get("/api/v1/auth/methods").param("returnTo", "/dashboard")
            .with(request -> {
                request.setAttribute("unused", "unused");
                return request;
            }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data[?(@.id=='oauth-wechatwork')].methodType").value(hasItems("OAUTH_REDIRECT")))
        .andExpect(jsonPath("$.data[?(@.id=='oauth-wechatwork')].provider").value(hasItems("wechatwork")))
        .andExpect(jsonPath("$.data[?(@.id=='oauth-wechatwork')].actionUrl")
            .value(hasItems("/api/v1/auth/wechatwork/authorize?returnTo=%2Fdashboard")));
}

@Test
void methodsShouldApplyVisibleProviderAllowlist() throws Exception {
    mockMvc.perform(get("/api/v1/auth/methods").param("returnTo", "/dashboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].provider").value(hasItems("wechatwork")))
        .andExpect(jsonPath("$.data[?(@.provider=='local')]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.provider=='github')]").isEmpty())
        .andExpect(jsonPath("$.data[?(@.provider=='gitlab')]").isEmpty());
}
```

Update `@TestPropertySource` in the same class so these tests run with WeCom enabled and visible:

```java
"skillhub.auth.wechatwork.enabled=true",
"skillhub.auth.wechatwork.corp-id=fr24-corp",
"skillhub.auth.wechatwork.agent-id=100001",
"skillhub.auth.wechatwork.corp-secret=test-secret",
"skillhub.auth.wechatwork.display-name=WeCom",
"skillhub.auth.methods.visible-providers=wechatwork"
```

- [ ] **Step 2: Run the catalog test and verify it fails**

Run:

```bash
make test-backend-app TEST=AuthControllerTest
```

Expected: FAIL because `oauth-wechatwork` is not returned and `skillhub.auth.methods.visible-providers` is not implemented.

- [ ] **Step 3: Add WeCom auth properties**

Create `WechatWorkAuthProperties.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.wechatwork")
public class WechatWorkAuthProperties {
    private boolean enabled;
    private String corpId = "";
    private String agentId = "";
    private String corpSecret = "";
    private String callbackBaseUrl = "";
    private String displayName = "WeCom";
    private boolean employeeLoginOnly = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getCorpSecret() { return corpSecret; }
    public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }
    public String getCallbackBaseUrl() { return callbackBaseUrl; }
    public void setCallbackBaseUrl(String callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isEmployeeLoginOnly() { return employeeLoginOnly; }
    public void setEmployeeLoginOnly(boolean employeeLoginOnly) { this.employeeLoginOnly = employeeLoginOnly; }
}
```

Create `package-info.java`:

```java
/**
 * Enterprise WeChat browser login support for private SkillHub deployments.
 */
package com.iflytek.skillhub.auth.wechatwork;
```

- [ ] **Step 4: Add auth-method visibility properties**

Create `AuthMethodVisibilityProperties.java`:

```java
package com.iflytek.skillhub.config;

import java.util.ArrayList;
import java.util.List;
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
        this.visibleProviders = visibleProviders == null ? new ArrayList<>() : new ArrayList<>(visibleProviders);
    }

    public boolean allows(String provider) {
        return visibleProviders.isEmpty() || visibleProviders.contains(provider);
    }
}
```

- [ ] **Step 5: Extend AuthMethodCatalog**

Modify the constructor to inject `WechatWorkAuthProperties` and `AuthMethodVisibilityProperties`. Add WeCom before other OAuth methods, then filter every method by provider:

```java
private final WechatWorkAuthProperties wechatWorkAuthProperties;
private final AuthMethodVisibilityProperties visibilityProperties;

public AuthMethodCatalog(OAuth2ClientProperties oAuth2ClientProperties,
                         DirectAuthProperties directAuthProperties,
                         AuthSessionBootstrapProperties sessionBootstrapProperties,
                         List<DirectAuthProvider> directAuthProviders,
                         List<PassiveSessionAuthenticator> passiveSessionAuthenticators,
                         WechatWorkAuthProperties wechatWorkAuthProperties,
                         AuthMethodVisibilityProperties visibilityProperties) {
    this.oAuth2ClientProperties = oAuth2ClientProperties;
    this.directAuthProperties = directAuthProperties;
    this.sessionBootstrapProperties = sessionBootstrapProperties;
    this.directAuthProviders = directAuthProviders;
    this.passiveSessionAuthenticators = passiveSessionAuthenticators;
    this.wechatWorkAuthProperties = wechatWorkAuthProperties;
    this.visibilityProperties = visibilityProperties;
}
```

Inside `listMethods`, add:

```java
if (wechatWorkAuthProperties.isEnabled() && visibilityProperties.allows("wechatwork")) {
    methods.add(new AuthMethodResponse(
        "oauth-wechatwork",
        "OAUTH_REDIRECT",
        "wechatwork",
        wechatWorkAuthProperties.getDisplayName(),
        buildWechatWorkAuthorizationUrl(sanitizedReturnTo)
    ));
}
```

Filter existing additions:

```java
if (visibilityProperties.allows("local")) {
    methods.add(new AuthMethodResponse(
        "local-password",
        "PASSWORD",
        "local",
        "Local Account",
        "/api/v1/auth/local/login"
    ));
}
```

Use the same `visibilityProperties.allows(entry.getKey())`, `allows(provider.providerCode())`, and `allows(provider.providerCode())` checks for OAuth, direct, and session-bootstrap providers.

Add:

```java
private String buildWechatWorkAuthorizationUrl(String returnTo) {
    String baseUrl = "/api/v1/auth/wechatwork/authorize";
    if (returnTo == null) {
        return baseUrl;
    }
    return baseUrl + "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
}
```

- [ ] **Step 6: Add safe defaults to application.yml**

Add under `skillhub.auth`:

```yaml
    wechatwork:
      enabled: ${SKILLHUB_AUTH_WECHATWORK_ENABLED:false}
      corp-id: ${SKILLHUB_AUTH_WECHATWORK_CORP_ID:}
      agent-id: ${SKILLHUB_AUTH_WECHATWORK_AGENT_ID:}
      corp-secret: ${SKILLHUB_AUTH_WECHATWORK_CORP_SECRET:}
      callback-base-url: ${SKILLHUB_AUTH_WECHATWORK_CALLBACK_BASE_URL:}
      display-name: ${SKILLHUB_AUTH_WECHATWORK_DISPLAY_NAME:WeCom}
    methods:
      visible-providers: ${SKILLHUB_AUTH_METHODS_VISIBLE_PROVIDERS:}
```

- [ ] **Step 7: Run the catalog tests**

Run:

```bash
make test-backend-app TEST=AuthControllerTest
```

Expected: PASS for the new WeCom catalog assertions and existing auth controller assertions.

- [ ] **Step 8: Commit backend catalog work**

```bash
git add server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthProperties.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/package-info.java \
  server/skillhub-app/src/main/java/com/iflytek/skillhub/config/AuthMethodVisibilityProperties.java \
  server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AuthMethodCatalog.java \
  server/skillhub-app/src/main/resources/application.yml \
  server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AuthControllerTest.java
git commit -m "feat(auth): expose WeCom login method"
```

---

### Task 2: WeCom API Client

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAccessTokenResponse.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkUserInfoResponse.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthException.java`
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkApiClient.java`
- Test: `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkApiClientTest.java`

- [ ] **Step 1: Write failing API client tests**

Create `WechatWorkApiClientTest.java`:

```java
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
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class WechatWorkApiClientTest {
    @Test
    void resolveUserIdLoadsTokenThenUserInfo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatWorkAuthProperties properties = properties();
        WechatWorkApiClient client = new WechatWorkApiClient(
            builder,
            properties,
            Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC)
        );

        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp-1&corpsecret=secret-1"))
            .andRespond(withSuccess("""
                {"errcode":0,"errmsg":"ok","access_token":"token-1","expires_in":7200}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-1&code=code-1"))
            .andRespond(withSuccess("""
                {"errcode":0,"errmsg":"ok","UserId":"alice","DeviceId":"device-1"}
                """, MediaType.APPLICATION_JSON));

        WechatWorkUserInfoResponse response = client.resolveUserInfo("code-1");

        assertThat(response.userId()).isEqualTo("alice");
        server.verify();
    }

    @Test
    void resolveUserIdRejectsNonEnterpriseUserWhenEmployeeOnly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatWorkApiClient client = new WechatWorkApiClient(
            builder,
            properties(),
            Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC)
        );

        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=corp-1&corpsecret=secret-1"))
            .andRespond(withSuccess("""
                {"errcode":0,"errmsg":"ok","access_token":"token-1","expires_in":7200}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=token-1&code=code-1"))
            .andRespond(withSuccess("""
                {"errcode":0,"errmsg":"ok","OpenId":"external-open-id"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.resolveUserInfo("code-1"))
            .isInstanceOf(WechatWorkAuthException.class)
            .hasMessageContaining("WeCom user is not an enterprise member");
    }

    private WechatWorkAuthProperties properties() {
        WechatWorkAuthProperties properties = new WechatWorkAuthProperties();
        properties.setEnabled(true);
        properties.setCorpId("corp-1");
        properties.setCorpSecret("secret-1");
        properties.setAgentId("100001");
        properties.setEmployeeLoginOnly(true);
        return properties;
    }
}
```

- [ ] **Step 2: Run the API client test and verify it fails**

Run:

```bash
cd server && ./mvnw -pl skillhub-auth -am test -Dtest=WechatWorkApiClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL because WeCom client classes do not exist.

- [ ] **Step 3: Add response records and exception**

Create `WechatWorkAccessTokenResponse.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WechatWorkAccessTokenResponse(
    @JsonProperty("errcode") int errorCode,
    @JsonProperty("errmsg") String errorMessage,
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") long expiresIn
) {}
```

Create `WechatWorkUserInfoResponse.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record WechatWorkUserInfoResponse(
    @JsonProperty("errcode") int errorCode,
    @JsonProperty("errmsg") String errorMessage,
    @JsonProperty("UserId") String userId,
    @JsonProperty("OpenId") String openId,
    @JsonProperty("DeviceId") String deviceId,
    Map<String, Object> raw
) {}
```

Create `WechatWorkAuthException.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import org.springframework.security.core.AuthenticationException;

public class WechatWorkAuthException extends AuthenticationException {
    public WechatWorkAuthException(String message) {
        super(message);
    }

    public WechatWorkAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Add WeCom API client implementation**

Create `WechatWorkApiClient.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class WechatWorkApiClient {
    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com";
    private final RestClient restClient;
    private final WechatWorkAuthProperties properties;
    private final Clock clock;
    private volatile CachedAccessToken cachedAccessToken;

    public WechatWorkApiClient(RestClient.Builder restClientBuilder,
                               WechatWorkAuthProperties properties) {
        this(restClientBuilder, properties, Clock.systemUTC());
    }

    WechatWorkApiClient(RestClient.Builder restClientBuilder,
                        WechatWorkAuthProperties properties,
                        Clock clock) {
        this.restClient = restClientBuilder
            .baseUrl(API_BASE_URL)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.properties = properties;
        this.clock = clock;
    }

    public WechatWorkUserInfoResponse resolveUserInfo(String code) {
        if (!StringUtils.hasText(code)) {
            throw new WechatWorkAuthException("Missing WeCom callback code");
        }
        String token = accessToken();
        Map<String, Object> body = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/cgi-bin/auth/getuserinfo")
                .queryParam("access_token", token)
                .queryParam("code", code)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (body == null) {
            throw new WechatWorkAuthException("Empty WeCom user info response");
        }
        int errorCode = number(body.get("errcode"));
        if (errorCode != 0) {
            throw new WechatWorkAuthException("WeCom user info failed: " + errorCode);
        }
        String userId = string(body.get("UserId"));
        String openId = string(body.get("OpenId"));
        if (properties.isEmployeeLoginOnly() && !StringUtils.hasText(userId)) {
            throw new WechatWorkAuthException("WeCom user is not an enterprise member");
        }
        return new WechatWorkUserInfoResponse(
            errorCode,
            string(body.get("errmsg")),
            userId,
            openId,
            string(body.get("DeviceId")),
            body
        );
    }

    private String accessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && current.expiresAt().isAfter(clock.instant())) {
            return current.token();
        }
        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && current.expiresAt().isAfter(clock.instant())) {
                return current.token();
            }
            WechatWorkAccessTokenResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/cgi-bin/gettoken")
                    .queryParam("corpid", properties.getCorpId())
                    .queryParam("corpsecret", properties.getCorpSecret())
                    .build())
                .retrieve()
                .body(WechatWorkAccessTokenResponse.class);
            if (response == null || response.errorCode() != 0 || !StringUtils.hasText(response.accessToken())) {
                throw new WechatWorkAuthException("WeCom access token failed");
            }
            Instant expiresAt = clock.instant().plusSeconds(Math.max(60, response.expiresIn() - 120));
            cachedAccessToken = new CachedAccessToken(response.accessToken(), expiresAt);
            return response.accessToken();
        }
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private record CachedAccessToken(String token, Instant expiresAt) {}
}
```

- [ ] **Step 5: Run the API client tests**

Run:

```bash
cd server && ./mvnw -pl skillhub-auth -am test -Dtest=WechatWorkApiClientTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Commit WeCom API client**

```bash
git add server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAccessTokenResponse.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkUserInfoResponse.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkAuthException.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkApiClient.java \
  server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkApiClientTest.java
git commit -m "feat(auth): add WeCom API client"
```

---

### Task 3: WeCom Browser Login Flow and Controller

**Files:**
- Create: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowService.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/WechatWorkAuthController.java`
- Modify: `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java`
- Test: `server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowServiceTest.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/WechatWorkAuthControllerTest.java`

- [ ] **Step 1: Write failing login-flow service tests**

Create `WechatWorkLoginFlowServiceTest.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WechatWorkLoginFlowServiceTest {
    @Test
    void buildAuthorizationRedirectStoresStateAndReturnTo() {
        WechatWorkLoginFlowService service = service();
        MockHttpServletRequest request = new MockHttpServletRequest();

        String redirect = service.buildAuthorizationRedirect(request, "/dashboard/publish");

        assertThat(redirect).startsWith("https://open.work.weixin.qq.com/wwopen/sso/qrConnect?");
        assertThat(redirect).contains("appid=corp-1");
        assertThat(redirect).contains("agentid=100001");
        assertThat(request.getSession().getAttribute("WECHATWORK_AUTH_STATE")).isInstanceOf(String.class);
        assertThat(request.getSession().getAttribute("WECHATWORK_RETURN_TO")).isEqualTo("/dashboard/publish");
    }

    @Test
    void completeCallbackRejectsStateMismatch() {
        WechatWorkLoginFlowService service = service();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("WECHATWORK_AUTH_STATE", "expected");

        assertThatThrownBy(() -> service.completeCallback(request, "actual", "code-1"))
            .isInstanceOf(WechatWorkAuthException.class)
            .hasMessageContaining("Invalid WeCom login state");
    }

    private WechatWorkLoginFlowService service() {
        WechatWorkAuthProperties properties = new WechatWorkAuthProperties();
        properties.setEnabled(true);
        properties.setCorpId("corp-1");
        properties.setAgentId("100001");
        properties.setCorpSecret("secret-1");
        properties.setCallbackBaseUrl("https://skillhub.fr24.test");
        WechatWorkApiClient apiClient = mock(WechatWorkApiClient.class);
        IdentityBindingService identityBindingService = mock(IdentityBindingService.class);
        PlatformSessionService sessionService = mock(PlatformSessionService.class);
        given(apiClient.resolveUserInfo("code-1")).willReturn(
            new WechatWorkUserInfoResponse(0, "ok", "alice", null, "device-1", Map.of("UserId", "alice"))
        );
        given(identityBindingService.bindOrCreate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .willReturn(new PlatformPrincipal("usr_1", "alice", null, null, "wechatwork", Set.of("USER")));
        return new WechatWorkLoginFlowService(properties, apiClient, identityBindingService, sessionService);
    }
}
```

- [ ] **Step 2: Run the login-flow service test and verify it fails**

Run:

```bash
cd server && ./mvnw -pl skillhub-auth test -Dtest=WechatWorkLoginFlowServiceTest
```

Expected: FAIL because `WechatWorkLoginFlowService` does not exist.

- [ ] **Step 3: Implement WechatWorkLoginFlowService**

Create `WechatWorkLoginFlowService.java`:

```java
package com.iflytek.skillhub.auth.wechatwork;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WechatWorkLoginFlowService {
    static final String SESSION_STATE = "WECHATWORK_AUTH_STATE";
    static final String SESSION_RETURN_TO = "WECHATWORK_RETURN_TO";

    private final SecureRandom secureRandom = new SecureRandom();
    private final WechatWorkAuthProperties properties;
    private final WechatWorkApiClient apiClient;
    private final IdentityBindingService identityBindingService;
    private final PlatformSessionService sessionService;

    public WechatWorkLoginFlowService(WechatWorkAuthProperties properties,
                                      WechatWorkApiClient apiClient,
                                      IdentityBindingService identityBindingService,
                                      PlatformSessionService sessionService) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.identityBindingService = identityBindingService;
        this.sessionService = sessionService;
    }

    public String buildAuthorizationRedirect(HttpServletRequest request, String returnTo) {
        assertEnabled();
        String state = randomState();
        String sanitizedReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_STATE, state);
        if (sanitizedReturnTo == null) {
            session.removeAttribute(SESSION_RETURN_TO);
        } else {
            session.setAttribute(SESSION_RETURN_TO, sanitizedReturnTo);
        }
        String redirectUri = callbackUrl(request);
        return "https://open.work.weixin.qq.com/wwopen/sso/qrConnect"
            + "?appid=" + encode(properties.getCorpId())
            + "&agentid=" + encode(properties.getAgentId())
            + "&redirect_uri=" + encode(redirectUri)
            + "&state=" + encode(state);
    }

    public String completeCallback(HttpServletRequest request, String state, String code) {
        assertEnabled();
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new WechatWorkAuthException("Missing WeCom login session");
        }
        Object expectedState = session.getAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_STATE);
        if (!(expectedState instanceof String expected) || !expected.equals(state)) {
            throw new WechatWorkAuthException("Invalid WeCom login state");
        }
        if (!StringUtils.hasText(code)) {
            throw new WechatWorkAuthException("Missing WeCom callback code");
        }
        WechatWorkUserInfoResponse userInfo = apiClient.resolveUserInfo(code);
        var extra = new HashMap<String, Object>();
        if (userInfo.raw() != null) {
            extra.putAll(userInfo.raw());
        }
        PlatformPrincipal principal = identityBindingService.bindOrCreate(
            new OAuthClaims("wechatwork", userInfo.userId(), null, false, userInfo.userId(), extra),
            UserStatus.ACTIVE
        );
        sessionService.establishSession(principal, request);
        Object returnTo = session.getAttribute(SESSION_RETURN_TO);
        session.removeAttribute(SESSION_RETURN_TO);
        return returnTo instanceof String value ? value : "/dashboard";
    }

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new WechatWorkAuthException("WeCom login is disabled");
        }
    }

    private String callbackUrl(HttpServletRequest request) {
        String base = StringUtils.hasText(properties.getCallbackBaseUrl())
            ? properties.getCallbackBaseUrl()
            : request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "");
        return base.replaceAll("/+$", "") + "/api/v1/auth/wechatwork/callback";
    }

    private String randomState() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Add controller and route policy**

Create `WechatWorkAuthController.java`:

```java
package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.wechatwork.WechatWorkAuthException;
import com.iflytek.skillhub.auth.wechatwork.WechatWorkLoginFlowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/api/v1/auth/wechatwork")
public class WechatWorkAuthController {
    private final WechatWorkLoginFlowService loginFlowService;

    public WechatWorkAuthController(WechatWorkLoginFlowService loginFlowService) {
        this.loginFlowService = loginFlowService;
    }

    @GetMapping("/authorize")
    public RedirectView authorize(@RequestParam(name = "returnTo", required = false) String returnTo,
                                  HttpServletRequest request) {
        return new RedirectView(loginFlowService.buildAuthorizationRedirect(request, returnTo));
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam(name = "state", required = false) String state,
                                 @RequestParam(name = "code", required = false) String code,
                                 HttpServletRequest request) {
        try {
            return new RedirectView(loginFlowService.completeCallback(request, state, code));
        } catch (WechatWorkAuthException ex) {
            return new RedirectView("/login?reason=ssoFailed");
        }
    }
}
```

Add this route policy to `RouteSecurityPolicyRegistry.AUTHORIZATION_POLICIES` near other auth routes:

```java
RouteAuthorizationPolicy.permitAll(null, "/api/v1/auth/wechatwork/**"),
```

- [ ] **Step 5: Run login-flow tests**

Run:

```bash
cd server && ./mvnw -pl skillhub-auth test -Dtest=WechatWorkLoginFlowServiceTest
```

Expected: PASS.

- [ ] **Step 6: Add MockMvc controller tests**

Create `WechatWorkAuthControllerTest.java`:

```java
package com.iflytek.skillhub.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.wechatwork.WechatWorkAuthException;
import com.iflytek.skillhub.auth.wechatwork.WechatWorkLoginFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WechatWorkAuthController.class)
class WechatWorkAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WechatWorkLoginFlowService loginFlowService;

    @Test
    void authorizeRedirectsToWechatWork() throws Exception {
        given(loginFlowService.buildAuthorizationRedirect(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("/dashboard")))
            .willReturn("https://open.work.weixin.qq.com/wwopen/sso/qrConnect?state=abc");

        mockMvc.perform(get("/api/v1/auth/wechatwork/authorize").param("returnTo", "/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://open.work.weixin.qq.com/wwopen/sso/qrConnect?state=abc"));
    }

    @Test
    void callbackRedirectsToReturnTargetAfterSuccess() throws Exception {
        given(loginFlowService.completeCallback(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("abc"), org.mockito.ArgumentMatchers.eq("code-1")))
            .willReturn("/dashboard");

        mockMvc.perform(get("/api/v1/auth/wechatwork/callback").param("state", "abc").param("code", "code-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void callbackRedirectsToLoginOnFailure() throws Exception {
        given(loginFlowService.completeCallback(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("bad"), org.mockito.ArgumentMatchers.eq("code-1")))
            .willThrow(new WechatWorkAuthException("Invalid WeCom login state"));

        mockMvc.perform(get("/api/v1/auth/wechatwork/callback").param("state", "bad").param("code", "code-1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?reason=ssoFailed"));
    }
}
```

- [ ] **Step 7: Run backend auth tests**

Run:

```bash
make test-backend-app TEST=WechatWorkAuthControllerTest
make test-backend-app TEST=AuthControllerTest
```

Expected: PASS.

- [ ] **Step 8: Commit WeCom browser flow**

```bash
git add server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowService.java \
  server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/WechatWorkAuthController.java \
  server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java \
  server/skillhub-auth/src/test/java/com/iflytek/skillhub/auth/wechatwork/WechatWorkLoginFlowServiceTest.java \
  server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/WechatWorkAuthControllerTest.java
git commit -m "feat(auth): add WeCom scan login flow"
```

---

### Task 4: Frontend Brand Constants and Shell Branding

**Files:**
- Create: `web/src/shared/lib/brand.ts`
- Modify: `web/src/app/layout.tsx`
- Modify: `web/src/pages/landing.tsx`
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`
- Test: `web/src/app/layout.test.ts`

- [ ] **Step 1: Write failing layout branding test**

Add to `layout.test.ts`:

```ts
import { renderToStaticMarkup } from 'react-dom/server'

it('renders FR24 SkillHub branding in the shell', () => {
  const html = renderToStaticMarkup(<Layout />)

  expect(html).toContain('FR24 SkillHub')
  expect(html).toContain('https://www.flightroutes24.com/images/logo-white.eea24fa8.svg')
})
```

- [ ] **Step 2: Run the layout test and verify it fails**

Run:

```bash
cd web && pnpm vitest run src/app/layout.test.ts
```

Expected: FAIL because layout still renders plain `SkillHub`.

- [ ] **Step 3: Add brand constants**

Create `brand.ts`:

```ts
export const appBrand = {
  companyShortName: 'FR24',
  productName: 'SkillHub',
  displayName: 'FR24 SkillHub',
  logoUrl: 'https://www.flightroutes24.com/images/logo-white.eea24fa8.svg',
  homepageUrl: 'https://www.flightroutes24.com/',
} as const
```

- [ ] **Step 4: Update layout header and footer**

Import `appBrand` in `layout.tsx`:

```ts
import { appBrand } from '@/shared/lib/brand'
```

Replace the header brand link with:

```tsx
<Link to="/" className="flex items-center gap-3 text-xl font-semibold tracking-tight">
  <span className="flex h-9 w-20 items-center justify-center rounded-md bg-slate-950 px-2">
    <img src={appBrand.logoUrl} alt={appBrand.companyShortName} className="max-h-5 w-auto" />
  </span>
  <span className="text-brand-gradient">{appBrand.productName}</span>
</Link>
```

Replace the footer brand block text with `appBrand.displayName` and the same logo chip.

- [ ] **Step 5: Update landing first viewport**

Import `appBrand` in `landing.tsx` and replace the hero H1 with:

```tsx
<div className="mb-5 flex h-14 w-32 items-center justify-center rounded-lg bg-slate-950 px-3 shadow-sm">
  <img src={appBrand.logoUrl} alt={appBrand.companyShortName} className="max-h-7 w-auto" />
</div>
<h1 className="text-5xl md:text-7xl font-bold tracking-tight text-brand-gradient mb-4">
  {appBrand.displayName}
</h1>
```

- [ ] **Step 6: Add i18n copy**

In both locale files, add:

```json
"brand": {
  "displayName": "FR24 SkillHub"
}
```

Keep existing keys intact and valid JSON.

- [ ] **Step 7: Run frontend brand tests**

Run:

```bash
cd web && pnpm vitest run src/app/layout.test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit frontend brand shell**

```bash
git add web/src/shared/lib/brand.ts web/src/app/layout.tsx web/src/pages/landing.tsx \
  web/src/i18n/locales/en.json web/src/i18n/locales/zh.json web/src/app/layout.test.ts
git commit -m "feat(web): add FR24 branding"
```

---

### Task 5: Frontend WeCom-Only Login UI

**Files:**
- Create: `web/public/wechatwork-logo.svg`
- Modify: `web/src/features/auth/login-button.tsx`
- Modify: `web/src/pages/login.tsx`
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`
- Test: `web/src/features/auth/login-button.test.ts`
- Test: `web/src/pages/login.test.tsx`

- [ ] **Step 1: Write failing login button test**

Add to `login-button.test.ts`:

```ts
it('renders WeCom login with local icon path', () => {
  vi.mocked(useAuthMethods).mockReturnValue({
    data: [{
      id: 'oauth-wechatwork',
      methodType: 'OAUTH_REDIRECT',
      provider: 'wechatwork',
      displayName: 'WeCom',
      actionUrl: '/api/v1/auth/wechatwork/authorize?returnTo=%2Fdashboard',
    }],
    isLoading: false,
  } as never)

  const html = renderToStaticMarkup(<LoginButton returnTo="/dashboard" />)

  expect(html).toContain('/wechatwork-logo.svg')
  expect(html).toContain('loginButton.loginWith')
})
```

- [ ] **Step 2: Write failing enterprise login page test**

Update `login.test.tsx` to let `useAuthMethods` return WeCom for one test:

```ts
it('renders enterprise SSO only when WeCom is the only auth method', () => {
  vi.mocked(useAuthMethods).mockReturnValue({
    data: [{
      id: 'oauth-wechatwork',
      methodType: 'OAUTH_REDIRECT',
      provider: 'wechatwork',
      displayName: 'WeCom',
      actionUrl: '/api/v1/auth/wechatwork/authorize?returnTo=%2Fdashboard',
    }],
  } as never)

  const html = renderToStaticMarkup(<LoginPage />)

  expect(html).toContain('login.enterpriseWechatTitle')
  expect(html).not.toContain('login.tabPassword')
  expect(html).not.toContain('login.register')
  expect(html).toContain('FR24 SkillHub')
})
```

- [ ] **Step 3: Run frontend login tests and verify they fail**

Run:

```bash
cd web && pnpm vitest run src/features/auth/login-button.test.ts src/pages/login.test.tsx
```

Expected: FAIL because enterprise WeCom-only rendering is not implemented.

- [ ] **Step 4: Add WeCom logo asset**

Create `web/public/wechatwork-logo.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" role="img" aria-label="WeCom">
  <rect width="48" height="48" rx="10" fill="#1677ff"/>
  <path fill="#fff" d="M13 16.5c0-3.04 3.1-5.5 6.92-5.5 3.45 0 6.32 2 6.84 4.62 4.6.48 8.24 3.7 8.24 7.63 0 4.25-4.25 7.75-9.5 7.75-.82 0-1.62-.08-2.38-.25l-4.37 2.3.8-3.48C16.63 28.55 14 26.04 14 23.25c0-.44.06-.86.18-1.27C13.43 21 13 19.83 13 16.5Zm6.92-3C17.48 13.5 15.5 14.84 15.5 16.5c0 1.64 1.98 3 4.42 3s4.41-1.36 4.41-3c0-1.66-1.97-3-4.41-3Zm6.58 14.75c3.85 0 7-2.24 7-5s-3.15-5-7-5c-.28 0-.56.01-.83.04-1.05 2.18-3.84 3.71-7.08 3.71-.68 0-1.34-.07-1.97-.2-.08.47-.12.95-.12 1.45 0 2.76 3.14 5 7 5 .68 0 1.34-.07 1.96-.21l.46-.1 1.6.85-.3-1.32.77-.34c.95-.42 1.77-.99 2.4-1.66-.98.5-2.32.78-3.89.78Z"/>
</svg>
```

- [ ] **Step 5: Update LoginButton**

Change `LoginButtonProps` and provider filtering:

```ts
interface LoginButtonProps {
  returnTo?: string
  methods?: AuthMethod[]
}
```

Import type:

```ts
import type { AuthMethod } from '@/api/types'
```

Use supplied methods when present:

```ts
const authMethods = methods ?? data ?? []
const providers = authMethods.filter((method) => method.methodType === 'OAUTH_REDIRECT')
```

Keep `OAuthIcon` as `/${normalizedProvider}-logo.svg`; the new asset makes WeCom stable.

- [ ] **Step 6: Update LoginPage enterprise-only branch**

Import brand:

```ts
import { appBrand } from '@/shared/lib/brand'
```

Add near auth method derivation:

```ts
const oauthMethods = (authMethods ?? []).filter((method) => method.methodType === 'OAUTH_REDIRECT')
const passwordMethods = (authMethods ?? []).filter((method) => method.methodType === 'PASSWORD' || method.methodType === 'DIRECT_PASSWORD')
const isWechatWorkOnly = oauthMethods.length === 1 && oauthMethods[0]?.provider === 'wechatwork' && passwordMethods.length === 0
```

Before the tabbed card body, render:

```tsx
{isWechatWorkOnly ? (
  <div className="space-y-6">
    <div className="text-center">
      <h2 className="text-lg font-semibold text-foreground">{t('login.enterpriseWechatTitle')}</h2>
      <p className="mt-2 text-sm text-muted-foreground">
        {t('login.enterpriseWechatHint', { brand: appBrand.displayName })}
      </p>
    </div>
    <LoginButton returnTo={returnTo} methods={oauthMethods} />
  </div>
) : (
  <Tabs defaultValue="password" className="space-y-6">
    ...
  </Tabs>
)}
```

Update the top logo block to use FR24:

```tsx
<div className="mx-auto flex h-16 w-36 items-center justify-center rounded-xl bg-slate-950 px-3 shadow-glow">
  <img src={appBrand.logoUrl} alt={appBrand.companyShortName} className="max-h-8 w-auto" />
</div>
<h1 className="text-4xl font-bold font-heading text-foreground">{appBrand.displayName}</h1>
```

- [ ] **Step 7: Add login translations**

In `en.json` under `login` add:

```json
"enterpriseWechatTitle": "FR24 employee sign-in",
"enterpriseWechatHint": "Use your WeCom account to access {{brand}}. Only FR24 enterprise members can continue."
```

In `zh.json` under `login` add:

```json
"enterpriseWechatTitle": "FR24 员工登录",
"enterpriseWechatHint": "使用企业微信账号访问 {{brand}}。仅 FR24 企业成员可以继续。"
```

- [ ] **Step 8: Run frontend login tests**

Run:

```bash
cd web && pnpm vitest run src/features/auth/login-button.test.ts src/pages/login.test.tsx
```

Expected: PASS.

- [ ] **Step 9: Commit frontend WeCom login UI**

```bash
git add web/public/wechatwork-logo.svg web/src/features/auth/login-button.tsx web/src/pages/login.tsx \
  web/src/i18n/locales/en.json web/src/i18n/locales/zh.json \
  web/src/features/auth/login-button.test.ts web/src/pages/login.test.tsx
git commit -m "feat(web): add WeCom-only login experience"
```

---

### Task 6: Verification and Deployment Notes

**Files:**
- Modify: `README.md` or deployment docs only if existing deployment docs already mention auth configuration.
- No generated OpenAPI file changes are expected if the existing `AuthMethod` shape remains unchanged.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
make test-backend-app TEST=AuthControllerTest
make test-backend-app TEST=WechatWorkAuthControllerTest
cd server && ./mvnw -pl skillhub-auth test -Dtest=WechatWorkApiClientTest,WechatWorkLoginFlowServiceTest
```

Expected: all commands PASS.

- [ ] **Step 2: Run frontend checks**

Run:

```bash
make typecheck-web
make lint-web
cd web && pnpm vitest run src/app/layout.test.ts src/pages/login.test.tsx src/features/auth/login-button.test.ts
```

Expected: all commands PASS.

- [ ] **Step 3: Run broader backend app test**

Run:

```bash
make test-backend-app
```

Expected: PASS.

- [ ] **Step 4: Confirm generated OpenAPI state**

Run:

```bash
git diff -- web/src/api/generated/schema.d.ts
```

Expected: no diff. If controller annotations cause schema drift, run `make generate-api`, inspect the schema diff, and commit it with the implementation.

- [ ] **Step 5: Add deployment note if docs have an auth section**

If an existing deployment/auth doc already lists environment variables, add this exact block:

```text
FR24 WeCom login:
SKILLHUB_PUBLIC_BASE_URL=https://<fr24-skillhub-host>
SKILLHUB_AUTH_WECHATWORK_ENABLED=true
SKILLHUB_AUTH_WECHATWORK_CORP_ID=<corp id>
SKILLHUB_AUTH_WECHATWORK_AGENT_ID=<agent id>
SKILLHUB_AUTH_WECHATWORK_CORP_SECRET=<corp secret>
SKILLHUB_AUTH_WECHATWORK_CALLBACK_BASE_URL=https://<fr24-skillhub-host>
SKILLHUB_AUTH_METHODS_VISIBLE_PROVIDERS=wechatwork
```

- [ ] **Step 6: Commit verification docs if changed**

If a deployment doc was changed:

```bash
git add <changed-deployment-doc>
git commit -m "docs(auth): document FR24 WeCom deployment config"
```

If no docs were changed, record that in the final implementation summary.

---

## Self-Review Notes

- Spec coverage: FR24 branding is covered in Tasks 4 and 5; WeCom catalog exposure is covered in Task 1; WeCom API identity resolution is covered in Task 2; session-based callback login is covered in Task 3; employee-facing login hiding is covered in Task 5; verification is covered in Task 6.
- Type consistency: provider code is consistently `wechatwork`; auth method id is consistently `oauth-wechatwork`; new env names consistently use `SKILLHUB_AUTH_WECHATWORK_*` and `SKILLHUB_AUTH_METHODS_VISIBLE_PROVIDERS`.
- OpenAPI impact: no schema generation is expected because the implementation reuses existing auth-method fields and browser redirect endpoints. The verification task still checks for generated-schema drift.
