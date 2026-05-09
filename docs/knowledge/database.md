# Smart 数据库规范与菜单数据写入指南

> 本文档涵盖数据库命名规范、必须字段、菜单数据写入规则等，是后端开发和 SQL 脚本编写的核心参考。

## 1. 数据库基本信息

| 项目 | 值 |
|------|-----|
| 数据库类型 | PostgreSQL 16 |
| 扩展 | pgvector（向量搜索，用于 AI RAG） |
| Docker 镜像 | pgvector/pgvector:pg16 |
| 默认用户 | csiyj |
| 默认密码 | ****** |
| 默认库名 | csiyj_db |
| 字符集 | UTF-8 |

## 2. 命名规范

### 2.1 表名

| 前缀 | 模块 | 示例 |
|------|------|------|
| `sys_` | system 系统 | sys_user、sys_role、sys_menu |
| `flow_` | 流程引擎 | flow_definition、flow_instance_biz |
| `ai_` | AI 平台 | ai_agent、ai_conversation |
| `gen_` | 代码生成 | gen_table、gen_template |
| `qrtz_` | 定时任务 | qrtz_triggers（Quartz 自动创建） |
| `act_` / `flw_` | Flowable | act_ru_task（Flowable 自动创建） |

- 全部小写 + 下划线分隔（snake_case）
- 新业务模块使用独立前缀，避免与系统表冲突

### 2.2 字段名

- 全部小写 + 下划线分隔（snake_case）
- 主键命名：`{表名去前缀}_id`（如 `user_id`、`role_id`、`menu_id`）
- 外键字段：直接使用关联表的主键名（如 `dept_id`、`post_id`）
- 布尔/状态字段：使用 CHAR(1)，值为 `'0'`/`'1'` 或 `'t'`/`'f'`

### 2.3 主键类型

- 使用 `BIGSERIAL`（自增长 BIGINT）
- 对应 Java 类型 `Long`
- 对应 MyBatis-Plus 注解 `@TableId(type = IdType.AUTO)`

## 3. 必须字段

每张业务表**必须**包含以下字段：

```sql
create_by     VARCHAR(64)  DEFAULT '',
create_time   TIMESTAMP    DEFAULT now(),
update_by     VARCHAR(64)  DEFAULT '',
update_time   TIMESTAMP    DEFAULT now(),
del_flag      CHAR(1)      NOT NULL DEFAULT '0',   -- 0=正常, 1=已删除
tenant_id     BIGINT       NOT NULL DEFAULT 1       -- 租户 ID
```

**Java Entity 侧：** 继承 `AuditableEntity`（`com.smart.common.data.domain.AuditableEntity`）即可自动包含这些字段，配合 `AutoFillMetaObjectHandler` 自动填充。

## 4. 禁止项

| 禁止 | 替代方案 |
|------|----------|
| TIMESTAMPTZ（带时区时间戳） | 使用 TIMESTAMP，时区在应用层处理 |
| 数据库外键约束 | 在应用层维护关联关系 |
| 存储过程 | 在 Java Service 层实现业务逻辑 |
| MySQL 特有语法 | 仅使用 PostgreSQL 兼容语法 |

## 5. 菜单数据写入规范（核心重点）

### 5.1 sys_menu 表结构

```sql
CREATE TABLE "public"."sys_menu" (
  "menu_id"      INT8         NOT NULL,             -- 菜单 ID（BIGINT）
  "menu_name"    VARCHAR(128) NOT NULL,             -- 菜单名称
  "permission"   VARCHAR(128),                      -- 权限标识（按钮才填）
  "path"         VARCHAR(255),                      -- 路由路径
  "component"    VARCHAR(255),                      -- 组件路径
  "parent_id"    INT8         NOT NULL DEFAULT 0,   -- 父菜单 ID（0=顶级）
  "icon"         VARCHAR(128),                      -- 图标名称
  "sort_order"   INT4         NOT NULL DEFAULT 0,   -- 排序号
  "menu_type"    CHAR(1)      NOT NULL DEFAULT '0', -- 类型：0=目录, 1=菜单, 2=按钮
  "keep_alive"   CHAR(1)      NOT NULL DEFAULT 'f', -- 是否缓存：t/f
  "visible"      CHAR(1)      NOT NULL DEFAULT 't', -- 是否可见：t/f
  "del_flag"     CHAR(1)      NOT NULL DEFAULT '0', -- 逻辑删除：0/1
  "create_time"  TIMESTAMP(6) DEFAULT now(),
  "update_time"  TIMESTAMP(6) DEFAULT now(),
  "tenant_id"    INT8         NOT NULL DEFAULT 1    -- 租户 ID
);
```

### 5.2 ID 分段规则（最重要！）

为避免不同模块的菜单 ID 冲突，每个业务模块**独占一个 ID 段**：

