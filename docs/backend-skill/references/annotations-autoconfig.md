# 注解与自动配置速查

## 自定义注解

### @ServiceApi — 内部服务调用保护

**包路径：** `com.smart.common.security.annotation.ServiceApi`

标记 Controller 方法或类为内部服务 API，仅允许微服务间调用。网关 `SmartRequestGlobalFilter` 会剥离外部请求中的 `X-Internal-Call` 头，防止外部伪造。被 `@ServiceApi` 标记的端点由 `ServiceApiEndpointRegistry` 自动扫描，在安全过滤链中放行。

```java
@ServiceApi
@GetMapping("/user/info/{username}")
public ApiResult<UserInfo> info(@PathVariable String username,
                                @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId) {
    // ...
}
```

### @AuditTrace — 审计追踪

**包路径：** `com.smart.common.log.annotation.AuditTrace`

标记 Controller 方法，自动记录操作审计日志（操作人、时间、IP、参数等）。

```java
@AuditTrace("新增用户")
@PostMapping
public ApiResult<Void> save(@Valid @RequestBody UserForm form) { ... }
```

### @Dedup — 幂等/去重控制

**包路径：** `com.smart.common.idempotent.annotation.Dedup`

标记 Controller 方法，在 Redis 中基于用户+请求参数设置去重窗口，防止重复提交。窗口内重复请求返回 409 Conflict。默认 10 秒窗口。

```java
@Dedup(duration = 5, timeUnit = TimeUnit.SECONDS)
@PostMapping
public ApiResult<Void> submit(...) { ... }
```

### @TenantEntity — 租户隔离标记

**包路径：** `com.smart.common.core.annotation.TenantEntity`

标记 MyBatis-Plus Entity 类为租户隔离，`SmartTenantInterceptor` 自动为该表的 SQL 追加 `AND tenant_id = ?`。也可通过 `smart.tenant.tables` 配置。

```java
@Data
@TableName("sys_user")
@TenantEntity
public class SysUser extends AuditableEntity { ... }
```

### @MaskField — 数据脱敏

**包路径：** `com.smart.common.xss.annotation.MaskField`

Jackson 序列化时自动脱敏，支持 `MaskStrategy.MIDDLE`（中间脱敏）、`MaskStrategy.ALL`（全部脱敏）等策略。

```java
@MaskField(strategy = MaskStrategy.MIDDLE)
private String phone;  // 13812345678 → 138****5678
```

---

## 自动配置类

| 模块 | 自动配置类 | 注册方式 | 功能 |
|------|------------|----------|------|
| common-core | `JacksonConfiguration` | AutoConfiguration.imports | Jackson 序列化配置 |
| common-core | `I18nConfiguration` | AutoConfiguration.imports | 国际化消息源 |
| common-core | `WebFilterAutoConfiguration` | AutoConfiguration.imports | 注册 RequestTraceFilter（traceId MDC 注入） |
| common-data | `MybatisPlusConfiguration` | AutoConfiguration.imports | 拦截器链、自动填充 |
| common-data | `RedisConfiguration` | AutoConfiguration.imports | Redis 连接与序列化 |
| common-security | `SmartResourceServerAutoConfiguration` | AutoConfiguration.imports | OAuth2 资源服务器、安全过滤链 |
| common-feign | `RpcAutoConfiguration` | AutoConfiguration.imports | Feign 拦截器自动注册 |
| common-xss | `XssAutoConfiguration` | AutoConfiguration.imports | XSS 过滤器注册 |

注册机制：Spring Boot 3 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

---

## Feign 拦截器

`RpcAutoConfiguration` 自动注册两个拦截器：

| 拦截器 | 职责 |
|--------|------|
| `ServiceCallInterceptor` | 为 Feign 请求注入 `X-Internal-Call: true` |
| `ContextPropagateInterceptor` | 透传 Authorization、X-Tenant-Id、X-User-Id、X-Username、X-Trace-Id |

引入 `smart-common-feign` 依赖后自动生效，无需 `@EnableSmartFeignClients`。

---

## MyBatis-Plus 拦截器链

`MybatisPlusConfiguration` 配置的拦截器顺序：

1. `PaginationInnerInterceptor` — 分页（PostgreSQL 方言，maxLimit=500）
2. `OptimisticLockerInnerInterceptor` — 乐观锁
3. `TenantLineInnerInterceptor` + `SmartTenantInterceptor` — 租户隔离（需 `smart.tenant.enabled=true`）
4. `DataPermissionInterceptor` — 数据权限（依赖 DataPermissionResolver Bean）

---

## 网关过滤器

`SmartRequestGlobalFilter`（order=10）：
1. 剥离外部请求的 `X-Internal-Call` 头（安全边界）
2. 生成/传播 traceId
3. 记录请求延迟

安全模型：网关剥离 `X-Internal-Call` → `ServiceCallInterceptor` Feign 出站注入 → `ServiceApiEndpointRegistry` 校验 → 无该头的外部请求被拒绝。
