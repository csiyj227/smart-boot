# 编码规范

## 分层架构

```
smart-system-api/                    ← API 层：DTO、Feign 接口
  └─ com.smart.admin.api
       ├─ dto/                        ← 数据传输对象（入参/出参）
       │    ├─ UserInfo.java
       │    └─ UserForm.java
       └─ feign/                      ← Feign 远程调用接口
            └─ RemoteUserService.java

smart-system-biz/                    ← 业务实现层
  └─ com.smart.admin
       ├─ entity/                     ← 实体类（对应数据库表）
       │    ├─ SysUser.java
       │    └─ SysRole.java
       ├─ mapper/                     ← MyBatis-Plus Mapper 接口
       │    └─ SysUserMapper.java
       ├─ service/                    ← Service 接口
       │    ├─ SysUserService.java
       │    └─ impl/                  ← Service 实现
       │         └─ SysUserServiceImpl.java
       └─ controller/                 ← REST Controller
            └─ SysUserController.java
```

**规则：**
- API 模块只放 DTO 和 Feign 接口，不放业务逻辑
- BIZ 模块放实体、Mapper、Service、Controller
- 禁止跨模块直接依赖 BIZ 模块，必须通过 API 模块的 Feign 接口调用

## Entity 规范

### 基类

所有需要审计字段的实体继承 `AuditableEntity`（`com.smart.common.data.domain.AuditableEntity`）：

```java
@Data
public class AuditableEntity implements Serializable {
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private String delFlag = "0";
}
```

**自动填充：** `AutoFillMetaObjectHandler` 自动填充 `createBy`、`createTime`、`updateBy`、`updateTime`、`delFlag`。`createBy`/`updateBy` 从 `X-Username` 请求头获取当前用户名，获取不到时默认 `"system"`。

### Entity 模板

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@TenantEntity                    // ← 多租户表必须标注
public class SysUser extends AuditableEntity {

    @TableId(type = IdType.AUTO)  // ← 自增主键
    private Long userId;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)  // ← 密码字段防序列化泄露
    private String password;

    private Long tenantId;        // ← 租户隔离字段

    // 瞬时字段（非数据库列）
    @TableField(exist = false)
    private String deptName;
}
```

**命名规则：**
- Entity 类名：`Sys` + 业务名（如 `SysUser`、`SysRole`、`SysDept`）
- 表名：下划线分隔（如 `sys_user`、`sys_role`）
- 主键字段：实体名去 `Sys` 前缀 + `Id`（如 `userId`、`roleId`）
- 租户字段：`tenantId`（`Long` 类型）

## Service 规范

### Service 接口

```java
public interface SysUserService extends IService<SysUser> {

    SysUser findByUsernameAndTenant(String username, Long tenantId);

    Set<String> getPermissionsByUserId(Long userId);

