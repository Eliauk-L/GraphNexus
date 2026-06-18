# GraphNexus Podman 部署指南

> 版本：v2.0 | 日期：2026-06-18
>
> 适用范围：本地开发 / MVP 单机部署（适配 4 GiB Podman Machine）
>
> 架构：三层编排 — 基础设施层 → 应用集群层（双副本） → Nginx 网关层

---

## 一、前置条件

| 依赖 | 最低版本 | 验证命令 |
|------|---------|---------|
| **Podman** | 4.x+ | `podman version` |
| **podman-compose** | 1.x+ | `podman-compose version` |
| **Podman Machine**（macOS） | — | `podman machine list` |
| **JDK 17**（本地编译） | 17+ | `java -version` |
| **Maven**（本地编译） | 3.9+ | `mvn -version` |

### macOS 初始化

```bash
# 推荐 4 GiB+ 内存（含 2 个应用副本）
podman machine stop
podman machine set --memory 4096 --cpus 6
podman machine start

# 首次创建
podman machine init --cpus 6 --memory 4096 --disk-size 50
podman machine start

# 验证
podman machine ssh -- free -h
```

---

## 二、架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│  podman-compose.yml（基础设施层）                                  │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────┐   │
│  │ Neo4j   │ │ MySQL   │ │ Redis   │ │ RabbitMQ │ │ MinIO   │   │
│  │ :7687   │ │ :3306   │ │ :6379   │ │ :5672    │ │ :9000   │   │
│  └─────────┘ └─────────┘ └─────────┘ └──────────┘ └─────────┘   │
│                         graphnexus-net                            │
└──────────────────────────────┬───────────────────────────────────┘
                               │
┌──────────────────────────────┴───────────────────────────────────┐
│  deployment/podman-compose.app.yml（应用集群层）                    │
│  ┌──────────────────────┐  ┌──────────────────────┐              │
│  │ graphnexus-app-1     │  │ graphnexus-app-2     │              │
│  │ host→:8081, pod:8080 │  │ host→:8082, pod:8080 │              │
│  └──────────┬───────────┘  └──────────┬───────────┘              │
└─────────────┼──────────────────────────┼──────────────────────────┘
              │                          │
┌─────────────┴──────────────────────────┴──────────────────────────┐
│  deployment/podman-compose.nginx.yml（网关层）                     │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │ Nginx :8080  ──least_conn──▶  graphnexus-app-1:8080         │  │
│  │                              graphnexus-app-2:8080          │  │
│  └─────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 三、目录结构

```
GraphNexus/
├── podman-compose.yml                    # 基础设施编排（Neo4j/MySQL/Redis/RabbitMQ/MinIO）
├── Containerfile                         # 多阶段构建（→ deployment/Containerfile 亦可）
├── .env                                  # 环境变量（密钥，不提交 Git）
├── .env.example                          # 环境变量模板
├── .dockerignore                         # 构建上下文排除规则
├── pom.xml
├── src/
│   └── main/resources/
│       ├── application.yml               # 基础配置（profile: dev）
│       ├── application-dev.yml           # 开发环境（localhost）
│       └── application-prod.yml          # 容器化生产环境（服务名）
└── deployment/
    ├── deploy.md                         # 本文档
    ├── Containerfile                     # 多阶段构建（备用位置）
    ├── podman-compose.app.yml            # 应用集群编排（2 副本）
    ├── podman-compose.nginx.yml          # Nginx 网关编排
    ├── init-sql/                         # MySQL 初始化脚本
    ├── nginx/
    │   ├── nginx.conf                    # Nginx 主配置（upstream + 日志）
    │   └── conf.d/
    │       ├── graphnexus.conf           # API 虚拟主机（分级超时 + Swagger）
    │       └── proxy-headers.inc         # 共享反向代理头
    ├── rabbitmq/
    │   ├── rabbitmq.conf                 # RabbitMQ 配置
    │   └── definitions.json              # 队列/交换机/绑定预定义
    └── redis/
        └── redis.conf                    # Redis 配置
```

---

## 四、快速启动（四步）

### 4.1 准备环境变量

```bash
cp .env.example .env
# 编辑 .env，填入真实 API Key：
#   LLM_API_KEY=sk-your-key-here
#   MINERU_API_TOKEN=your-token-here
source .env
```

### 4.2 第一步：启动基础设施

