# Private SSO Direct Auth Integration Design

## Overview

SkillHub private deployment needs to support enterprise SSO login so that users authenticated by the company's identity system can access SkillHub without separate registration. This uses the existing `DirectAuthProvider` SPI with a `PrivateSsoDirectAuthProvider` implementation that validates credentials against the company SSO via REST API.

**Deployment scenario**: SkillHub and SSO are on different domains (no shared cookies).

## SSO REST API Specification

SkillHub requires the company SSO to expose one primary endpoint. The interface is designed by translating the ref-project `UserFacade.validateUser()` + `SSOController.login()` logic into a REST equivalent.

### POST /api/sso/authenticate

```
Content-Type: application/json
```

**Request (2FA disabled):**

```json
{
  "username": "zhangsan",
  "password": "<SM2-encrypted>"
}
```

**Request (2FA enabled):**

```json
{
  "username": "zhangsan",
  "password": "<SM2-encrypted>",
  "twoFactorCode": "123456"
}
```

**Response 200 (success):**

```json
{
  "uid": "U10042",
  "username": "zhangsan",
  "displayName": "张三",
  "email": "zhangsan@company.com",
  "avatarUrl": "https://..."
}
```

**Response 401 (credential error):**

```json
{ "code": "INVALID_CREDENTIALS", "message": "用户名或密码错误" }
```

**Response 401 (2FA code error):**

```json
{ "code": "INVALID_2FA_CODE", "message": "验证码错误" }
```

**Response 403 (account disabled):**

```json
{ "code": "ACCOUNT_DISABLED", "message": "账号已被禁用" }
```

### Optional: GET /api/sso/users/{uid}

For future profile sync. Returns the same user info shape as authenticate.

### Password encryption

Frontend encrypts password with SM2 public key before submission. Encrypted plaintext format: `password-timestamp`. SSO side decrypts with private key and validates timestamp within 5 minutes to prevent replay attacks. SM2 public key is injected via configuration into both frontend and backend.

### 2FA flow

2FA is a single-step form: when enabled, the login form shows username + password + verification code fields side by side. Whether 2FA is required is determined by SkillHub frontend configuration (`SKILLHUB_WEB_AUTH_PRIVATE_SSO_TWO_FACTOR_ENABLED`), not by SSO response negotiation.

## Backend Architecture

### New files (skillhub-auth module)

```
com.iflytek.skillhub.auth.privatesso/
├── PrivateSsoDirectAuthProvider.java    // implements DirectAuthProvider
├── PrivateSsoClient.java               // REST client for SSO /api/sso/authenticate
├── PrivateSsoProperties.java           // @ConfigurationProperties
└── PrivateSsoIdentityService.java      // delegates to IdentityBindingService
```

### Core flow

User submits username + password (+ optional 2FA code) to `POST /api/v1/auth/direct/login`:

1. `PrivateSsoDirectAuthProvider.authenticate(DirectAuthRequest)` is called
2. `PrivateSsoClient` calls `SSO POST /api/sso/authenticate`
3. SSO returns 200 with `{uid, username, displayName, email, avatarUrl}`
4. `PrivateSsoIdentityService` calls `IdentityBindingService.bindOrCreate()`:
   - First login: creates `user_account(ACTIVE)` + `identity_binding(provider_code='private-sso', subject=uid)`
   - Return login: loads existing user, syncs displayName/email
5. Returns `PlatformPrincipal`, `AuthController` calls `PlatformSessionService.establishSession()`

### Configuration

```yaml
skillhub:
  auth:
    direct:
      enabled: true
    private-sso:
      base-url: https://sso.company.com
      connect-timeout: 5s
      read-timeout: 10s
      sm2-public-key: <base64>
      two-factor:
        enabled: false
      identity:
        provider-code: private-sso
        initial-status: ACTIVE
```

### Error mapping

| SSO Response | SkillHub Exception |
|-------------|-------------------|
| 401 INVALID_CREDENTIALS | AuthFlowException(401, "error.auth.invalidCredentials") |
| 401 INVALID_2FA_CODE | AuthFlowException(401, "error.auth.invalid2faCode") |
| 403 ACCOUNT_DISABLED | AuthFlowException(403, "error.auth.accountDisabled") |
| Timeout / network error | AuthFlowException(502, "error.auth.ssoUnavailable") |

### Relationship to existing code

