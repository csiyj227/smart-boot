# Smart 部署运维知识库

> 本文档涵盖 Smart 平台的部署架构、环境搭建、服务启动和运维操作指南。

## 1. 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行时 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| PostgreSQL | 16 (pgvector 镜像) | 数据库 + 向量存储 |
| Redis | 7.x | 缓存 / 会话 / Token 存储 |
| Nacos | 2.x | 注册中心 / 配置中心（微服务模式） |

## 2. Docker 基础设施

### 2.1 当前 docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: pgvector-csiyj
    environment:
      POSTGRES_USER: csiyj
      POSTGRES_PASSWORD: ******
      POSTGRES_DB: csiyj_db
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  pgdata:
```

**说明：**
- 使用 `pgvector/pgvector:pg16` 镜像（内含 pgvector 扩展，支持 AI RAG 向量搜索）
- 默认用户/密码/库：`csiyj` / `******` / `csiyj_db`
- 数据持久化到 Docker volume `pgdata`

### 2.2 启动基础设施

```bash
cd smart/docker
docker-compose up -d
```

### 2.3 完整部署建议

当前 docker-compose 仅包含 PostgreSQL。完整的生产部署还需要：

| 服务 | 建议镜像 | 端口 |
|------|----------|------|
| PostgreSQL | pgvector/pgvector:pg16 | 5432 |
| Redis | redis:7-alpine | 6379 |
| Nacos | nacos/nacos-server:v2.x | 8848 |
| smart-gateway | 自行打包 | 8080 |
| smart-auth | 自行打包 | 9200 |
| smart-system | 自行打包 | 9201 |
| smart-flow | 自行打包 | 5008 |
| smart-ai | 自行打包 | 5009 |
| smart-ui | nginx:alpine | 80 |

## 3. 数据库初始化

### 3.1 SQL 脚本执行顺序

```bash
cd smart/db

# 1. 基础表结构（用户、角色、菜单、字典等系统表）
psql -h localhost -U csiyj -d csiyj_db -f public.sql

# 2. 流程引擎表
psql -h localhost -U csiyj -d csiyj_db -f flow.sql

# 3. 表单模块表
psql -h localhost -U csiyj -d csiyj_db -f form.sql

# 4. 流程/表单菜单数据
psql -h localhost -U csiyj -d csiyj_db -f flow_menu.sql

# 5. 其他业务 SQL（按需执行）
```

**注意事项：**
- `public.sql` 必须最先执行，包含所有基础系统表
- `flow.sql` 和 `form.sql` 依赖 `public.sql` 中的基础表
- `flow_menu.sql` 包含菜单数据和角色授权，具有幂等性（先删后插）
- Flowable 引擎表（`act_*` / `flw_*`）由 Flowable 自动创建，无需手动执行

### 3.2 连接配置

| 参数 | 默认值 | 环境变量 |
|------|--------|----------|
| Host | localhost | `DB_HOST` |
| Port | 5432 | `DB_PORT` |
| Database | csiyj_db | `DB_NAME` |
| Username | csiyj | `DB_USER` |
| Password | ****** | `DB_PASS` |

## 4. 后端启动

### 4.1 微服务模式

**构建：**
```bash
cd smart
mvn clean package -P cloud -DskipTests
```

**按顺序启动（必须按此顺序）：**

```bash
# 1. 注册中心（如果使用内嵌 Nacos）
java -jar smart-register/target/smart-register.jar

# 2. 认证服务
java -jar smart-auth/target/smart-auth.jar

# 3. API 网关
java -jar smart-gateway/target/smart-gateway.jar

# 4. 用户权限管理
java -jar smart-system/smart-system-biz/target/smart-system-biz.jar

# 5. 流程引擎（可选）
java -jar smart-flow/smart-flow-biz/target/smart-flow-biz.jar

# 6. 代码生成（可选）
java -jar smart-codegen/smart-codegen-biz/target/smart-codegen-biz.jar

# 7. AI 平台（可选）
java -jar smart-ai/smart-ai-biz/target/smart-ai-biz.jar

# 8. NL2SQL（可选）
java -jar smart-nl2sql/smart-nl2sql-biz/target/smart-nl2sql-biz.jar
```

**启动顺序说明：**
1. Nacos 必须最先启动（所有服务依赖它注册和拉取配置）
2. Auth 需要在 Gateway 之前启动（Gateway 需要转发认证请求）
3. Gateway 在 Auth 之后启动（作为统一入口）
4. system 在 Auth 之后启动（Auth 通过 Feign 调用 system 获取用户信息）
5. 其他服务无顺序要求

### 4.2 单体模式

**构建：**
```bash
cd smart
mvn clean package -P boot -DskipTests
```

**启动（单个 JAR）：**
```bash
java -jar smart-boot/target/smart-boot.jar
```

单体模式下所有服务运行在一个进程中，端口 8080。

### 4.3 环境变量

通过环境变量覆盖默认配置：

```bash
# 数据库
export DB_HOST=192.168.1.100
export DB_PORT=5432
export DB_NAME=smart_prod
export DB_USER=smart
export DB_PASS=your_password

# Redis
export REDIS_HOST=192.168.1.100
export REDIS_PORT=6379

# Nacos（微服务模式）
export NACOS_HOST=192.168.1.100
export NACOS_PORT=8848
```

## 5. 前端部署

### 5.1 Web 端开发模式

```bash
cd smart/smart-ui
npm install
npm run dev    # http://localhost:8888
```

### 5.2 Web 端生产构建

```bash
cd smart/smart-ui
npm run build    # 输出到 dist/
```

生产环境使用 Nginx 托管 `dist/` 静态文件，并配置反向代理：

```nginx
server {
    listen 80;
    server_name smart.example.com;

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://gateway:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /file/ {
        proxy_pass http://gateway:8080/file/;
    }
}
```

### 5.3 App 端开发模式

```bash
cd smart/smart-app
npm install
npm run dev:h5           # H5：http://localhost:9000
npm run dev:mp-weixin    # 微信小程序：使用微信开发者工具打开 dist/dev/mp-weixin
```

### 5.4 App 端生产构建

```bash
npm run build:h5         # H5 产物
npm run build:mp-weixin  # 小程序产物
```

## 6. 默认账号

| 账号 | 密码 | 角色 | 说明 |
|------|------|------|------|
| admin | smart123 | 超级管理员 | 拥有所有权限 |

## 7. 服务端口汇总

| 服务 | 端口 | 模式 |
|------|------|------|
| smart-register (Nacos) | 8848 | 微服务 |
| smart-auth | 9200 | 微服务 |
| smart-gateway | 8080 | 微服务 |
| smart-system | 9201 | 微服务 |
| smart-flow | 5008 | 微服务 |
| smart-ai | 5009 | 微服务 |
| smart-boot | 8080 | 单体 |
| smart-ui (dev) | 8888 | 前端开发 |
| smart-app (dev) | 9000 | App 开发 |
| PostgreSQL | 5432 | 基础设施 |
| Redis | 6379 | 基础设施 |

## 8. 健康检查

微服务模式下各服务提供 Spring Boot Actuator 健康端点：

```bash
curl http://localhost:9201/actuator/health
```

Nacos 控制台查看服务注册状态：`http://localhost:8848/nacos`（默认账号 nacos/nacos）