| ID 段 | 模块 | 已分配 |
|-------|------|--------|
| [1, 99) | 系统基础（用户、角色、菜单等） | 是 |
| [100, 199) | 系统管理（字典、参数、日志等） | 是 |
| [200, 299) | 代码生成 | 是 |
| [300, 399) | 定时任务 | 是 |
| [400, 499) | 文件管理 | 是 |
| **[500, 599)** | **流程管理** | 是 |
| **[600, 699)** | **表单管理** | 是 |
| [700, 799) | AI 平台 | 待分配 |
| [800, 899) | NL2SQL | 待分配 |
| [900, 999) | 业务模块 | 待分配 |

### 5.3 ID 编号规则

以流程管理模块 [500, 599) 为例：

```
500  ── 一级目录（流程管理）         整十数
 ├── 510  ── 二级菜单（流程定义）     整十数
 │    ├── 511  ── 按钮（查询）        菜单 ID + 1
 │    ├── 512  ── 按钮（新建）        菜单 ID + 2
 │    ├── 513  ── 按钮（编辑）        菜单 ID + 3
 │    ├── 514  ── 按钮（删除）        菜单 ID + 4
 │    └── 515  ── 按钮（发布）        菜单 ID + 5
 ├── 520  ── 二级菜单（我的待办）     +10
 │    └── 521  ── 按钮（查看）
 ├── 530  ── 二级菜单（我的已办）     +10
 └── 540  ── 二级菜单（我发起的）     +10
```

**规则总结：**
- **一级目录：** 使用 ID 段的起始整十数（如 500、600）
- **二级菜单：** 在一级目录基础上 +10（如 510、520、530）
- **按钮权限：** 在所属菜单基础上 +1~+5（如 511、512、513）

### 5.4 各字段填写规范

| 字段 | 目录（type=0） | 菜单（type=1） | 按钮（type=2） |
|------|---------------|---------------|---------------|
| menu_name | 模块名称（如"流程管理"） | 页面名称（如"流程定义"） | 操作名称（如"流程定义查询"） |
| permission | 空字符串 `''` | 空字符串 `''` | 权限标识（如 `flow_def_view`） |
| path | 模块路径（如 `/flow`） | 页面路径（如 `/flow/list`） | 空字符串 `''` |
| component | `'LAYOUT'` | 相对路径（如 `flow/list/flow-list`） | 空字符串 `''` |
| parent_id | `0`（顶级） | 一级目录的 menu_id | 所属菜单的 menu_id |
| icon | Element Plus 图标名 | Element Plus 图标名或空 | 空字符串 `''` |
| sort_order | 模块间排序 | 菜单间排序 | 按钮间排序 |
| keep_alive | `'f'` | `'t'` 或 `'f'` | `'f'` |
| visible | `'t'` | `'t'` | `'t'` |

### 5.5 component 字段规则

| 类型 | component 值 | 说明 |
|------|-------------|------|
| 目录 | `'LAYOUT'` | 布局组件（Vue Router 的 Layout 容器） |
| 菜单 | `'flow/list/flow-list'` | 相对于 `src/views/` 的路径，**不带 `.vue` 后缀** |
| 按钮 | `''`（空字符串） | 不渲染组件 |

**注意：** 按钮类型的 `path` 和 `component` 必须是**空字符串**，不是 `NULL`。

### 5.6 permission 字段命名规范

格式：`{模块}_{实体}_{操作}`

| 示例 | 含义 |
|------|------|
| `flow_def_view` | 流程定义-查看 |
| `flow_def_create` | 流程定义-新建 |
| `flow_def_edit` | 流程定义-编辑 |
| `flow_def_delete` | 流程定义-删除 |
| `flow_def_publish` | 流程定义-发布 |
| `flow_task_view` | 待办任务-查看 |
| `form_view` | 表单-查看 |
| `form_create` | 表单-新建 |

### 5.7 完整 SQL 示例

```sql
-- ====================================================
-- 流程管理模块菜单 [500, 599)
-- ====================================================

-- 先删除旧数据（幂等性保证）
DELETE FROM "public"."sys_role_menu" WHERE menu_id BETWEEN 500 AND 599;
DELETE FROM "public"."sys_menu"      WHERE menu_id BETWEEN 500 AND 599;

-- 1. 一级目录：流程管理
INSERT INTO "public"."sys_menu"
  (menu_id, menu_name, permission, path, component, parent_id, icon, sort_order, menu_type, keep_alive, visible, del_flag, create_time, update_time, tenant_id)
VALUES
  (500, '流程管理', '', '/flow', 'LAYOUT', 0, 'Connection', 5, '0', 'f', 't', '0', now(), now(), 1);

-- 2. 二级菜单：流程定义
INSERT INTO "public"."sys_menu" VALUES
  (510, '流程定义', '', '/flow/list', 'flow/list/flow-list', 500, 'Document', 1, '1', 't', 't', '0', now(), now(), 1);

-- 3. 按钮权限：流程定义的操作按钮
INSERT INTO "public"."sys_menu" VALUES
  (511, '流程定义查询', 'flow_def_view',    '', '', 510, '', 0, '2', 'f', 't', '0', now(), now(), 1),
  (512, '流程定义新建', 'flow_def_create',  '', '', 510, '', 1, '2', 'f', 't', '0', now(), now(), 1),
  (513, '流程定义编辑', 'flow_def_edit',    '', '', 510, '', 2, '2', 'f', 't', '0', now(), now(), 1),
  (514, '流程定义删除', 'flow_def_delete',  '', '', 510, '', 3, '2', 'f', 't', '0', now(), now(), 1),
  (515, '流程定义发布', 'flow_def_publish', '', '', 510, '', 4, '2', 'f', 't', '0', now(), now(), 1);

-- 4. 二级菜单：我的待办
INSERT INTO "public"."sys_menu" VALUES
  (520, '我的待办', '', '/flow/task/todo', 'flow/task/todo-list', 500, '', 2, '1', 't', 't', '0', now(), now(), 1);

INSERT INTO "public"."sys_menu" VALUES
  (521, '待办查看', 'flow_task_view', '', '', 520, '', 0, '2', 'f', 't', '0', now(), now(), 1);

-- 5. 授权给超管角色（role_id=1）
INSERT INTO "public"."sys_role_menu" ("role_id", "menu_id", "tenant_id") VALUES
  (1, 500, 1),
  (1, 510, 1), (1, 511, 1), (1, 512, 1), (1, 513, 1), (1, 514, 1), (1, 515, 1),
  (1, 520, 1), (1, 521, 1);

-- 6. 推进序列到下一个空闲段（防止自增 ID 冲突）
SELECT setval('"public"."sys_menu_menu_id_seq"', 600, false);
```