```bash
podman compose up -d

# 等待所有服务 healthy（约 60-90 秒）
watch -n 2 'podman compose ps'

# 验证
curl -s http://localhost:7474 | head -1          # Neo4j
echo "SELECT 1" | podman exec -i graphnexus-mysql mysql -ugraphnexus -pgraphnexus123 graphnexus
echo "PING" | podman exec -i graphnexus-redis redis-cli -a graphnexus123
podman exec graphnexus-rabbitmq rabbitmq-diagnostics check_running
curl -s http://localhost:9000/minio/health/live   # MinIO
```

### 4.3 第二步：构建应用镜像

```bash
# 编译 fat JAR
mvn package -DskipTests -q

# 构建镜像（多阶段构建，--format docker 确保 HEALTHCHECK 生效）
podman build --format docker -t graphnexus-app:0.1.0 -f deployment/Containerfile .

# 验证
podman images graphnexus-app:0.1.0
```

### 4.4 第三步：启动应用集群（双副本）

```bash
podman compose -f deployment/podman-compose.app.yml up -d

# 等待 healthy（约 90s）
podman compose -f deployment/podman-compose.app.yml ps

# 验证两个副本
curl -s http://localhost:8081/actuator/health   # 副本 1
curl -s http://localhost:8082/actuator/health   # 副本 2
```

### 4.5 第四步：启动 Nginx 网关

```bash
podman compose -f deployment/podman-compose.nginx.yml up -d

# 验证
curl -s http://localhost:8080/health
```

### 4.6 一键验证

```bash
# Nginx 网关健康
curl http://localhost:8080/health

# Swagger UI 接口文档
open http://localhost:8080/swagger-ui.html

# 业务 API
curl -s http://localhost:8080/api/v1/llm/ping -X POST

# OpenAPI JSON
curl -s http://localhost:8080/v3/api-docs | head -20

# 两个副本各自健康
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
```

---

## 五、服务访问入口

### 5.1 通过 Nginx 网关（推荐）

| 路径 | 说明 |
|------|------|
| `http://localhost:8080/` | Nginx 状态页 |
| `http://localhost:8080/health` | 网关健康检查 |
| `http://localhost:8080/swagger-ui.html` | **Swagger UI 接口文档** |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.0 JSON |
| `http://localhost:8080/actuator/health` | 应用健康（负载均衡到两副本） |
| `http://localhost:8080/actuator/prometheus` | Prometheus 指标 |
| `http://localhost:8080/api/v1/document/upload` | 文档上传 |
| `http://localhost:8080/api/v1/query/ask` | 智能问答 |

### 5.2 管理面板（通过 Nginx）

| 面板 | URL | 认证 |
|------|-----|------|
| **Neo4j Browser** | `http://localhost:8080/neo4j/` | `neo4j` / `graphnexus123` |
| **RabbitMQ UI** | `http://localhost:8080/rabbitmq/` | `graphnexus` / `graphnexus123` |
| **MinIO Console** | `http://localhost:8080/minio/` | `minioadmin` / `minioadmin123` |

### 5.3 直连服务（调试用）

| 服务 | 地址 | 认证 |
|------|------|------|
| Neo4j Bolt | `bolt://localhost:7687` | `neo4j` / `graphnexus123` |
| MySQL | `localhost:3306` | `graphnexus` / `graphnexus123` |
| Redis | `localhost:6379` | `graphnexus123` |
| RabbitMQ AMQP | `localhost:5672` | `graphnexus` / `graphnexus123` |
| MinIO S3 | `localhost:9000` | `minioadmin` / `minioadmin123` |
| 应用副本 1 | `localhost:8081` | — |
| 应用副本 2 | `localhost:8082` | — |

---

## 六、常用操作

### 6.1 停止服务

```bash
# 停止网关
podman compose -f deployment/podman-compose.nginx.yml down

# 停止应用集群
podman compose -f deployment/podman-compose.app.yml down

# 停止基础设施（保留数据卷）
podman compose down

# 停止并删除所有数据卷（⚠️ 不可逆）
podman compose down -v
```

### 6.2 重启单个服务

```bash
# 基础设施
podman compose restart neo4j
podman compose restart mysql
podman compose restart redis
podman compose restart rabbitmq
podman compose restart minio

# 应用副本
podman compose -f deployment/podman-compose.app.yml restart graphnexus-app-1
podman compose -f deployment/podman-compose.app.yml restart graphnexus-app-2

# Nginx 网关（或热重载配置）
podman compose -f deployment/podman-compose.nginx.yml restart nginx
# 热重载（不中断连接）
podman exec graphnexus-nginx nginx -s reload
```

### 6.3 查看日志

