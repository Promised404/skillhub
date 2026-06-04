# Private SSO /api/sso/authenticate.do 接口设计文档

## 概述

为 SkillHub 私有化部署提供 OMS 用户认证 REST 端点。SkillHub 后端调用此接口完成用户名/密码 + Google Authenticator 2FA 认证，获取用户信息后绑定或创建本地用户。

## 决策记录

| 决策项 | 选择 | 原因 |
|--------|------|------|
| URL 路由 | 使用 `*.do` 后缀 (`/api/sso/authenticate.do`) | 与现有 SSO Tomcat 路由约定一致，无需修改 web.xml |
| 响应格式 | HTTP 状态码 + 扁平 JSON | 匹配 spec 定义，标准 REST 实践 |
| uid 映射 | `UserInfoDTO.id` (Long→String) | 不可变、全局唯一，跨系统稳定 |
| 2FA 机制 | Google Authenticator (OMS) | 此端点仅服务 OMS 用户 |
| TGC/Cookie | 不创建 | SkillHub 自行管理会话，SSO 仅负责认证 |
| Controller | 独立 `SsoAuthenticateController` | 与遗留 SSOController 隔离，职责单一 |
| 密码连续失败锁定 | 不使用 LoginManage | 现有 OMS 登录无此机制，保持一致；仅 Google Auth 有独立锁定 |

## 新增文件

| 文件 | 位置 | 说明 |
|------|------|------|
| `SsoAuthenticateController.java` | `fr_f_sso_provider/controller/` | REST 端点 |
| `AuthenticateRequest.java` | `fr_f_sso_provider/dto/` | 请求 DTO |
| `AuthenticateResponse.java` | `fr_f_sso_provider/dto/` | 成功响应 DTO |
| `ErrorResponse.java` | `fr_f_sso_provider/dto/` | 错误响应 DTO |
| `AuthenticateException.java` | `fr_f_sso_provider/dto/` | 认证异常类 |
| `DecryptedPassword.java` | `fr_f_sso_api/util/` | 解密结果 record (与 Sm2Util 同包) |
| `SsoAuthenticateExceptionHandler.java` | `fr_f_sso_provider/controller/` | 全局异常处理 |

## 修改文件

| 文件 | 变更 |
|------|------|
| `web.xml` | DispatcherServlet 新增 `/api/*` url-pattern |
| `Sm2Util.java` | 新增 `decryptAndPassword()` 静态方法，提取 SM2 解密 + 时间戳校验逻辑（异常时抛出 AuthenticateException） |

## Dubbo 依赖

新 Controller 需注入以下已有 Dubbo 服务（均已在 `dubbo-account-ref.xml` 中声明）：

| Bean | 接口 | 用途 |
|------|------|------|
| `userFacade` | `UserFacade` | 密码验证 |
| `distributorFacade` | `DistributorFacade` | 检查 Google Auth 全局开关 |
| `ssoService` | `SSOService` | 登录权限校验 |

## 认证流程

```
请求进入
  │
  ├─ 1. 参数校验（username/password 非空）
  │     └─ 缺失 → 400 INVALID_REQUEST
  │
  ├─ 2. SM2 解密 + timestamp 校验
  │     复用 Sm2Util.decryptAndPassword()
  │     └─ 解密失败或 timestamp 过期（>5min）→ 400 INVALID_REQUEST
  │
  ├─ 3. 密码验证
  │     构建 UserInfoDTO(userType=1/DISTRIBUTOR), 调用 userFacade.validateUser()
  │     └─ 验证失败映射返回码:
  │         806004 → 401 INVALID_CREDENTIALS
  │         806009 → 401 INVALID_CREDENTIALS
  │         806007 → 403 ACCOUNT_DISABLED
  │         806011 → 401 INVALID_CREDENTIALS（密码错误次数达上限）
  │         806012 → 403 ACCOUNT_DISABLED（密码过期）
  │         806013 → 403 ACCOUNT_DISABLED（Okta 用户拦截）
  │         其他非0 → 401 INVALID_CREDENTIALS
  │
  ├─ 4. 登录权限校验
  │     调用 ssoService.authorize(validateUser, domain)
  │     └─ 无权限 → 403 ACCOUNT_DISABLED
  │
  ├─ 5. Google Authenticator 2FA 校验
  │     先检查全局开关 distributorFacade.omsValidGoogleCode():
  │     ├─ 全局未启用 → 跳过 2FA，直接成功
  │     └─ 全局已启用:
  │         ├─ 先检查失败锁定: tryCount >= 6 → 403 ACCOUNT_LOCKED
  │         ├─ twoFactorCode 为 null/空 → 401 INVALID_2FA_CODE
  │         └─ twoFactorCode 非 null → GoogleAuthenticatorUtils.verify() 校验
  │             ├─ 连续 6 次失败 → 5 分钟锁定 → 403 ACCOUNT_LOCKED
  │             └─ 验证码错误 → 401 INVALID_2FA_CODE
  │
  ├─ 6. 成功响应
  │     构建 AuthenticateResponse:
  │     uid = String.valueOf(validateUser.getId())
  │     username = validateUser.getUsername()
  │     displayName = validateUser.getRealName() ?: username
  │     email = validateUser.getRegisterEmail() (nullable)
  │     avatarUrl = null
  │
  └─ 返回 200 + AuthenticateResponse JSON
```