- **No changes** to `DirectAuthProvider` interface, `AuthController`, `PlatformSessionService`, `IdentityBindingService`
- **No changes** to existing OAuth or local login flows
- `PrivateSsoDirectAuthProvider` is injected via `@ConditionalOnProperty("skillhub.auth.private-sso.base-url")`
- `providerCode()` returns `"private-sso"`, frontend sets `SKILLHUB_WEB_AUTH_DIRECT_PROVIDER=private-sso`

## Frontend Implementation

### Runtime configuration

```
SKILLHUB_WEB_AUTH_DIRECT_ENABLED=true
SKILLHUB_WEB_AUTH_DIRECT_PROVIDER=private-sso
SKILLHUB_WEB_AUTH_PRIVATE_SSO_TWO_FACTOR_ENABLED=true
SKILLHUB_WEB_AUTH_PRIVATE_SSO_SM2_PUBLIC_KEY=<base64>
```

### Login page changes

1. **Password encryption**: Before submission, encrypt password with SM2 public key using `sm-crypto` library: `sm2.doEncrypt(password + '-' + timestamp, publicKey)`
2. **2FA input field**: When `SKILLHUB_WEB_AUTH_PRIVATE_SSO_TWO_FACTOR_ENABLED=true`, show a verification code input field below the password field; submit `twoFactorCode` alongside username/password
3. **Hide register and forgot-password links**: Enterprise SSO mode does not need local registration or password reset; conditionally hide these links when direct auth is enabled
4. **Tab label**: Change password tab from "密码登录" to "企业账号登录" when direct auth is enabled

### SM2 encryption

Frontend adds `sm-crypto` dependency. Before `mutationFn` submits, it encrypts `password + '-' + Date.now()` with the configured SM2 public key.

### Error handling

Existing `use-password-login.ts` already handles `ApiError`. Add i18n keys:
- `error.auth.invalid2faCode` -> "验证码错误，请重新输入"
- `error.auth.ssoUnavailable` -> "认证服务暂时不可用，请稍后重试"

### No changes

- Session bootstrap logic (not used)
- OAuth tab logic
- `use-auth-methods.ts` and `AuthMethodCatalog` (backend already supports `DIRECT_PASSWORD` method type)

## Security

### Password security

- SM2 encrypted in transit; SkillHub backend never sees plaintext passwords
- Timestamp in encrypted plaintext prevents replay attacks (5-minute window)
- SM2 public key sourced from configuration, not hardcoded in frontend

### Brute-force protection

Reuse existing `AuthFailureThrottleService` in `AuthController.directLogin()`. It already calls `assertAllowed` before auth and `recordFailure` on 401 responses.

### SSO unavailability

- `PrivateSsoClient` enforces connect timeout 5s, read timeout 10s
- Timeout/connection failure maps to `AuthFlowException(502)` with user-friendly message
- No fallback to local login (prevents credential leakage to unintended verification path)

### User status sync

- SSO returns 403 ACCOUNT_DISABLED -> SkillHub marks `user_account.status = DISABLED` -> subsequent logins rejected locally without SSO call
- Admins can also disable users independently in SkillHub admin panel

### Identity uniqueness

- `identity_binding(provider_code='private-sso', subject=uid)` unique constraint ensures one SSO UID maps to one SkillHub account
- No email-based account merging

### Logout

- SkillHub logout only clears its own Spring Session; does not call SSO logout
- Users must log out of SSO separately (different domains, no cross-domain logout)

## Testing

### Unit tests

| Test Class | Coverage |
|-----------|----------|
| `PrivateSsoClientTest` | Normal verification, 401/403 error mapping, timeout/network exception handling |
| `PrivateSsoDirectAuthProviderTest` | authenticate returns correct PlatformPrincipal fields |
| `PrivateSsoIdentityServiceTest` | First login creates user + binding, return login syncs profile, DISABLED user rejected |

Uses MockWebServer to simulate SSO REST responses.

### Integration tests

- Full Spring Context with `skillhub.auth.direct.enabled=true`, verify `POST /api/v1/auth/direct/login` end-to-end
- Verify `IdentityBinding` record persisted correctly
- Verify Spring Session established, `/api/v1/auth/me` returns correct user

### Frontend tests

- 2FA disabled: verification code field hidden, no `twoFactorCode` in submitted data
- 2FA enabled: verification code field visible, `twoFactorCode` included in submission
- SM2 encryption: password encrypted before submission
- Error scenarios: INVALID_CREDENTIALS / INVALID_2FA_CODE / SSO unavailable message display

### Out of scope

- SSO-side password verification logic (SSO team's responsibility)
- SM2 algorithm correctness (standard library, trusted implementation)