    void saveUserWithRoles(SysUser user, List<Long> roleIds);
}
```

**规则：**
- 继承 MyBatis-Plus 的 `IService<T>`，获得基础 CRUD
- 接口方法命名：`findByXxx`（查询）、`saveXxx`（新增）、`updateXxx`（修改）、`deleteXxx`（删除）

### Service 实现

```java
@Service
@RequiredArgsConstructor                      // ← 构造器注入，禁止 @Autowired 字段注入
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {

    private final SysMenuMapper menuMapper;   // ← 构造器注入
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)  // ← 写操作必须加事务
    public void saveUserWithRoles(SysUser user, List<Long> roleIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        save(user);
        saveUserRoles(user.getUserId(), roleIds);
    }
}
```

**规则：**
- 继承 `ServiceImpl<Mapper, Entity>`
- 使用 `@RequiredArgsConstructor` + `private final` 构造器注入
- 写操作方法加 `@Transactional(rollbackFor = Exception.class)`
- 查询用 `LambdaQueryWrapper`，禁止拼接字符串 SQL

## Controller 规范

```java
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    // ── 分页查询 ──────────────────────────────────
    @PreAuthorize("@authz.hasPermission('sys_user_view')")
    @GetMapping("/page")
    public ApiResult<Page<SysUser>> page(Page<SysUser> page, SysUser query) {
        return ApiResult.success(sysUserService.page(page, Wrappers.<SysUser>lambdaQuery()
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .eq(StringUtils.hasText(query.getStatus()), SysUser::getStatus, query.getStatus())));
    }

    // ── 单条查询 ──────────────────────────────────
    @PreAuthorize("@authz.hasPermission('sys_user_view')")
    @GetMapping("/{userId}")
    public ApiResult<SysUser> getById(@PathVariable Long userId) {
        return ApiResult.success(sysUserService.getById(userId));
    }

    // ── 新增 ──────────────────────────────────────
    @PreAuthorize("@authz.hasPermission('sys_user_add')")
    @AuditTrace("新增用户")
    @PostMapping
    public ApiResult<Void> save(@Valid @RequestBody UserForm form) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(form, user);
        sysUserService.saveUserWithRoles(user, form.getRoleIds());
        return ApiResult.success();
    }

    // ── 修改 ──────────────────────────────────────
    @PreAuthorize("@authz.hasPermission('sys_user_edit')")
    @AuditTrace("修改用户")
    @PutMapping
    public ApiResult<Void> update(@Valid @RequestBody UserForm form) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(form, user);
        sysUserService.updateUserWithRoles(user, form.getRoleIds());
        return ApiResult.success();
    }

    // ── 删除 ──────────────────────────────────────
    @PreAuthorize("@authz.hasPermission('sys_user_del')")
    @AuditTrace("删除用户")
    @DeleteMapping("/{userId}")
    public ApiResult<Void> delete(@PathVariable Long userId) {
        sysUserService.deleteUserWithRoles(userId);
        return ApiResult.success();
    }

    // ── 内部服务调用 ───────────────────────────────
    @ServiceApi
    @GetMapping("/info/{username}")
    public ApiResult<UserInfo> info(@PathVariable String username,
                                    @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId) {
        SysUser user = sysUserService.findByUsernameAndTenant(username, tenantId);
        if (user == null) {
            return ApiResult.failure("User not found");
        }
        // ...
    }
}
```

**规则清单：**
- ⛔ 禁止 `@Autowired` 字段注入，使用 `@RequiredArgsConstructor` 构造器注入
- ⛔ 所有方法返回 `ApiResult<T>`，OAuth2 Token 端点除外
- 权限控制使用 `@PreAuthorize("@authz.hasPermission('xxx')")`
- 审计日志使用 `@AuditTrace("操作描述")`
- 内部服务调用接口使用 `@ServiceApi`
- 入参校验使用 `@Valid` + JSR 303 注解
- 查询参数用对象绑定（如 `SysUser query`），不用 `@RequestParam` 逐个声明

## Feign 接口规范

```java
@FeignClient(contextId = "remoteUserService", value = "smart-system")
public interface RemoteUserService {

    @GetMapping("/user/info/{username}")
    ApiResult<UserInfo> info(@PathVariable("username") String username,
                     @RequestHeader(AuthHeaders.TENANT_ID) Long tenantId,
                     @RequestHeader(AuthHeaders.SERVICE_CALL) String from);
}
```

**规则：**
- `contextId` 必须指定，避免同一服务多个 Feign Client 冲突
- `value` = 目标服务名（如 `smart-system`）
- 必须传 `@RequestHeader(AuthHeaders.SERVICE_CALL) String from`，由 `ServiceCallInterceptor` 自动填充
- 多租户场景必须传 `@RequestHeader(AuthHeaders.TENANT_ID) Long tenantId`
- 返回值类型为 `ApiResult<T>`

## Mapper 规范

```java
public interface SysUserMapper extends BaseMapper<SysUser> {
    // 复杂查询写在 XML 中，简单查询用 LambdaQueryWrapper
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
```

**规则：**
- 继承 `BaseMapper<T>`，获得基础 CRUD
- 简单单表查询用 `LambdaQueryWrapper`，写在 Service 层
- 复杂多表关联查询写在 Mapper XML（`resources/mapper/` 目录）
- 禁止在 Java 代码中拼接 SQL 字符串

## DTO 规范

```java
@Data
public class UserForm {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String realName;
    private String phone;
    private Long deptId;
    private List<Long> roleIds;
}
```

**规则：**
- 位于 API 模块的 `dto` 包
- 使用 JSR 303 校验注解（`@NotBlank`、`@Size`、`@Pattern` 等）
- 前端入参用 `XxxForm`，出参用 `XxxInfo`/`XxxVO`
