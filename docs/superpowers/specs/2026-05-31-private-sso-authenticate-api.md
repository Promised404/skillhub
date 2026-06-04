# Private SSO /api/sso/authenticate.do 接口对接文档

## 背景

SkillHub 私有化部署需要与企业 SSO 系统对接，实现统一登录。SkillHub 后端作为客户端，调用 SSO 的 REST 接口完成用户认证。

当前 SSO 系统的登录逻辑分布于：
- `SSOController.login()` — 处理 HTTP 登录请求，包含 SM2 解密、timestamp 校验、连续失败锁定
- `UserFacade.validateUser()` — Dubbo 接口，执行实际的密码验证 / 短信验证码校验

**本接口目标**：将上述逻辑封装为 REST 端点，供 SkillHub 后端调用。

## 接口定义

### POST /api/sso/authenticate.do

#### 请求

```
Content-Type: application/json
```

```json
{
  "username": "zhangsan",
  "password": "<SM2-encrypted>",
  "twoFactorCode": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `username` | string | 是 | 用户名 |
| `password` | string | 是 | SM2 加密后的密码（格式见下方） |
| `twoFactorCode` | string | 否 | 二次验证码。SkillHub 前端根据部署配置决定是否展示此字段，不传或为 null 时跳过 2FA 校验 |

**SM2 密码加密格式**：

前端加密明文为 `password-timestamp`，其中 `timestamp` 为毫秒级 Unix 时间戳：

```
明文 = "mypassword-1748736000000"
密文 = SM2.doEncrypt(明文, publicKey)
传输密文 = "04" + 密文hex
```

`04` 前缀为 SM2 非压缩点格式标识，Java 端 BouncyCastle 解密时需要此前缀。

**现有代码对照**：当前 `SSOController.login()` 已有相同的 SM2 解密逻辑（第 190-210 行），可直接复用：
- `Sm2Util.decrypt(passwd, privateKey)` 解密
- 以 `-` 分割，取第二段为 timestamp
- 校验 `|currentTime - timestamp| ≤ DECRYPT_TIME_THRESHOLD`（5 分钟）

#### 成功响应 200

```json
{
  "uid": "U10042",
  "username": "zhangsan",
  "displayName": "张三",
  "email": "zhangsan@company.com",
  "avatarUrl": "https://avatar.company.com/u10042.png"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uid` | string | 是 | SSO 侧用户唯一标识，SkillHub 用此值作为 `identity_binding.subject` |
| `username` | string | 是 | 登录名，用于展示 |
| `displayName` | string | 是 | 用户显示名 |
| `email` | string | 否 | 邮箱，为 null 时 SkillHub 不设置邮箱字段 |
| `avatarUrl` | string | 否 | 头像 URL，为 null 时 SkillHub 不设置头像 |

**SkillHub 使用方式**：收到 200 响应后，SkillHub 以 `provider_code='private-sso'` + `subject=uid` 调用 `IdentityBindingService.bindOrCreate()`，首次登录自动创建本地用户并绑定身份，后续登录同步 displayName / email / avatarUrl。

#### 错误响应

所有错误响应均为 JSON 格式，包含 `code` 和 `message` 字段：

```json
{
  "code": "ERROR_CODE",
  "message": "错误描述"
}
```

| HTTP 状态码 | code | 说明 | SkillHub 侧处理 |
|------------|------|------|-----------------|
| **401** | `INVALID_CREDENTIALS` | 用户名或密码错误 | 显示"用户名或密码错误" |
| **401** | `INVALID_2FA_CODE` | 二次验证码错误 | 显示"验证码错误，请重新输入" |
| **403** | `ACCOUNT_DISABLED` | 账号已被禁用 | 同步本地用户状态为 DISABLED，显示"该账号已被禁用" |
| **403** | `ACCOUNT_LOCKED` | 连续失败次数过多，账号临时锁定 | SkillHub 侧已有独立的失败计数限制，此错误映射为"认证服务暂时不可用" |
| **400** | `INVALID_REQUEST` | 请求参数不合法 | SkillHub 映射为"认证服务暂时不可用" |
| **其他** | — | 服务端异常 | SkillHub 映射为"认证服务暂时不可用" |

**SkillHub 错误映射代码**（供参考，无需 SSO 侧实现）：

```java
// SkillHub PrivateSsoClient 中的映射逻辑
401 + body.contains("INVALID_2FA_CODE") → AuthFlowException(401, "error.auth.invalid2faCode")
401 (其他)                                → AuthFlowException(401, "error.auth.invalidCredentials")
403                                       → AuthFlowException(403, "error.auth.accountDisabled")
其他                                      → AuthFlowException(502, "error.auth.ssoUnavailable")
```

## 实现建议

基于现有代码，建议在 SSO 项目中新增一个 REST Controller，复用已有的 `UserFacade` 和 SM2 解密逻辑：

### 1. 新增 Controller

```java
@RestController
@RequestMapping("/api/sso")
public class SsoAuthenticateController {

    @Reference  // Dubbo 注入
    private UserFacade userFacade;

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody AuthenticateRequest request) {
        // 1. SM2 解密 + timestamp 校验（复用 SSOController 中已有逻辑）
        // 2. 构造 UserInfoDTO，调用 userFacade.validateUser()
        // 3. 根据返回码映射为上述响应格式
    }
}
```

### 2. 复用已有逻辑

| 功能 | 现有代码位置 | 说明 |
|------|-------------|------|
| SM2 解密 | `SSOController.login()` 第 190-210 行 | 直接提取为工具方法 |
| timestamp 防重放 | 同上，`DECRYPT_TIME_THRESHOLD` | 5 分钟阈值，已有常量 |
| 密码验证 | `UserFacadeImpl.validateUser()` | 已完整实现 |
| 短信验证码验证 | `UserFacadeImpl.validateUser()` 前 50 行 | 通过 `verificationCode` 字段触发 |
| 连续失败锁定 | `SSOController.login()` 第 215-239 行 | 建议保留此逻辑 |
| Okta 用户拦截 | `UserFacadeImpl.validateUser()` 第 153-158 行 | 已有，无需额外处理 |

### 3. 2FA 与短信验证码的关系

SkillHub 的 `twoFactorCode` 字段对应 SSO 现有的短信验证码登录流程：

- 当 `twoFactorCode` 不为 null 时，将其值设置到 `UserInfoDTO.verificationCode`，并设置 `registerPhoneNumber`
- 当 `twoFactorCode` 为 null 时，走标准密码验证流程

> **注意**：当前 SkillHub 前端在 2FA 启用时，用户在同一表单中填写用户名+密码+验证码一并提交。SSO 侧需确保 `username` + `password` + `verificationCode` 同时传入时能正确处理（先验证密码正确性，再校验验证码）。

### 4. uid 字段映射

SkillHub 使用 `uid` 作为用户唯一标识。SSO 侧 `UserInfo` 表中可选字段：

| SSO 字段 | 说明 | 建议 |
|---------|------|------|
| `id` | 数据库自增主键 | 不建议直接使用，可能跨环境不唯一 |
| `userNo` | 用户编号 | 如有此字段，优先使用 |
| `username` | 登录名 | 可用作 uid，但需确保不可变更 |

请确认 SSO 侧使用哪个字段作为跨系统唯一标识，SkillHub 将以此值绑定 `identity_binding.subject`。

## 超时与网络约定

| 项目 | 值 | 说明 |
|------|---|------|
| SkillHub → SSO 连接超时 | 5 秒 | 可通过 `skillhub.auth.private-sso.connect-timeout` 调整 |
| SkillHub → SSO 读取超时 | 10 秒 | 可通过 `skillhub.auth.private-sso.read-timeout` 调整 |
| SM2 timestamp 有效期 | 5 分钟 | 与 SSO 现有 `DECRYPT_TIME_THRESHOLD` 一致 |
| SkillHub 重试策略 | 不重试 | 认证调用失败直接返回错误，不进行重试 |

## 测试验证

SSO 团队完成接口开发后，可使用以下命令验证：

### 成功场景

```bash
curl -X POST https://sso.company.com/api/sso/authenticate.do \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "<SM2-encrypted-password-timestamp>"
  }'
```

预期响应：

```json
{
  "uid": "U10042",
  "username": "testuser",
  "displayName": "测试用户",
  "email": "testuser@company.com",
  "avatarUrl": null
}
```

### 密码错误

```bash
curl -X POST https://sso.company.com/api/sso/authenticate.do \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "wrong"}'
```

预期响应：

```
HTTP/1.1 401 Unauthorized
```

```json
{"code": "INVALID_CREDENTIALS", "message": "用户名或密码错误"}
```

### 账号禁用

```json
{"code": "ACCOUNT_DISABLED", "message": "账号已被禁用"}
```

```
HTTP/1.1 403 Forbidden
```

## 联调清单

- [ ] SSO 侧提供 `/api/sso/authenticate.do` 端点
- [ ] 确认 `uid` 字段使用 SSO 侧哪个字段映射
- [ ] 确认 SM2 公钥（SkillHub 前后端配置同一公钥，SSO 侧持有对应私钥）
- [ ] 确认 2FA 流程：是否复用短信验证码，还是使用其他 2FA 方案（如 TOTP）
- [ ] 提供 SSO 测试环境地址及测试账号
- [ ] 确认网络连通性：SkillHub 后端 → SSO 服务（跨域部署，需防火墙放通）