```bash
# 基础设施
podman compose logs -f --tail 100
podman compose logs -f neo4j

# 应用
podman compose -f deployment/podman-compose.app.yml logs -f
podman compose -f deployment/podman-compose.app.yml logs -f graphnexus-app-1

# Nginx
podman compose -f deployment/podman-compose.nginx.yml logs -f
```

### 6.4 进入容器调试

```bash
# 基础设施
podman exec -it graphnexus-neo4j bash
podman exec -it graphnexus-mysql bash
podman exec -it graphnexus-redis sh
podman exec -it graphnexus-rabbitmq bash
podman exec -it graphnexus-minio sh

# 应用
podman exec -it graphnexus-app-1 bash
podman exec -it graphnexus-app-2 bash

# Nginx
podman exec -it graphnexus-nginx sh
```

### 6.5 更新应用

```bash
# 重新编译 + 构建镜像
mvn package -DskipTests -q
podman build --format docker -t graphnexus-app:0.1.0 -f deployment/Containerfile .

# 滚动重启（逐个替换，减少停机）
podman compose -f deployment/podman-compose.app.yml restart graphnexus-app-1
# 等待健康检查通过...
podman compose -f deployment/podman-compose.app.yml restart graphnexus-app-2
```

### 6.6 Nginx 配置热重载

```bash
# 修改配置后验证
podman exec graphnexus-nginx nginx -t

# 热重载（不中断现有连接）
podman exec graphnexus-nginx nginx -s reload
```

### 6.7 数据备份

```bash
# Neo4j — 在线备份
podman exec graphnexus-neo4j neo4j-admin database dump neo4j --to-path=/backup/
podman cp graphnexus-neo4j:/backup ./backup/neo4j/

# MySQL — 逻辑备份
podman exec graphnexus-mysql mysqldump -ugraphnexus -pgraphnexus123 graphnexus \
  > ./backup/mysql/graphnexus_$(date +%Y%m%d%H%M).sql

# MinIO — 通过 mc 客户端备份
podman exec graphnexus-minio mc mirror /data/ ./backup/minio/
```

### 6.8 重置数据

```bash
# 停止所有服务并清空数据卷
podman compose -f deployment/podman-compose.nginx.yml down
podman compose -f deployment/podman-compose.app.yml down
podman compose down -v

# 重新启动（自动重建所有数据）
podman compose up -d
podman compose -f deployment/podman-compose.app.yml up -d
podman compose -f deployment/podman-compose.nginx.yml up -d
```

---

## 七、Nginx 负载均衡与超时策略

### 7.1 负载均衡

| 策略 | 说明 |
|------|------|
| `least_conn` | 新请求分配给当前活跃连接数最少的后端 |
| `weight=1` | 两副本等权重 |
| `max_fails=3` | 3 次失败后标记为不可用 |
| `fail_timeout=30s` | 30 秒后重新探测 |
| `keepalive 32` | 到每个后端保持 32 个空闲长连接 |

### 7.2 端点超时分级

| 级别 | 超时 | 端点 |
|------|------|------|
| 超长 | `600s` | `/api/v1/graph/fusion/*` — 全图融合 |
| 长 | `300s` | `/api/v1/document/{id}/process` — PDF 解析 |
| | | `/api/v1/graph/extract/*` — LLM 知识图谱抽取 |
| 上传 | `120s` | `/api/v1/document/upload` — 文件上传（100MB） |
| 标准 | `60s` | 其余所有 `/api/*` — 查询/问答/CRUD/指标 |
| 健康 | `10s` | `/actuator/*` — 应用健康检查 |

---

## 八、环境变量参考

完整变量列表见 [.env.example](../.env.example)。

### 8.1 基础设施凭证（与 podman-compose.yml 保持一致）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `NEO4J_USERNAME` | `neo4j` | Neo4j 用户名 |
| `NEO4J_PASSWORD` | `graphnexus123` | Neo4j 密码 |
| `MYSQL_USERNAME` | `graphnexus` | MySQL 用户名 |
| `MYSQL_PASSWORD` | `graphnexus123` | MySQL 密码 |
| `REDIS_PASSWORD` | `graphnexus123` | Redis 密码 |
| `RABBITMQ_USERNAME` | `graphnexus` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `graphnexus123` | RabbitMQ 密码 |
| `RABBITMQ_VHOST` | `graphnexus` | RabbitMQ 虚拟主机 |
| `MINIO_ROOT_USER` | `minioadmin` | MinIO 用户名 |
| `MINIO_ROOT_PASSWORD` | `minioadmin123` | MinIO 密码 |
| `MINIO_BUCKET` | `graphnexus-prod` | MinIO Bucket 名称 |

