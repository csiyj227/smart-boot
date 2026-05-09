---
name: smart-backend-dev
version: 1.0.0
description: Smart 后端开发 Skill。触发场景：Smart 项目后端 Java 代码编写、Spring Boot/Cloud 微服务开发、MyBatis-Plus 实体/Service/Controller 生成、OAuth2 认证配置、多租户开发、Feign 服务间调用、代码规范检查。当用户提到 Smart 后端开发、写 Controller、写 Entity、写 Service、新增模块、配置租户、添加 API 接口、写 Feign 远程调用时，应使用此 skill。
---

# Smart 后端开发 Skill

本文件是 Smart 后端开发的索引和工作流指引。细则下沉到 `references/` 目录，按需加载。

## 参考规范索引

**⛔ 按 READ 阶段按需加载，不要一次性读完所有参考文件。**

### 核心框架

| 维度 | 文件 | 何时加载 |
|------|------|----------|
| 技术栈与模块依赖 | references/tech-stack.md | 新建模块、引入依赖、理解模块关系时 |
| 双模式架构（cloud/boot） | references/dual-mode.md | 切换构建模式、理解启动差异、新增模块到 boot 时 |
| 统一响应格式（ApiResult） | references/api-convention.md | 写 Controller 返回值、处理错误码时 |
| 注解与自动配置速查 | references/annotations-autoconfig.md | 使用自定义注解、理解自动配置时 |

### 编码规范

| 维度 | 文件 | 何时加载 |
|------|------|----------|
| Entity/Mapper/Service/Controller 规范 | references/coding-standards.md | 编写业务代码时 |
| 多租户开发 | references/multi-tenant.md | 添加租户隔离表、使用 TenantContext 时 |
| 安全与内部调用 | references/security-internal-call.md | 配置 @ServiceApi、Feign 调用、权限控制时 |
| 数据库规范 | ../knowledge/database.md | 编写 SQL 脚本、建表时 |

### 运维与排障

| 维度 | 文件 | 何时加载 |
|------|------|----------|
| 部署与启动 | ../knowledge/deployment.md | 部署、启动、环境变量配置时 |
| 常见问题排查 | references/troubleshooting.md | 启动报 404、租户数据查不到、Feign 调用失败时 |

## 工作流

### 新增业务模块

1. 加载 `references/tech-stack.md` 确认模块依赖和 BOM 版本
2. 加载 `references/dual-mode.md` 确认单体模式需要的包扫描配置
3. 按分层规范创建：Entity → Mapper → Service → Controller
4. 加载 `references/coding-standards.md` 校验命名和分层合规
5. 如需租户隔离，加载 `references/multi-tenant.md` 配置 @TenantEntity
6. 如需 Feign 调用，加载 `references/security-internal-call.md` 配置 @ServiceApi

### 新增 REST 接口

1. 加载 `references/api-convention.md` 确认 URL 命名和返回格式
2. 加载 `references/annotations-autoconfig.md` 确认需要使用的注解
3. 编写 Controller → Service → Mapper
4. 如需权限控制，使用 `@PreAuthorize`
5. 如需审计日志，使用 `@AuditTrace`
6. 如需幂等控制，使用 `@Dedup`

### 代码审查

1. 加载对应 references 文件
2. 逐项检查命名规范、分层规范、安全注解、租户标识

## 硬性规则

- ⛔ **禁止使用 `@Autowired` 字段注入**，使用 `@RequiredArgsConstructor` 构造器注入
- ⛔ **所有 Controller 方法返回 `ApiResult<T>`**，OAuth2 Token 端点除外
- ⛔ **多租户表必须标注 `@TenantEntity`**，否则数据不会自动过滤
- ⛔ **拦截器链顺序不可变更**：租户 → 数据权限 → 分页 → 乐观锁
- ⛔ **`com.smart` 依赖不写 `<version>`**，由 BOM 统一管理
- ⛔ **单体模式新增模块必须同时更新**：ComponentScan + MapperScan + pom.xml + 根 pom profile
- ⛔ **数据库仅用 PostgreSQL**，禁止 MySQL 语法
- ⛔ **禁止数据库外键约束**，应用层保证一致性
- ⛔ **禁止 `TIMESTAMPTZ`**，使用 `TIMESTAMP`
- ⛔ **服务间内部调用接口必须标注 `@ServiceApi`**
