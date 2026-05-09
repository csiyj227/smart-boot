# Smart Admin

> 企业级多租户微服务后台管理脚手架，以 RuoYi-Cloud 为功能蓝本，集成 AI 智能平台、流程引擎、表单设计器、NL2SQL 智能分析等高级能力。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.3.3 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2023.0.3 / 2023.0.1.2 |
| 认证 | Spring Authorization Server | 1.3.1 |
| ORM | MyBatis-Plus | 3.5.7 |
| 流程引擎 | Flowable | 7.x |
| AI | Spring AI + 阿里百炼 | — |
| 数据库 | PostgreSQL (pgvector) | 16.x |
| 缓存 | Redis (Redisson) | 7.x |
| 注册中心 | Nacos | 2.x |
| 前端 Web | Vue 3 + Element Plus + Vite 5 + Pinia + TypeScript | 3.4+ |
| 前端 App | uni-app (Vue 3) + Pinia + TypeScript | — |

## 项目结构

```
smart/
├── db/                          # 数据库 SQL 脚本
├── docs/                        # 项目文档
├── docker/                      # Docker 编排配置
├── smart-common/                # 公共模块（不可依赖业务模块）
│   ├── smart-common-bom/        # 依赖版本管理（BOM）
│   ├── smart-common-core/       # 核心工具：R<T>、常量、异常、工具类
│   ├── smart-common-data/       # 数据层：MyBatis-Plus、多租户、动态数据源、Redis
│   ├── smart-common-security/   # 安全：OAuth2 资源服务器、权限注解、内部调用保护
│   ├── smart-common-feign/      # Feign 客户端、内部调用拦截
│   ├── smart-common-log/        # @OpLog 操作日志切面
│   ├── smart-common-swagger/    # Knife4j / OpenAPI 文档
│   ├── smart-common-idempotent/ # @Idempotent 幂等性控制
│   ├── smart-common-xss/        # XSS 过滤
│   └── smart-common-gateway/    # 网关公共组件
├── smart-register/              # Nacos 注册中心（内嵌，开发环境免部署）
├── smart-auth/                  # 认证服务（OAuth2 授权服务器）
├── smart-gateway/               # API 网关（Spring Cloud Gateway）
├── smart-upms/                  # 用户权限管理服务
│   ├── smart-upms-api/          # Feign 接口 + DTO
│   └── smart-upms-biz/          # 业务实现（用户、角色、菜单、部门、字典等）
├── smart-codegen/               # 代码生成器
│   ├── smart-codegen-api/
│   └── smart-codegen-biz/
├── smart-flow/                  # 流程引擎（Flowable 7.x，DDD 架构）
│   ├── smart-flow-api/          # 流程 DTO、命令对象
│   └── smart-flow-biz/          # 流程定义、实例、任务中心、表单绑定
├── smart-ai/                    # AI 智能平台（Spring AI + 阿里百炼）
├── smart-nl2sql/                # NL2SQL 智能分析（自然语言转 SQL）
├── smart-boot/                  # 单体模式聚合启动器
├── smart-ui/                    # 前端 Web（Vue 3 + Element Plus）
└── smart-app/                   # 前端 App（uni-app 跨平台）
```

## 双模式架构

项目支持 **微服务模式** 和 **单体模式** 两种部署方式，通过 Maven Profile 切换：

### 微服务模式（cloud，默认）

```bash
mvn clean package -P cloud
```

- 每个业务模块独立部署为微服务
- 依赖 Nacos 注册中心 + Spring Cloud Gateway
- 服务间通过 Feign 调用
- 适合生产环境

### 单体模式（boot）

```bash
mvn clean package -P boot
```

- 所有业务模块聚合到 `smart-boot` 一个 JAR
- 无需 Nacos、Gateway
- 适合开发调试和小规模部署

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 16（推荐使用 pgvector 镜像）
- Redis 7.x
- Nacos 2.x（微服务模式必需）
- Node.js 18+（前端开发）

### 1. 启动基础设施

```bash
cd docker
docker-compose up -d    # 启动 PostgreSQL
```

### 2. 初始化数据库

按顺序执行 `db/` 目录下的 SQL 脚本：

1. `public.sql` — 基础表结构（用户、角色、菜单、字典等）
2. `flow.sql` — 流程引擎表
3. `form.sql` — 表单模块表
4. `flow_menu.sql` — 流程/表单菜单数据
5. 其他业务 SQL

### 3. 启动后端

**微服务模式**（按顺序启动）：

```
1. SmartNacosApplication     — 注册中心（8848）
2. SmartAuthApplication      — 认证服务（9200）
3. SmartGatewayApplication   — API 网关（8080）
4. SmartAdminApplication     — 用户权限（9201）
5. SmartFlowApplication      — 流程引擎（5008）
6. SmartCodegenApplication   — 代码生成
7. SmartAiApplication        — AI 平台（5009）
8. SmartNl2SqlApplication    — NL2SQL
```

**单体模式**：

```
SmartBootApplication — 所有服务（8080）
```

### 4. 启动前端 Web

```bash
cd smart-ui
npm install
npm run dev              # 开发环境，端口 8888
```

### 5. 启动前端 App

```bash
cd smart-app
npm install
npm run dev:h5           # H5 开发，端口 9000
npm run dev:mp-weixin    # 微信小程序
```

## 核心功能

| 模块 | 功能 |
|------|------|
| **认证与安全** | OAuth2 密码模式、验证码登录、Token 管理、RSA 加密 |
| **用户权限** | 用户、角色、菜单、部门、岗位、字典、数据权限 |
| **多租户** | 租户隔离、上下文传播、TenantBroker |
| **流程引擎** | 流程定义（DSL→BPMN）、流程实例、任务中心、审批记录、表单绑定 |
| **表单设计器** | 可视化表单设计、Schema 驱动渲染、字段权限控制 |
| **代码生成** | 表导入、多模板、代码预览、一键生成 CRUD |
| **AI 智能平台** | 多 Agent 对话、MCP 工具集成、知识库（RAG）、模型管理 |
| **NL2SQL** | 自然语言转 SQL、数据源管理、数据集、智能问数 |
| **系统监控** | 操作日志、登录日志、在线用户、定时任务 |
| **文件管理** | 本地存储 + S3 兼容对象存储、分片上传 |

## 文档目录

```
docs/
├── PRD.md                       # 产品需求文档
├── SPEC.md                      # 技术规格书
├── TODO_LIST.md                 # 待办事项清单
├── FEATURE-MATRIX.md            # 功能对比矩阵
├── backend-skill.md             # 后端开发规范
├── web/
│   └── frontend-skill.md        # 前端 Web 开发规范
├── app/
│   └── frontend-skill.md        # 前端 App 开发规范
├── knowledge/
│   ├── architecture.md          # 架构设计知识库
│   ├── deployment.md            # 部署运维知识库
│   ├── flow-and-form.md         # 流程与表单知识库
│   └── database.md              # 数据库规范与菜单数据写入指南
```

## 许可证

私有项目，仅限内部使用。
