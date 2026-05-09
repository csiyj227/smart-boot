# Smart 架构设计知识库

> 本文档是 Smart 平台的架构设计全景，适用于 AI RAG 知识库检索和新成员快速了解项目。

## 1. 项目定位

Smart 是一个**企业级多租户微服务后台管理脚手架**，基于 Spring Cloud + Spring Authorization Server 构建。核心能力包括：

- **RBAC 权限管理**：用户、角色、菜单、部门、岗位、数据权限
- **流程引擎**：Flowable 7.x，DDD 架构，流程定义/实例/任务中心/表单绑定
- **AI 智能平台**：多 Agent 对话、MCP 工具集成、RAG 知识库
- **NL2SQL 智能分析**：自然语言转 SQL、数据源管理、智能问数
- **代码生成器**：表导入、多模板、一键生成 CRUD
- **多租户**：租户隔离、上下文传播、TenantBroker

## 2. 技术架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                    前端层 (Presentation)                       │
│  ┌─────────────────┐  ┌──────────────────────────────────┐   │
│  │  smart-ui (Web)  │  │  smart-app (App: H5/小程序/原生)  │   │
│  │  Vue3+Element+   │  │  uni-app + Vue3 + Pinia          │   │
│  │  Pinia+Vite      │  │                                  │   │
│  └────────┬─────────┘  └────────────┬─────────────────────┘   │
│           │ /api (port 8888)        │ /api (port 9000)        │
└───────────┼─────────────────────────┼─────────────────────────┘
            │                         │
            ▼                         ▼
┌──────────────────────────────────────────────────────────────┐
│              网关层 (Gateway) — port 8080                     │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  smart-gateway (Spring Cloud Gateway)                  │   │
│  │  - 动态路由 / 限流熔断 / 统一鉴权                        │   │
│  │  - 剥离外部 X-Internal-Call 头                          │   │
│  │  - 注入 X-Tenant-Id / 转发到下游服务                     │   │
│  └────────────────────────────────────────────────────────┘   │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    业务服务层 (Services)                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │smart-auth│ │smart-    │ │smart-flow│ │smart-ai  │        │
│  │ (9200)   │ │system    │ │ (5008)   │ │ (5009)   │        │
│  │OAuth2 AS │ │ (9201)   │ │流程引擎   │ │AI 平台   │        │
│  ┌──────────┐ ┌──────────┐                                   │
│  │smart-    │ │smart-    │                                   │
│  │codegen   │ │nl2sql    │                                   │
│  │代码生成   │ │智能问数   │                                   │
│  └──────────┘ └──────────┘                                   │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    基础设施层 (Infrastructure)                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────────────┐  │
│  │  Nacos   │ │  Redis   │ │  PostgreSQL 16 (pgvector)    │  │
│  │注册/配置  │ │缓存/会话  │ │  数据库 + 向量存储            │  │
│  │ (8848)   │ │ (6379)   │ │  (5432)                      │  │
│  └──────────┘ └──────────┘ └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## 3. 双模式架构

### 3.1 微服务模式（cloud profile，默认）

```
mvn clean package -P cloud
```

**特点：**
- 每个业务模块独立进程、独立端口
- 通过 Nacos 进行服务注册和配置管理
- 通过 Spring Cloud Gateway 统一网关入口
- 服务间通过 Feign + @ServiceApi 保护内部调用
- 适合生产环境、团队协作开发

**启动顺序：**
1. Nacos (8848) → 2. Auth (9200) → 3. Gateway (8080) → 4. system (9201) → 5. 其他服务

### 3.2 单体模式（boot profile）

```
mvn clean package -P boot
```

**特点：**
- 所有服务聚合到 smart-boot 单一 JAR
- 无需 Nacos、Gateway
- 统一端口 8080
- 适合开发调试、小规模部署

**关键差异：**
- `SmartBootApplication` 必须显式 `@ComponentScan` 所有模块包
- `SmartBootApplication` 必须显式 `@MapperScan` 所有 Mapper 包
- 不使用 `@EnableSmartFeignClients`（本地 Bean 调用）

## 4. 模块依赖关系

```
smart-common-bom ──── 统一版本管理（所有模块引入）
    │
smart-common-core ──── ApiResult<T>、工具类、枚举常量、异常、国际化（被所有模块传递依赖）
    │
    ├── smart-common-data ──── MyBatis-Plus、多租户拦截器、Redis、分页
    ├── smart-common-security ──── OAuth2 资源服务器、@ServiceApi 服务间调用保护
    ├── smart-common-feign ──── Feign 自动配置（RpcAutoConfiguration）、ServiceCallInterceptor
    ├── smart-common-log ──── @AuditTrace 审计追踪切面
    ├── smart-common-swagger ──── Knife4j / OpenAPI
    ├── smart-common-idempotent ──── @Dedup 幂等控制
    ├── smart-common-xss ──── XSS 过滤、@MaskField 脱敏
    └── smart-common-gateway ──── 网关专用组件（动态路由、金丝雀负载均衡）

smart-system-api ──── Feign 接口 + DTO（被其他服务引用）
smart-system-biz ──── 用户权限业务实现

smart-flow-api ──── 流程 DTO
smart-flow-biz ──── 流程引擎（DDD 架构 + Flowable 7.x）

smart-auth ──── OAuth2 授权服务器
smart-gateway ──── API 网关
smart-codegen ──── 代码生成器
smart-ai ──── AI 智能平台（Spring AI + 阿里百炼）
smart-nl2sql ──── NL2SQL 智能分析
smart-register ──── 内嵌 Nacos（开发环境）
smart-boot ──── 单体模式聚合启动器
```