## 请求格式

```
POST /api/sso/authenticate.do
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
| `password` | string | 是 | SM2 加密密码，格式 `04` + 密文hex，明文为 `password-timestamp` |
| `twoFactorCode` | string | 否 | Google Authenticator 验证码。用户启用 2FA 时必传 |

## 成功响应 200

```json
{
  "uid": "12345",
  "username": "zhangsan",
  "displayName": "张三",
  "email": "zhangsan@company.com",
  "avatarUrl": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `uid` | string | 是 | SSO 侧用户唯一标识，SkillHub 用作 identity_binding.subject |
| `username` | string | 是 | 登录名 |
| `displayName` | string | 是 | 显示名，realName 为空时回退到 username |
| `email` | string | 否 | 邮箱，null 时 SkillHub 不设置邮箱 |
| `avatarUrl` | string | 否 | 头像 URL，当前始终为 null |

## 错误响应

所有错误均为 JSON 格式 `{ "code": "...", "message": "..." }`，使用对应 HTTP 状态码。

| HTTP 状态码 | code | message | 说明 |
|------------|------|---------|------|
| 400 | `INVALID_REQUEST` | 请求参数不合法 | 参数缺失 / SM2 解密失败 / timestamp 过期 |
| 401 | `INVALID_CREDENTIALS` | 用户名或密码错误 | 密码验证失败 |
| 401 | `INVALID_2FA_CODE` | 验证码错误 | Google Auth 验证码错误或缺失 |
| 403 | `ACCOUNT_DISABLED` | 账号已被禁用 | 账号禁用 / 密码过期 / Okta 用户 |
| 403 | `ACCOUNT_LOCKED` | 连续登录失败次数过多，账号临时锁定 | Google Auth 连续失败6次 |
| 500 | `INTERNAL_ERROR` | 服务内部错误 | 未预期的异常 |

## SM2 工具方法提取

将 `SSOController.login()` 中的 SM2 解密 + timestamp 校验逻辑 (~20行) 提取为 `Sm2Util.decryptAndPassword()`:

```java
public static DecryptedPassword decryptAndPassword(String encrypted, String privateKey) {
    String decrypted = decrypt(encrypted, privateKey);
    if (StringUtils.isBlank(decrypted)) {
        throw new AuthenticateException(400, "INVALID_REQUEST", "请求参数不合法");
    }
    String[] parts = decrypted.split("-");
    if (parts.length < 2) {
        throw new AuthenticateException(400, "INVALID_REQUEST", "请求参数不合法");
    }
    long timestamp = Long.parseLong(parts[1]);
    if (Math.abs(System.currentTimeMillis() - timestamp) > DECRYPT_TIME_THRESHOLD) {
        throw new AuthenticateException(400, "INVALID_REQUEST", "请求参数不合法");
    }
    return new DecryptedPassword(parts[0]);
}
```

`DecryptedPassword` 为 Java 21 record，放在 `Sm2Util` 同包下。

## Google Authenticator 校验

复用现有 `GoogleCodeManage` + `GoogleAuthenticatorUtils`，逻辑对应 `SSOController.login()` 第 287-308 行:

1. 检查全局开关 `distributorFacade.omsValidGoogleCode()`
2. 全局启用时检查 `twoFactorCode` 是否提供
3. 通过 `GoogleAuthenticatorUtils.verify(validateUser.getGoogleKey(), twoFactorCode)` 校验
4. 失败计数由 `GoogleCodeManage` 管理，连续 6 次触发 5 分钟锁定

## 连续失败锁定

不对 OMS 密码验证引入 LoginManage 锁定，保持与现有 OMS 登录行为一致。仅 Google Auth 有独立的失败锁定机制：

- Google Auth 连续失败由 `GoogleCodeManage` 管理（6 次 → 5 分钟锁定）
- 密码验证失败不做计数和锁定

## URL 路由变更

无需修改 `web.xml`，使用现有 `*.do` url-pattern 即可路由到新的 Controller：

```xml
<servlet-mapping>
    <servlet-name>springmvc</servlet-name>
    <url-pattern>*.do</url-pattern>
</servlet-mapping>
```

## 测试策略

1. **单元测试** `SsoAuthenticateControllerTest`:
   - 参数缺失 → 400
   - SM2 解密失败 → 400
   - timestamp 过期 → 400
   - 密码错误 → 401 INVALID_CREDENTIALS
   - 账号禁用 → 403 ACCOUNT_DISABLED
   - Okta 用户 → 403 ACCOUNT_DISABLED
   - 无登录权限 → 403 ACCOUNT_DISABLED
   - 有 Google Auth 但未传 twoFactorCode → 401 INVALID_2FA_CODE
   - Google Auth 验证码错误 → 401 INVALID_2FA_CODE
   - Google Auth 连续失败锁定 → 403 ACCOUNT_LOCKED
   - 成功 → 200 + 完整用户信息
   - 无 Google Auth 的用户成功 → 200（无需 twoFactorCode）

2. **集成验证**: `curl` 命令按 spec 中测试验证章节执行
