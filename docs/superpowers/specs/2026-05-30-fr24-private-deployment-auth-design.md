# FR24 Private Deployment Auth Design

## Context

FlightRoutes24 (FR24) wants to deploy SkillHub as a private internal registry. Employees should use
their existing WeCom (Enterprise WeChat) identity instead of registering separate SkillHub accounts.
The public employee-facing entry should carry FR24 branding and expose WeCom login as the primary
authentication path.

The current codebase already has:

- A dynamic authentication-method catalog exposed by `/api/v1/auth/methods`.
- Browser session authentication backed by Spring Security sessions.
- OAuth identity binding through `IdentityBindingService`.
- Local password login, GitHub OAuth, GitLab OAuth, mock auth, and optional direct/session-bootstrap
  flows.
- Frontend login UI that renders methods returned by the auth-method catalog.

WeCom PC browser scan login is not a standard Spring OAuth2 token exchange. It uses a
`qrConnect` authorization URL, returns a `code`, then requires SkillHub to call WeCom server APIs
with the enterprise application access token to resolve the employee `UserId`.

## Goals

- Brand the private SkillHub deployment as an FR24 internal registry.
- Use the FR24 logo in the global shell, login page, landing page, and footer.
- Let employees log in from a PC browser by scanning or confirming through WeCom.
- Allow only FR24 WeCom enterprise members to sign in through the employee entry.
- Reuse SkillHub sessions, user accounts, identity bindings, RBAC, and `/api/v1/auth/me`.
- Hide local registration/password login and GitHub/GitLab login from the normal employee login page.
- Keep operational fallback auth possible through backend configuration and bootstrap admin support.

## Non-Goals

- No WeCom client-internal silent login in this phase.
- No embedded QR-code widget in the SkillHub login page in this phase.
- No SCIM or full WeCom contact synchronization.
- No automatic namespace/team mapping from WeCom departments.
- No migration of existing local/GitHub/GitLab accounts.
- No manual editing of generated OpenAPI files unless implementation changes the OpenAPI contract.

## User Experience

### Employee Login

The login page should present FR24 branding and a single primary action:

- "Sign in with WeCom" / "使用企业微信登录"

Clicking the button redirects the browser to the WeCom `qrConnect` authorization URL. After the
employee scans or confirms in WeCom, WeCom redirects back to SkillHub. SkillHub completes the server
side login and redirects the employee to the original `returnTo` path or `/dashboard`.

### Hidden Employee-Unsafe Paths

For FR24 deployments, the normal login page should not show:

- password login tab
- registration link
- forgot-password link
- GitHub/GitLab OAuth buttons

Local auth endpoints may remain available behind configuration for bootstrap or emergency admin
use, but they should not be advertised to employees.

### Branding

The private deployment should display the deployment as `FR24 SkillHub` while preserving SkillHub as
the product name. FR24 should be visible in the first viewport and in global navigation.

Use the provided logo URL:

`https://www.flightroutes24.com/images/logo-white.eea24fa8.svg`

Because that logo is white, UI placements should use a dark or high-contrast background chip when
needed. Avoid relying on the remote logo for critical layout sizing; set stable dimensions around
the image.

## Backend Design

### Configuration

Add a WeCom auth properties class in `skillhub-auth` or `skillhub-app` depending on the final
controller/service split:

- `skillhub.auth.wechatwork.enabled`
- `skillhub.auth.wechatwork.corp-id`
- `skillhub.auth.wechatwork.agent-id`
- `skillhub.auth.wechatwork.corp-secret`
- `skillhub.auth.wechatwork.callback-base-url`
- `skillhub.auth.wechatwork.display-name` defaulting to `WeCom`

Secrets must come from environment variables in deployment configuration. Do not commit real FR24
WeCom credentials.

### Authentication Method Catalog

Extend `AuthMethodCatalog` to include WeCom when enabled:

```text
id: wechatwork
methodType: OAUTH_REDIRECT
provider: wechatwork
displayName: WeCom
actionUrl: /api/v1/auth/wechatwork/authorize?returnTo=...
```

For FR24 private deployment mode, the catalog should be configurable so employee UI receives only
the WeCom method. This can be done with an auth-method visibility allowlist rather than hard-coding
FR24 behavior into the generic catalog.

### WeCom Login Endpoints

Add session-authenticated browser flow endpoints:

```text
GET /api/v1/auth/wechatwork/authorize
GET /api/v1/auth/wechatwork/callback
```

`authorize` responsibilities:

- sanitize `returnTo` using the same rules as current OAuth redirects
- generate a random `state`
- store `state` and `returnTo` in the HTTP session
- build the WeCom `qrConnect` URL
- redirect to WeCom

`callback` responsibilities:

- validate `state` against the session
- reject missing or reused state
- reject missing `code`
- resolve WeCom employee identity
- bind or create the SkillHub user
- create the standard SkillHub session authentication
- redirect to the stored `returnTo` or `/dashboard`

### WeCom API Client

Add a small WeCom API client service:

1. Call WeCom `gettoken` with `corp-id` and `corp-secret`.
2. Cache the access token until shortly before expiry.
3. Call WeCom `getuserinfo` with the access token and callback `code`.
4. Require a `UserId` in the response.
5. Treat `OpenId` without `UserId` as non-employee and deny access.