### 8.2 外部 API 密钥

| 变量 | 说明 |
|------|------|
| `LLM_API_KEY` | DeepSeek API Key（阿里云百炼），必填 |
| `MINERU_API_TOKEN` | MinerU PDF 解析 Token，必填 |

---

## 九、资源配置参考

| 容器 | 镜像 | 内存上限 | 磁盘建议 |
|------|------|---------|---------|
| Neo4j | `neo4j:5.26-community` | 512 MiB | 5 GiB |
| MySQL | `mysql:8.0` | 256 MiB | 5 GiB |
| Redis | `redis:7-alpine` | 64 MiB | 500 MiB |
| RabbitMQ | `rabbitmq:3.13-management-alpine` | 128 MiB | 1 GiB |
| MinIO | `minio/minio:latest` | 128 MiB | 5 GiB |
| graphnexus-app ×2 | `graphnexus-app:0.1.0` | 512 MiB ×2 | — |
| Nginx | `nginx:stable-alpine` | 32 MiB | — |
| **合计** | — | **~2.0 GiB** | **~17 GiB** |

---

## 十、故障排查

### 10.1 Podman Machine 内存不足（macOS）

```bash
podman machine inspect | grep -i memory
podman machine stop
podman machine set --memory 4096 --cpus 6
podman machine start
podman machine ssh -- free -h
```

### 10.2 容器 OOMKilled

```bash
# 查看 OOM 状态
podman inspect graphnexus-neo4j | jq '.[0].State.OOMKilled'

# 按需逐个启动以降低瞬时内存峰值
podman compose up -d mysql
sleep 30
podman compose up -d neo4j
sleep 30
podman compose up -d redis rabbitmq minio
```

### 10.3 Nginx 返回 502 Bad Gateway

```bash
# 检查应用副本是否在运行
podman compose -f deployment/podman-compose.app.yml ps

# 检查 Nginx 能否解析应用主机名
podman exec graphnexus-nginx wget -qO- http://graphnexus-app-1:8080/actuator/health

# 查看 Nginx 错误日志
podman exec graphnexus-nginx cat /var/log/nginx/error.log

# 热重载 Nginx
podman exec graphnexus-nginx nginx -s reload
```

### 10.4 Swagger UI 无法访问

```bash
# 确认 Nginx 配置包含 Swagger 路由
podman exec graphnexus-nginx grep -n "swagger" /etc/nginx/conf.d/graphnexus.conf

# 如果缺少，更新配置后热重载
podman exec graphnexus-nginx nginx -t
podman exec graphnexus-nginx nginx -s reload
```

### 10.5 端口冲突

```bash
# 检查端口占用
lsof -i :8080    # Nginx
lsof -i :8081    # App 副本 1
lsof -i :8082    # App 副本 2
lsof -i :3306    # MySQL
lsof -i :6379    # Redis

# 修改对应 compose 文件中的 ports 映射
```

### 10.6 应用启动后立即退出

```bash
# 查看应用日志
podman logs graphnexus-app-1

# 常见原因：
# 1. 基础设施未就绪 → 等待基础设施 healthy 后重启应用
# 2. 环境变量缺失 → 检查 .env 是否 source
# 3. 端口冲突 → 检查 8081/8082 是否被占用
```

### 10.7 数据卷权限问题（Linux SELinux）

如果遇到 `Permission denied`，确保挂载卷使用 `:Z` 标签：

```yaml
volumes:
  - ./deployment/nginx/nginx.conf:/etc/nginx/nginx.conf:Z,ro
```

---

## 十一、从 Docker 迁移到 Podman

```bash
# Podman 兼容 Docker Compose 语法
alias docker=podman
alias docker-compose=podman-compose

# 或直接使用 podman compose（Podman 4.x+ 内置）
podman compose up -d
```

---

## 十二、下一步

- [x] 部署 GraphNexus Spring Boot 应用容器（双副本 + Nginx 负载均衡）
- [ ] 配置 Prometheus + Grafana 监控栈（指标已暴露在 `/actuator/prometheus`）
- [ ] 配置 Loki + Promtail 日志采集（Nginx 已输出 JSON 格式日志）
- [ ] 生产环境切换到 Podman Quadlet（systemd 集成）
- [ ] CI/CD 流水线集成（自动构建 + 滚动更新）
- [ ] 配置 Pod 模式网络隔离（公网/内网分离）