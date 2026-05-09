# Smart 流程与表单知识库

> 本文档涵盖 Smart 平台流程引擎和表单模块的架构设计、核心概念、接入方式和开发指南。

## 1. 流程引擎概述

### 1.1 技术选型

- **引擎：** Flowable 7.x（适配 Spring Boot 3 / JDK 17）
- **架构风格：** DDD（领域驱动设计）分层架构
- **与 MyBatis-Plus 的关系：** 排除 Flowable 自带 MyBatis，使用项目统一的 MyBatis-Plus

### 1.2 DDD 分层结构

```
com.smart.flow/
├── api/                        # API 层（DTO、命令对象）
│   ├── definition/             # 流程定义 DTO
│   ├── form/                   # 表单绑定 DTO
│   ├── instance/               # 流程实例 DTO
│   ├── taskcenter/             # 任务中心 DTO
│   ├── assignee/               # 审批人策略 DTO
│   ├── dsl/                    # FlowChart DSL 相关
│   └── exception/              # 异常定义
├── domain/                     # 领域层（核心业务逻辑）
│   ├── chart/                  # 流程图领域
│   │   ├── FlowChart           # 流程图聚合根
│   │   ├── CompiledArtifact    # 编译产物（BPMN XML）
│   │   └── FlowChartValidator  # 流程图校验器
│   ├── definition/             # 流程定义领域
│   │   ├── FlowDefinitionRepository
│   │   ├── FlowDefinitionStatus
│   │   └── FlowDefinitionPublishedEvent
│   ├── instance/               # 流程实例领域
│   │   ├── FlowInstanceRepository
│   │   ├── BizNoGenerator
│   │   └── event/              # 领域事件
│   ├── assignee/               # 审批人策略
│   ├── audit/                  # 审计日志
│   ├── form/                   # 表单领域
│   └── org/                    # 组织领域
├── application/                # 应用服务层
│   ├── definition/             # FlowDefinitionAppService
│   ├── instance/               # 流程实例应用服务
│   ├── form/                   # 表单绑定应用服务
│   └── taskcenter/             # 任务中心查询服务
├── infrastructure/             # 基础设施层
│   └── persistence/mapper/     # MyBatis Mapper
└── interfaces/                 # 接口层
    └── rest/
        ├── FlowDefinitionController   # 流程定义 CRUD + 发布/归档
        ├── FlowInstanceController     # 流程实例（启动/撤回/终止）
        ├── TaskCenterController       # 任务中心（待办/已办/我发起/抄送）
        ├── FormBindingController      # 表单绑定管理
        └── FlowExceptionHandler       # 全局异常处理
```

## 2. 核心概念

### 2.1 流程定义生命周期

```
草稿 (DRAFT) → 发布 (PUBLISHED) → 归档 (ARCHIVED)
```

**发布流程：** 前端设计器保存 FlowChart DSL (JSON) → 编译为 BPMN XML → 部署到 Flowable → 状态变为 PUBLISHED

### 2.2 核心实体关系

```
flow_definition（流程定义）
    │
    ├── flow_form_binding（表单绑定）
    │       └── 关联 sys_form（表单定义）
    │
    └── Flowable process_definition（Flowable 流程定义）
            │
            └── flow_instance_biz（业务流程实例）
                    │
                    ├── flow_form_snapshot（表单快照）
                    ├── flow_approval_record（审批记录）
                    ├── flow_cc_record（抄送记录）
                    ├── flow_delegation（转办/委托）
                    └── flow_task_view（任务视图，CQRS 读模型）
```

### 2.3 FlowChart DSL

前端流程设计器生成 JSON 格式的 DSL，描述流程图的节点和连线。后端将 DSL 编译为 Flowable 的 BPMN XML。

### 2.4 CQRS 读模型

`flow_task_view` 表是专门为任务中心列表查询设计的 CQRS 读模型，避免跨 `act_*` 引擎表和业务表的复杂 JOIN 查询。

## 3. 核心 REST 接口

### 3.1 流程定义 (`/flow/definition`)

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/flow/definition/page` | 分页查询流程定义 |
| GET | `/flow/definition/{id}` | 查看流程定义详情 |
| POST | `/flow/definition` | 创建流程定义（草稿） |
| PUT | `/flow/definition` | 更新流程定义 |
| POST | `/flow/definition/{id}/publish` | 发布（DSL→BPMN→部署） |
| POST | `/flow/definition/{id}/archive` | 归档 |

### 3.2 流程实例 (`/flow/instance`)

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/flow/instance/start` | 启动流程实例 |
| POST | `/flow/instance/{id}/revoke` | 撤回流程 |
| POST | `/flow/instance/{id}/terminate` | 终止流程 |

### 3.3 任务中心 (`/flow/task-center`)

| 方法 | URL | 说明 |
|------|-----|------|
| GET | `/flow/task-center/todo` | 我的待办 |
| GET | `/flow/task-center/done` | 我的已办 |
| GET | `/flow/task-center/initiated` | 我发起的 |
| GET | `/flow/task-center/cc` | 抄送给我 |
| PUT | `/flow/task-center/cc/{id}/read` | 标记抄送为已读 |

### 3.4 表单绑定 (`/flow/form`)

