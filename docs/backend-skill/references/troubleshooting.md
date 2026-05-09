# 常见问题排查

## 启动类问题

### 症状：404 Not Found，Bean 注入失败

**原因：** 单体模式（smart-boot）未正确配置包扫描。

**解决：** 检查 `SmartBootApplication` 是否包含以下配置：

1. `@ComponentScan` 枚举所有模块根包
2. `@MapperScan` 枚举所有模块 mapper 包路径
3. `smart-boot/pom.xml` 包含新模块依赖
4. 根 `pom.xml` 的 `boot` profile 包含新模块

详细配置见 `references/dual-mode.md`。

### 症状：Feign 接口调用 404

**微服务模式排查：**
1. 目标服务是否启动并注册到 Nacos
2. `@FeignClient(value = "smart-system")` 中的服务名是否与 Nacos 注册名一致
3. Feign 接口路径是否与 Controller 路径完全一致

**单体模式排查：**
1. 单体模式下不使用 Feign，改为本地 Bean 注入
2. 确认 `@ComponentScan` 包含了 API 模块的包

## 多租户问题

### 症状：查询结果缺少数据

**原因：** SmartTenantInterceptor 自动追加 `AND tenant_id = ?`，如果当前租户 ID 不对，数据会被过滤。

**排查步骤：**
1. 检查请求头 `X-Tenant-Id` 是否正确传递
2. 检查 `TenantContext.get()` 返回值
3. 检查 Entity 是否标注了 `@TenantEntity`（标注了才会追加条件）
4. 开启 MyBatis-Plus SQL 日志查看实际执行的 SQL：
   ```yaml
   mybatis-plus:
     configuration:
       log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
   ```

### 症状：超级管理员看不到其他租户数据

**解决方案：**
```java
TenantContext.runWithoutTenant(() -> {
    // 此处查询不追加 tenant_id 条件
    return userMapper.selectList(null);
});
```

### 症状：异步线程中租户上下文丢失

**原因：** `TenantContext` 使用 `TransmittableThreadLocal`，需要正确包装线程池。

**解决方案：**
```java
// 方式 1：使用 TTL 包装的线程池
ExecutorService executor = TtlExecutors.getTtlExecutorService(rawExecutor);

// 方式 2：手动传递
TenantContext.runAs(tenantId, () -> {
    executor.submit(() -> {
        // 租户 ID 已自动传播
    });
});
```

## 认证与权限问题

### 症状：接口返回 401 Unauthorized

**排查步骤：**
1. 确认请求携带了 `Authorization: Bearer <token>` 头
2. 检查 Token 是否过期（JWT 默认过期时间在 Nacos 配置中）
3. 确认接口不在白名单中（白名单见 `SmartResourceServerAutoConfiguration`）
4. 检查 `@ServiceApi` 标记的端点是否正确被 `ServiceApiEndpointRegistry` 扫描

### 症状：接口返回 403 Forbidden

**排查步骤：**
1. 检查 `@PreAuthorize` 注解的权限标识是否与数据库中配置的一致
2. 检查用户是否拥有对应角色/权限
3. 确认 JWT Token 的 `authorities` claim 中包含所需权限

### 症状：@ServiceApi 端点被外部请求访问

**原因：** 网关未正确剥离 `X-Internal-Call` 头。

**排查步骤：**
1. 确认 `SmartRequestGlobalFilter` 在网关过滤器链中（order=10）
2. 直接访问服务端口绕过网关时，该头不会被剥离——这是预期行为（服务间内部调用）
3. 通过网关访问时，检查请求是否经过了 `SmartRequestGlobalFilter`

## Feign 调用问题

### 症状：Feign 调用返回 401

**原因：** `ContextPropagateInterceptor` 未正确传播 Authorization 头。

**排查步骤：**
1. 确认调用了 `AuthHeaders.SERVICE_CALL` 头（由 `ServiceCallInterceptor` 自动注入）
2. 确认 `RpcAutoConfiguration` 已生效（检查 `AutoConfiguration.imports` 注册）
3. 在异步线程中调用 Feign 时，RequestContextHolder 可能为空——使用 `TenantContext.runAs()` 传播上下文

### 症状：Feign 调用超时

**排查步骤：**
1. 检查目标服务健康状态
2. 调整 Feign 超时配置：
   ```yaml
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connect-timeout: 5000
               read-timeout: 30000
   ```

## 数据库问题

### 症状：SQL 语法错误

**原因：** Smart 仅支持 PostgreSQL，不兼容 MySQL 语法。

**常见差异：**
| MySQL | PostgreSQL |
|-------|-----------|
| `LIMIT x, y` | `LIMIT y OFFSET x` |
| `AUTO_INCREMENT` | `SERIAL` 或 `BIGSERIAL` |
| `TIMESTAMPTZ` | `TIMESTAMP`（禁止使用 TIMESTAMPTZ） |
| `IFNULL()` | `COALESCE()` |
| `` 反引号 | `""` 双引号或不加 |
| `GROUP_CONCAT` | `STRING_AGG` |

### 症状：乐观锁不生效

**排查步骤：**
1. Entity 中取消 `@Version` 字段注释（当前 `AuditableEntity` 中 `@Version` 已注释）
2. MyBatis-Plus 拦截器链中 `OptimisticLockerInnerInterceptor` 已配置
3. 更新时必须传入 version 值

## 缓存问题

### 症状：缓存数据不一致

**排查步骤：**
1. 使用 `CacheKeyRegistry` 枚举查看缓存 Key 前缀
2. 检查 Redis 中的 TTL 是否正确
3. 更新数据后是否清除了对应缓存

**常用缓存 Key（CacheKeyRegistry）：**
| 枚举值 | Key 前缀 | 默认 TTL |
|--------|----------|----------|
| `GATEWAY_ROUTES` | `smart:gw:routes` | 永不过期 |
| `OAUTH2_AUTH` | `smart:oauth2:auth` | 永不过期 |
| `CAPTCHA` | `smart:captcha:` | 120 秒 |
| `PWD_RETRY` | `smart:pwd:retry:` | 600 秒 |
| `DICT` | `smart:dict:` | 2 小时 |
| `SYS_CONFIG` | `smart:config:` | 2 小时 |
| `IDEMPOTENT` | `smart:dedup:` | 10 秒 |
| `MENU_PERMISSION` | `smart:menu:` | 1 小时 |

## 构建与部署

### 症状：编译报错 "Cannot resolve symbol"

**排查步骤：**
1. 执行 `mvn clean install -DskipTests` 全量构建
2. 确认 `smart-common-bom` 版本正确
3. IDE 刷新 Maven 依赖

### 症状：单体模式启动后接口 404

**排查步骤：**
1. 检查 `@ComponentScan` 是否包含新模块的包路径
2. 检查 `@MapperScan` 是否包含新模块的 mapper 包路径
3. 检查 `smart-boot/pom.xml` 是否包含新模块依赖
