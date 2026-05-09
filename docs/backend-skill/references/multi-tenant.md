# 多租户开发

## 架构概述

Smart 采用**共享数据库 + 共享 Schema + 行级隔离**模式，通过 `tenant_id` 字段实现数据隔离。

## 关键组件

### TenantContext — 租户上下文

**包路径：** `com.smart.common.core.tenant.TenantContext`

基于 `TransmittableThreadLocal` 的线程本地上下文，支持三种使用模式：

```java
// 模式 1：手动管理（需要显式 clear）
TenantContext.set(1L);
try {
    // 业务逻辑
} finally {
    TenantContext.clear();
}

// 模式 2：try-with-resources 范围（推荐）
try (TenantContext.Scope scope = TenantContext.scope(1L)) {
    // 业务逻辑
}  // 自动恢复之前的租户

// 模式 3：函数式（推荐）
TenantContext.runAs(1L, () -> {
    // 业务逻辑
});

TenantContext.supplyAs(1L, () -> {
    return someResult;
});
```

**超级管理员绕过：**

```java
// 超级管理员需要查看所有租户数据时
TenantContext.runWithoutTenant(() -> {
    // 此代码块内不追加 tenant_id 条件
});
```

**获取当前租户 ID：**

```java
Long tenantId = TenantContext.get().orElse(null);     // Optional，安全
Long tenantId = TenantContext.require();               // 不存在则抛异常
```

**线程传递：** 使用 `TransmittableThreadLocal`，自动跨线程池、异步回调传播租户 ID。

### SmartTenantInterceptor — 租户 SQL 拦截器

**包路径：** `com.smart.common.data.tenant.SmartTenantInterceptor`

自动为租户表的 SQL 追加 `AND tenant_id = ?` 条件。

**判断租户表的逻辑：**
1. Entity 类标注了 `@TenantEntity` → 是租户表
2. 表名在 `smart.tenant.tables` 配置中 → 是租户表
3. 都不满足 → 不是租户表，不追加条件

**跳过租户过滤的场景：**
- `TenantContext.isBypassed()` 为 `true`（超级管理员模式）
- `TenantContext.get()` 为空（无租户上下文）

### SmartTenantProperties — 租户配置

**配置前缀：** `smart.tenant`

```yaml
smart:
  tenant:
    enabled: true                    # 是否启用租户隔离（默认 true）
    column: tenant_id                # 租户字段名（默认 tenant_id）
    tables:                          # 额外的租户表（@TenantEntity 标注的表自动包含）
      - sys_user
      - sys_role
      - sys_dept
    super-admin-tenant-ids:          # 超级管理员租户 ID
      - 1
```

## Entity 配置

### 方式 1：注解（推荐）

```java
@Data
@TableName("sys_user")
@TenantEntity              // ← 标记此表需要租户隔离
public class SysUser extends AuditableEntity {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private Long tenantId;   // ← 必须有此字段
    // ...
}
```

### 方式 2：配置列表

对于没有 Entity 类的表，在 YAML 中配置：

```yaml
smart:
  tenant:
    tables:
      - some_legacy_table
```

## 新增租户隔离表 Checklist

1. Entity 类添加 `@TenantEntity` 注解
2. 数据库表添加 `tenant_id BIGINT NOT NULL DEFAULT 1` 列
3. 如果继承 `AuditableEntity`，确保 `tenantId` 字段存在
4. 不想在某个查询中追加租户条件时，使用 `TenantContext.runWithoutTenant()`

## 常见问题

### Q: 新增的表数据查不到？
A: 检查 Entity 是否标注了 `@TenantEntity`，或表名是否在 `smart.tenant.tables` 配置中。如果没有，SmartTenantInterceptor 不会追加 `tenant_id` 条件，但也不会过滤——可能是前端没传租户 ID 导致 `TenantContext` 为空。

### Q: 超级管理员需要查看所有租户数据？
A: 使用 `TenantContext.runWithoutTenant(() -> { ... })` 包裹查询代码。

### Q: 异步线程中租户上下文丢失？
A: `TenantContext` 使用 `TransmittableThreadLocal`，只要使用 `TtlRunnable` 或 `TtlCallable` 包装任务即可自动传播。如果使用 `@Async`，确保线程池使用了 `TtlExecutors` 包装。
