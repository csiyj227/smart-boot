# 安全与内部服务调用

## OAuth2 认证架构

```
客户端 → POST /oauth2/token (grant_type=password)
    → smart-gateway 转发到 smart-auth
    → smart-auth 调用 smart-system 获取用户信息（Feign + @ServiceApi）
    → 验证密码、生成 JWT Token（RSA 签名）
    → 返回 access_token + refresh_token
```

**Token 类型：** JWT（RSA 签名），无状态验证。

**关键配置类：** `SmartResourceServerAutoConfiguration`（`com.smart.common.security.component`）

**安全过滤链配置：**
- 无状态会话（`SessionCreationPolicy.STATELESS`）
- 禁用 CSRF、启用 CORS
- JWT Token 验证（从 `authorities` claim 提取权限）
- 白名单放行路径：
  - OAuth2 端点：`/oauth2/**`
  - 验证码：`/captcha/**`
  - 社交登录：`/social/providers`、`/social/authorize/**`
  - Actuator/Swagger：`/actuator/**`、`/v3/api-docs/**`、`/swagger-ui/**`、`/doc.html`
  - 文件下载/预览：`/file/download/**`、`/file/preview/**`
  - 租户列表：`/tenant/list`
  - `@ServiceApi` 端点（自动扫描注册）

## 权限体系

| 级别 | 机制 | 说明 |
|------|------|------|
| 路由级 | 动态路由（前端） | 根据菜单树生成用户可见路由 |
| 元素级 | v-permission 指令（前端） | 按钮/操作按权限标识显示/隐藏 |
| 方法级 | `@PreAuthorize`（后端） | Spring Security 方法鉴权 |
| 接口级 | `@ServiceApi`（后端） | 服务间内部调用保护 |
| 数据级 | `DataPermissionInterceptor`（后端） | MyBatis-Plus 行级权限过滤 |
| 租户级 | `SmartTenantInterceptor`（后端） | 自动拼接 `tenant_id` 条件 |

## @ServiceApi 内部调用机制

### 工作流程

```
smart-auth（调用方）                     smart-system（被调用方）
    │                                        │
    ├─ Feign 调用                             │
    │  ServiceCallInterceptor                │
    │  注入 X-Internal-Call: true ──────────→ ServiceApiEndpointRegistry
    │                                        │ 校验 X-Internal-Call 头存在
    │  ContextPropagateInterceptor           │ → 放行（不要求 JWT）
    │  透传 X-Tenant-Id, X-User-Id, ──────→ │
    │  X-Username, X-Trace-Id               │
    │                                        │
```

### 安全边界

1. **网关侧：** `SmartRequestGlobalFilter`（order=10）**剥离所有外部请求的 `X-Internal-Call` 头**，防止外部伪造
2. **服务侧：** `ServiceApiEndpointRegistry` 扫描所有 `@ServiceApi` 标记的端点，在安全过滤链中放行这些端点（仅限带有 `X-Internal-Call` 头的请求）
3. **Feign 侧：** `ServiceCallInterceptor` 在每个 Feign 出站请求上自动注入 `X-Internal-Call: true`

### Controller 端点类型对照

| 端点类型 | 注解 | 认证要求 | 用途 |
|----------|------|----------|------|
| 外部 API | `@PreAuthorize` | 需要 JWT Token | 前端/外部调用 |
| 内部 API | `@ServiceApi` | 需要 `X-Internal-Call` 头 | 微服务间调用 |
| 匿名 API | 无注解（在白名单中） | 不需要认证 | 验证码、文件下载等 |

### Feign 接口编写规范

```java
@FeignClient(contextId = "remoteUserService", value = "smart-system")
public interface RemoteUserService {

    @GetMapping("/user/info/{username}")
    ApiResult<UserInfo> info(@PathVariable("username") String username,
                     @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId,
                     @RequestHeader(AuthHeaders.SERVICE_CALL) String from);
}
```

**必须：**
- `contextId` 唯一标识
- `value` = 目标服务名
- 传递 `AuthHeaders.SERVICE_CALL` 头
- 多租户场景传递 `AuthHeaders.TENANT_ID` 头

**自动行为：**
- `ServiceCallInterceptor` 自动为 Feign 请求注入 `X-Internal-Call: true`
- `ContextPropagateInterceptor` 自动透传 Authorization、X-Tenant-Id、X-User-Id、X-Username、X-Trace-Id

## 请求头约定

| 头名称 | 常量 | 方向 | 说明 |
|--------|------|------|------|
| `X-Internal-Call` | `AuthHeaders.SERVICE_CALL` | Feign 出站 | 标记为内部调用（值固定为 `true`） |
| `X-Tenant-Id` | `AuthHeaders.TENANT_ID` | 全链路 | 当前租户 ID |
| `X-User-Id` | `AuthHeaders.USER_ID` | 全链路 | 当前用户 ID |
| `X-Username` | `AuthHeaders.USERNAME` | 全链路 | 当前用户名 |
| `X-Client-Id` | `AuthHeaders.CLIENT_ID` | 全链路 | OAuth2 客户端 ID |
| `X-Trace-Id` | `AuthHeaders.TRACE_ID` | 全链路 | 分布式追踪 ID |
| `Authorization` | `AuthHeaders.BEARER_PREFIX` | 全链路 | Bearer Token |

常量定义在 `com.smart.common.core.auth.AuthHeaders`。

## 密码安全

- 使用 `BCryptPasswordEncoder`（由 `SmartResourceServerAutoConfiguration` 自动注册）
- 密码字段使用 `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` 防止序列化泄露
- 密码重试锁定的 Redis Key：`CacheKeyRegistry.PWD_RETRY.key(username)`，5 次失败后锁定 10 分钟
