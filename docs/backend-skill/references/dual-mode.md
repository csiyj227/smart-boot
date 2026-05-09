# 双模式架构（cloud/boot）

Smart 支持**微服务模式（cloud）**和**单体模式（boot）**，通过 Maven Profile 切换。两种模式在包扫描、依赖引入、配置文件上有显著差异。

## 微服务模式（cloud，默认）

```bash
mvn clean package -P cloud
```

- 每个业务模块独立部署，各有自己的启动类
- 依赖 Nacos 注册中心 + Spring Cloud Gateway
- 服务间通过 Feign 远程调用
- 配置从 Nacos 拉取（使用 `bootstrap.yml`）

**启动类示例：**

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableSmartResourceServer
public class SmartAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartAdminApplication.class, args);
    }
}
```

**关键点：**
- 无需显式 `@ComponentScan`，默认扫描启动类所在包
- `@EnableSmartResourceServer` — 启用 OAuth2 资源服务器（除 auth 和 gateway 外的所有业务服务都需要）
- Feign 拦截器已通过 `RpcAutoConfiguration` 自动配置，无需 `@EnableSmartFeignClients`

## 单体模式（boot）

```bash
mvn clean package -P boot
```

- 所有业务模块聚合到 `smart-boot` 一个 JAR
- 无需 Nacos、Gateway
- 服务间本地 Bean 注入调用（非 Feign）

**启动类（SmartBootApplication）必须：**

1. 显式 `@ComponentScan` 枚举所有模块的根包
2. 显式 `@MapperScan` 枚举所有模块的 mapper 包路径
3. 在 `smart-boot/pom.xml` 中添加新模块依赖
4. 在根 `pom.xml` 的 `boot` profile 中添加新模块

**遗漏任何一步都会导致 404 或启动失败。**

## 两种模式关键差异

| 维度 | 微服务模式（cloud） | 单体模式（boot） |
|------|---------------------|-------------------|
| `@ComponentScan` | 不需要（默认扫描启动类所在包） | **必须**显式枚举所有模块根包 |
| `@MapperScan` | 各模块自动扫描 | **必须**显式列出所有 mapper 包路径 |
| Feign | 服务间 Feign 远程调用 | 本地 Bean 注入，不需要 Feign |
| `@EnableSmartResourceServer` | 每个服务独立鉴权 | 不需要（统一处理） |
| 配置文件 | `bootstrap.yml`（从 Nacos 拉取） | `application.yml`（本地配置） |
| 服务调用 | Feign 远程调用 | 本地 Bean 注入 |
| 端口 | 各服务独立端口 | 统一 8080 |

## 配置文件

### 微服务模式（bootstrap.yml）

```yaml
server:
  port: 9201
spring:
  application:
    name: smart-system
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:localhost}:${NACOS_PORT:8848}
      config:
        server-addr: ${NACOS_HOST:localhost}:${NACOS_PORT:8848}
        file-extension: yml
  config:
    import:
      - optional:nacos:${spring.application.name}.yml
      - optional:nacos:application-common.yml
```

### 单体模式（application.yml）

```yaml
server:
  port: 8080
spring:
  application:
    name: smart-boot
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:csiyj_db}
    username: ${DB_USER:csiyj}
    password: ${DB_PASS:******}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: delFlag
      logic-delete-value: "1"
      logic-not-delete-value: "0"

smart:
  tenant:
    enabled: true
```

## 服务端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| smart-register (Nacos) | 8848 | 注册/配置中心 |
| smart-gateway | 8080 | API 网关 |
| smart-auth | 9200 | 认证服务 |
| smart-system | 9201 | 用户权限管理 |
| smart-flow | 5008 | 流程引擎 |
| smart-ai | 5009 | AI 平台 |
| smart-boot | 8080 | 单体模式 |