The normalized SkillHub claims should be:

- `provider`: `wechatwork`
- `subject`: WeCom `UserId`
- `providerLogin`: WeCom `UserId` unless profile enrichment is later added
- `email`: absent unless a later profile-detail API is added and approved
- `extra`: raw minimal WeCom identity response without secrets

### Account Binding

Reuse `IdentityBindingService.bindOrCreate(...)` where possible. If direct construction of
`OAuthClaims` is sufficient, avoid creating a separate identity path.

New WeCom-created users should:

- receive the default `USER` role through existing role defaulting
- become members of the global namespace through existing active-user creation behavior
- have `oauthProvider` reported as `wechatwork`

### Security

- Use high-entropy state and session-bound validation for CSRF protection.
- Remove the state from the session after use.
- Sanitize all post-login redirects.
- Do not log access tokens, corp secrets, callback codes, or raw sensitive payloads.
- Cache WeCom access tokens in memory only unless the deployment later needs distributed token cache.
- Add clear denial behavior for non-enterprise users and WeCom API failures.

## Frontend Design

### Shared Branding

Introduce a small shared brand component or constants at the lowest appropriate layer:

- display name: `FR24 SkillHub`
- logo URL: the FR24 white SVG URL
- optional homepage URL: `https://www.flightroutes24.com/`

Use it in:

- app header
- footer
- login page brand mark
- landing hero

Keep text translatable through i18n keys.

### Login Page

The login page should render a single-method enterprise SSO state when the auth-method catalog only
returns WeCom. It should avoid tabs when there is only one visible method.

Behavior:

- show FR24 logo and `FR24 SkillHub`
- show concise employee SSO copy
- render one WeCom login button
- preserve `returnTo`
- preserve disabled-account and access-denied messaging

Existing multi-method login UI can remain for non-FR24 deployments.

### OAuth Button Icons

Add a `wechatwork` icon asset or icon mapping so `LoginButton` does not request a missing
`/wechatwork-logo.svg`. Prefer a checked-in asset under `web/public` or a lucide fallback icon if no
official reusable asset is available.

### Landing and Shell

Update the public-facing first viewport so FR24 is visible without turning the app into a marketing
page. The existing landing page can keep live skill search and registry content, but should replace
the top brand signal from plain `SkillHub` to `FR24 SkillHub`.

## Deployment Configuration

FR24 production-like configuration should include:

```text
SKILLHUB_PUBLIC_BASE_URL=https://<fr24-skillhub-host>
SKILLHUB_AUTH_WECHATWORK_ENABLED=true
SKILLHUB_AUTH_WECHATWORK_CORP_ID=<fr24 corp id>
SKILLHUB_AUTH_WECHATWORK_AGENT_ID=<wecom app agent id>
SKILLHUB_AUTH_WECHATWORK_CORP_SECRET=<wecom app secret>
SKILLHUB_AUTH_WECHATWORK_CALLBACK_BASE_URL=https://<fr24-skillhub-host>
SKILLHUB_AUTH_METHODS_VISIBLE_PROVIDERS=wechatwork
BOOTSTRAP_ADMIN_ENABLED=true
```

The WeCom admin console must configure the SkillHub public host as an allowed callback/trusted
domain for the selected WeCom application. The redirect URI used by SkillHub must be under that
domain.

## API and OpenAPI Impact

The initial implementation can avoid generated frontend type changes by using the existing
auth-method response shape and browser redirects. If new response DTOs or documented API contracts
are introduced, run `make generate-api` and commit `web/src/api/generated/schema.d.ts`.

## Error Handling

User-facing outcomes:

- invalid state or missing code: redirect to login with an authentication error
- non-FR24/non-enterprise WeCom user: redirect to access denied
- disabled SkillHub account: redirect to existing disabled-account path
- WeCom API unavailable: redirect to login with a retryable SSO error

Backend logs should include correlation-friendly context such as provider and sanitized error code,
but never secrets or tokens.

## Testing Strategy

### Backend Unit Tests

Add tests for:

- `authorize` stores state and redirects to the expected WeCom URL
- `callback` rejects missing state
- `callback` rejects state mismatch
- `callback` rejects missing code
- successful callback binds or creates a `wechatwork` user
- non-enterprise response without `UserId` is denied
- auth-method catalog exposes WeCom when enabled
- auth-method catalog can hide local/GitHub/GitLab in FR24 mode

### Frontend Tests

Add tests for:

- login page renders single enterprise SSO UI when only WeCom is returned
- login button redirects to WeCom authorize action URL
- password/register UI is absent in single-method enterprise mode
- header/footer show FR24 SkillHub branding
- existing multi-method login still works when multiple methods are returned

### Manual Verification

Before PR:

- `make test-backend-app`
- `make typecheck-web`
- `make lint-web`

If the callback flow is tested against a real FR24 WeCom application, verify:

- allowed employee can log in
- non-employee or out-of-enterprise identity is denied
- `returnTo` is preserved for an authenticated employee
- `/api/v1/auth/me` reports `oauthProvider=wechatwork`

## References

- WeCom scan login `qrConnect` parameters: `appid`, `agentid`, `redirect_uri`, `state`
- WeCom identity resolution: callback `code` plus application access token returns enterprise
  member `UserId`