### 5.8 菜单数据写入检查清单

写入菜单数据前，逐项检查：

- [ ] **ID 段是否独占？** 确认使用的 ID 段未被其他模块占用
- [ ] **parent_id 是否正确？** 一级=0，二级=一级ID，按钮=所属菜单ID
- [ ] **menu_type 是否正确？** 0=目录，1=菜单，2=按钮
- [ ] **component 格式是否正确？** 目录=`LAYOUT`，菜单=不带 `.vue` 的路径，按钮=空字符串
- [ ] **按钮的 path 和 component 是否为空字符串？** 不是 NULL
- [ ] **permission 标识是否唯一？** 不与其他模块重复
- [ ] **是否先删后插（幂等性）？** 脚本可重复执行
- [ ] **是否授权给超管？** sys_role_menu 中 role_id=1
- [ ] **是否推进序列？** `SELECT setval()` 推到下一个空闲段
- [ ] **tenant_id 是否为 1？** 默认租户

### 5.9 常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| 菜单不显示 | `visible = 'f'` 或 `del_flag = '1'` | 检查这两个字段 |
| 点击菜单 404 | `component` 路径错误或带了 `.vue` 后缀 | 修正为不带后缀的相对路径 |
| 按钮权限不生效 | `permission` 为空或未在 sys_role_menu 中授权 | 补充权限标识和授权 |
| ID 冲突 | 未使用独占 ID 段或序列未推进 | 使用空闲 ID 段 + setval |
| 菜单顺序混乱 | `sort_order` 值不合理 | 调整排序号 |
| 子菜单不在父菜单下 | `parent_id` 指向错误 | 修正 parent_id |

## 6. 其他表数据写入注意事项

### 6.1 角色数据（sys_role）

- `role_id = 1` 为超级管理员角色，**不可删除**
- 角色 code 建议使用大写英文（如 `ADMIN`、`USER`）
- 新角色的菜单权限通过前端"角色管理"页面配置，不建议在 SQL 中硬编码

### 6.2 字典数据（sys_dict_type + sys_dict_data）

- `dict_type` 必须唯一
- `dict_data` 的 `dict_sort` 决定下拉框选项顺序
- 新增字典后前端需要刷新缓存

### 6.3 OAuth2 客户端数据（sys_oauth_client_details）

- 默认客户端 `client_id = 'smart'`，`client_secret = 'smart'`
- 前端代码中硬编码了 `Basic smart:smart` 的 Authorization 头
- 如果修改客户端凭据，**必须同步修改以下两个前端文件中的 `CLIENT_BASIC_AUTH` 常量**：
  - Web 端：`smart-ui/src/api/auth.ts`
  - App 端：`smart-app/src/api/auth.ts`

### 6.4 租户数据（sys_tenant）

- `tenant_id = 1` 为默认租户
- 新增租户后需要初始化该租户的管理员用户和基础角色
- 所有业务表的 `tenant_id` 默认值为 1

## 7. SQL 脚本编写规范

### 7.1 脚本结构

```sql
-- ====================================================
-- 模块名称
-- 作者 | 日期
-- ====================================================

-- 1. 清理旧数据（幂等性）
DELETE FROM ...;

-- 2. 插入新数据
INSERT INTO ...;

-- 3. 授权给角色
INSERT INTO "public"."sys_role_menu" ...;

-- 4. 推进序列
SELECT setval(...);
```

### 7.2 规范要点

- 所有 SQL 脚本必须**幂等**（先删后插，可重复执行）
- 表名和字段名使用双引号包裹（PostgreSQL 规范）
- 字符串值使用单引号
- 时间戳使用 `now()` 函数
- 脚本文件放在 `smart/db/` 目录下