| 方法 | URL | 说明 |
|------|-----|------|
| POST | `/flow/form/bindForm` | 绑定表单到流程/节点 |
| GET | `/flow/form/{chartId}` | 查询绑定的表单 |
| GET | `/flow/form/snapshot-timeline` | 获取表单快照时间线 |

## 4. 表单模块

### 4.1 核心表

**sys_form（表单定义）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| form_id | BIGSERIAL | 主键 |
| form_key | VARCHAR | 表单唯一标识 |
| schema | TEXT | 表单 Schema（JSON，前端设计器生成） |
| layout | TEXT | 表单布局（JSON） |
| status | VARCHAR | 状态：0=草稿，1=已发布 |
| version | INT | 版本号 |

**sys_form_data（表单提交数据）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| form_id | BIGINT | 关联表单 ID |
| form_data | TEXT | 表单数据（JSON） |
| user_id | BIGINT | 提交人用户 ID |

## 5. 自定义表单接入流程

### 5.1 完整接入步骤

**第一步：设计表单**

在「表单管理」页面使用可视化设计器创建表单，设计器会生成 JSON Schema。

**第二步：创建流程定义**

在「流程定义」页面创建新的流程，使用流程设计器绘制流程图（节点、连线、审批人策略）。

**第三步：绑定表单到流程**

通过 `FormBindingController` 将表单绑定到流程：

```
POST /flow/form/bindForm
{
  "chartId": 100,         // 流程定义 ID
  "nodeKey": null,        // null=流程级别默认表单
  "formId": 200,          // 表单 ID
  "fieldRules": [...]     // 字段权限规则
}
```

**第四步：配置节点级别表单覆盖（可选）**

特定审批节点可以覆盖默认表单或调整字段权限：

```
POST /flow/form/bindForm
{
  "chartId": 100,
  "nodeKey": "approve_manager",  // 特定节点 key
  "formId": 200,
  "fieldRules": [
    { "field": "leaveDays", "rule": "r" },     // 只读
    { "field": "salary", "rule": "hidden" }     // 隐藏
  ]
}
```

**第五步：发布流程**

发布流程定义，DSL 编译为 BPMN XML 并部署到 Flowable。

**第六步：发起流程**

用户在前端填写表单并提交，后端启动流程实例：

```
POST /flow/instance/start
{
  "definitionId": 100,
  "formData": { "leaveDays": 3, "reason": "..." }
}
```

### 5.2 表单绑定机制详解

**flow_form_binding 表：**

| 字段 | 说明 |
|------|------|
| binding_id | 主键 |
| chart_id | 流程定义 ID |
| node_key | NULL=流程级别默认；非 NULL=特定节点覆盖 |
| form_id | 表单 ID |
| field_rules | JSON 格式的字段权限规则 |

**字段权限规则（field_rules）：**

```json
[
  { "field": "leaveDays", "rule": "rw" },
  { "field": "salary", "rule": "hidden" },
  { "field": "comment", "rule": "r" }
]
```

| rule 值 | 含义 |
|---------|------|
| `rw` | 可读写 |
| `r` | 只读 |
| `hidden` | 隐藏 |

### 5.3 表单快照机制

每次审批动作都会持久化当时的表单 payload 到 `flow_form_snapshot` 表：

| 字段 | 说明 |
|------|------|
| snapshot_id | 主键 |
| process_instance_id | Flowable 流程实例 ID |
| task_id | 产生此快照的任务（NULL=发起人快照） |
| node_key | 节点标识 |
| form_id | 表单 ID |
| snapshot_type | 0=发起人, 1=审批, 2=会签, 3=系统补丁 |
| payload | 表单数据 JSON |
| captured_by | 捕获人 |
| captured_at | 捕获时间 |

**用途：** 审计时间线回放，可以查看每个审批人在每个时间点看到的表单内容。

## 6. 审批记录

**flow_approval_record 表** 记录所有审批动作：

| 动作 | 说明 |
|------|------|
| approve | 同意 |
| reject | 驳回 |
| transfer | 转办 |
| delegate | 委托 |
| cc | 抄送 |

## 7. 流程引擎开发注意事项

### 7.1 Mapper 路径

流程模块的 Mapper 路径为 `com.smart.flow.infrastructure.persistence.mapper`，在单体模式下需要在 `SmartBootApplication` 的 `@MapperScan` 中显式配置。

### 7.2 Flowable 表

Flowable 引擎表（`act_*` / `flw_*`）由 Flowable 自动创建和管理，**不要手动修改这些表**。

### 7.3 排除 Flowable 自带 MyBatis

在 `smart-flow-biz/pom.xml` 中已排除 Flowable 自带的 MyBatis 依赖，避免与项目统一的 MyBatis-Plus 冲突。

### 7.4 前端组件（smart-ui Web 端）

以下路径均相对于 `smart-ui/src/`：

- **流程设计器：** `views/flow/designer/designer.vue`
- **表单设计器：** `views/form/designer/designer.vue`
- **流程预览：** `components/FlowPreview/`
- **表单渲染器：** `components/FormRenderer/`

App 端（smart-app）的流程相关页面位于 `smart-app/src/pages/flow/`，包括审批列表、审批详情、发起审批等。
