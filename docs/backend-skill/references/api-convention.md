# 统一响应格式与 API 命名规范

## ApiResult 统一响应

所有 Controller 方法返回 `ApiResult<T>`（`com.smart.common.core.web.ApiResult`）。

```java
public class ApiResult<T> {
    private final boolean success;    // 布尔标志（非数字码）
    private final String msg;
    private final T data;
    private final String errorCode;   // 机器可读错误码（如 "USER_NOT_FOUND"），仅失败时存在
    private final String traceId;     // 自动从 MDC 获取
    private final long timestamp;     // 请求级时间戳
}
```

**工厂方法：**

```java
// 成功
ApiResult.success()                    // 无数据成功
ApiResult.success(data)                // 带数据成功
ApiResult.success(data, "自定义消息")   // 带数据和消息

// 失败
ApiResult.failure("用户名或密码错误")                    // 仅消息
ApiResult.failure("USER_NOT_FOUND", "用户不存在")       // 错误码+消息
```

**前端兼容：** `ApiResult` 提供 `getCode()` 方法，`success=true` 返回 `0`，`failure` 返回 `1`，保持前端向后兼容。OAuth2 Token 端点（`/oauth2/token`）返回标准 RFC 6749 格式，不走 ApiResult 包装。

## URL 命名规范

| 操作 | 方法 | URL | 示例 |
|------|------|-----|------|
| 分页查询 | GET | `/entity/page` | `GET /user/page?current=1&size=10` |
| 单条查询 | GET | `/entity/{id}` | `GET /user/1` |
| 新增 | POST | `/entity` | `POST /user` |
| 修改 | PUT | `/entity` | `PUT /user` |
| 删除 | DELETE | `/entity/{id}` | `DELETE /user/1` |
| 批量删除 | DELETE | `/entity` | body: id[] |
| 列表查询 | GET | `/entity/list` | `GET /role/list` |
| 树形查询 | GET | `/entity/tree` | `GET /menu/tree` |

## 错误码体系

错误码使用大写下划线格式，按领域分类：

| 模式 | 示例 | 说明 |
|------|------|------|
| 实体未找到 | `USER_NOT_FOUND` | 单条查询无数据 |
| 业务校验失败 | `PASSWORD_MISMATCH` | 业务规则校验 |
| 权限不足 | `ACCESS_DENIED` | 未授权操作 |
| 重复操作 | `DUPLICATE_SUBMISSION` | 幂等拦截 |

抛出业务异常时使用：

```java
throw new BusinessException("USER_NOT_FOUND", "用户不存在");
```

`BusinessException`（`com.smart.common.core.exception.BusinessException`）携带 `errorCode` 字符串字段，`GlobalExceptionHandler` 会自动包装为 `ApiResult.failure(errorCode, message)`。
