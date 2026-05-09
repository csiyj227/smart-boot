# 技术栈与模块依赖

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 不升级到 21+ |
| Spring Boot | 3.3.3 | — |
| Spring Cloud | 2023.0.3 | 微服务模式必需 |
| Spring Cloud Alibaba | 2023.0.1.2 | Nacos 注册/配置中心 |
| Spring Authorization Server | 1.3.1 | OAuth2 认证 |
| MyBatis-Plus | 3.5.7 | ORM，不用 JPA |
| PostgreSQL | 16.x (pgvector) | 不兼容 MySQL |
| Redis | 7.x (Redisson 3.27.0) | 缓存/会话 |
| Nacos | 2.x | 微服务模式必需 |
| Flowable | 7.x | 流程引擎 |
| Knife4j | 4.5.0 | API 文档 |

## 模块清单

| 模块 | 职责 | 何时引入 |
|------|------|----------|
| `smart-common-core` | ApiResult、工具类、枚举常量、异常、国际化 | 所有模块自动传递依赖 |
| `smart-common-data` | MyBatis-Plus、多租户拦截器、动态数据源、Redis、分页 | 需要数据库访问的模块 |
| `smart-common-security` | OAuth2 资源服务器、@ServiceApi、权限注解 | 需要鉴权的业务服务 |
| `smart-common-feign` | Feign 自动配置（RpcAutoConfiguration）、ServiceCallInterceptor | 需要调用其他服务的模块 |
| `smart-common-log` | @AuditTrace 审计追踪切面 | 需要记录审计日志的模块 |
| `smart-common-swagger` | Knife4j / OpenAPI 文档 | 需要生成 API 文档的模块 |
| `smart-common-idempotent` | @Dedup 幂等性控制 | 需要防重复提交的模块 |
| `smart-common-xss` | XSS 过滤、@MaskField 脱敏 | 需要防 XSS 的模块 |
| `smart-common-gateway` | 动态路由、金丝雀负载均衡 | 仅 smart-gateway 使用 |

## BOM 统一版本管理

所有内部模块版本由 `smart-common-bom` 统一管理，业务模块 pom.xml 中**不写 version 标签**。

```xml
<dependencies>
    <!-- 1. 自身 API 模块 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-system-api</artifactId>
    </dependency>
    <!-- 2. 数据层 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-common-data</artifactId>
    </dependency>
    <!-- 3. 安全 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-common-security</artifactId>
    </dependency>
    <!-- 4. Feign 客户端 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-common-feign</artifactId>
    </dependency>
    <!-- 5. 审计日志 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-common-log</artifactId>
    </dependency>
    <!-- 6. API 文档 -->
    <dependency>
        <groupId>com.smart</groupId>
        <artifactId>smart-common-swagger</artifactId>
    </dependency>
    <!-- 7. Nacos（微服务模式必需） -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
</dependencies>
```

## 自动配置注册

使用 Spring Boot 3 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 方式注册（非旧版 `spring.factories`）。

| 模块 | 自动配置类 | 功能 |
|------|------------|------|
| common-core | `JacksonConfiguration` | Jackson 序列化配置 |
| common-core | `I18nConfiguration` | 国际化消息源配置 |
| common-core | `WebFilterAutoConfiguration` | RequestTraceFilter 注册 |
| common-data | `SmartCommonDataAutoConfiguration` | 数据层自动配置 |
| common-data | `MybatisPlusConfiguration` | 拦截器链：租户→数据权限→分页→乐观锁 |
| common-data | `RedisConfiguration` | Redis 连接与序列化 |
| common-security | `SmartResourceServerAutoConfiguration` | OAuth2 资源服务器 |
| common-feign | `RpcAutoConfiguration` | Feign 拦截器自动注册（替代 @EnableSmartFeignClients） |
| common-xss | `XssAutoConfiguration` | XSS 过滤器注册 |
| common-swagger | `SmartSwaggerConfiguration` | Knife4j 文档配置 |