## 5. 认证与安全架构

### 5.1 OAuth2 认证流程

```
客户端 → POST /oauth2/token (grant_type=password)
    → smart-gateway 转发到 smart-auth
    → smart-auth 调用 smart-system 获取用户信息（Feign + @ServiceApi）
    → 验证密码、生成 JWT Token（RSA 签名）
    → 返回 access_token + refresh_token
```

### 5.2 Token 规格

| 项目 | 值 |
|------|-----|
| 签名算法 | RSA |
| access_token 有效期 | 12 小时 |
| refresh_token 有效期 | 30 天 |
| 存储 | Redis |
| 传递方式 | `Authorization: Bearer {token}` |

### 5.3 权限控制层级

| 层级 | 机制 | 说明 |
|------|------|------|
| 路由级 | 动态路由（前端） | 根据 sys_menu 菜单树生成用户可见路由 |
| 元素级 | v-permission 指令（前端） | 按钮/操作根据权限标识显示/隐藏 |
| 方法级 | @PreAuthorize（后端） | Spring Security 注解鉴权 |
| 接口级 | @ServiceApi（后端） | 服务间内部调用保护 |
| 数据级 | DataPermissionInterceptor（后端） | MyBatis-Plus 数据权限拦截器 |
| 租户级 | SmartTenantInterceptor（后端） | 自动拼接 tenant_id 条件 |

### 5.4 多租户架构

**租户 ID 解析优先级：** Redis override > Header (`X-Tenant-Id`) > JWT claim

**数据隔离方式：** 共享数据库 + 共享 Schema + 行级隔离（`tenant_id` 字段）

**配置方式：**
- Entity 标注 `@TenantEntity` 注解（位于 `com.smart.common.core.annotation.TenantEntity`）
- 或在 `smart.tenant.tables` 配置项中列出表名
- `SmartTenantInterceptor` 自动在 SQL 中拼接 `AND tenant_id = ?`
- `TenantContext`（基于 TransmittableThreadLocal）管理当前线程租户 ID，支持 try-with-resources 范围和函数式 runAs/supplyAs

## 6. 前端架构

### 6.1 Web 端（smart-ui）

- **框架：** Vue 3 + TypeScript + Element Plus + Pinia + Vite
- **路由：** 静态路由 + 动态路由（从后端 sys_menu 生成）
- **权限：** 路由守卫 + v-permission 指令 + meta.permission
- **主题：** CSS 变量（violet-indigo 渐变色系），支持亮/暗色切换
- **代理：** `/api` → `http://localhost:8080`（开发端口 8888）

### 6.2 App 端（smart-app）

- **框架：** uni-app (Vue 3) + TypeScript + Pinia
- **平台：** H5、微信小程序、App Plus
- **路由：** pages.json 静态配置（无动态路由）
- **权限：** 无前端权限控制，依赖后端接口鉴权
- **SSE：** H5 端用 fetch + ReadableStream，小程序端不支持
- **代理：** `/api` → `http://localhost:8080`（开发端口 9000）

## 7. 数据库架构

### 7.1 数据库选型

PostgreSQL 16 + pgvector 扩展（支持向量搜索，用于 AI RAG 知识库）

### 7.2 表分类

| 前缀 | 模块 | 核心表 |
|------|------|--------|
| `sys_` | system 系统 | user、role、menu、dept、post、dict_type、dict_data、oauth_client_details、log、login_log、config、notice、form、form_data、file、job |
| `flow_` | 流程引擎 | definition、form_binding、instance_biz、form_snapshot、approval_record、cc_record、delegation、task_view |
| `ai_` | AI 平台 | agent、conversation、message、knowledge_base、knowledge_document、knowledge_segment、mcp_server、mcp_tool |
| `gen_` | 代码生成 | table、table_column、template、template_group |
| `qrtz_` | 定时任务 | Quartz JDBC 持久化表（11 张） |
| `act_` / `flw_` | Flowable | 引擎自动创建的运行时和历史表 |

### 7.3 必须字段

每张业务表必须包含：`create_by`、`create_time`、`update_by`、`update_time`、`del_flag`、`tenant_id`

## 8. 自动配置机制

使用 Spring Boot 3 的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册方式（非旧版 spring.factories）。

| 模块 | 自动配置类 | 功能 |
|------|------------|------|
| common-core | `JacksonConfiguration` | Jackson 序列化配置 |
| common-core | `I18nConfiguration` | 国际化消息源配置 |
| common-core | `WebFilterAutoConfiguration` | RequestTraceFilter 注册 |
| common-data | `MybatisPlusConfiguration` | 拦截器链：租户→数据权限→分页→乐观锁 |
| common-data | `RedisConfiguration` | Redis 连接与序列化 |
| common-security | `SmartResourceServerAutoConfiguration` | OAuth2 资源服务器 |
| common-feign | `RpcAutoConfiguration` | Feign 拦截器自动注册（替代 @EnableSmartFeignClients） |