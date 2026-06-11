# GraphNexus Podman 部署指南

> 版本：v1.0 | 日期：2026-06-10
>
> 适用范围：本地开发 / MVP 单机部署（适配 2 GiB Podman Machine）
>
> 参考文档：[tech-stack-java.md](../docs/tech-stack-java.md)、[physical-view.md](../docs/design-view/physical-view/physical-view.md)

---

## 一、前置条件

| 依赖 | 最低版本 | 验证命令 |
|------|---------|---------|
| **Podman** | 4.x+ | `podman version` |
| **podman-compose** | 1.x+ | `podman-compose version` |
| **Podman Machine**（macOS） | — | `podman machine list` |

### macOS 初始化

```bash
# 当前配置（2 GiB / 5 CPU — 本地轻量开发）
# 查看当前机器状态
podman machine list

# 如果需要更多内存（推荐 4 GiB+）：
podman machine stop
podman machine set --memory 4096 --cpus 6
podman machine start

# 首次创建
podman machine init --cpus 5 --memory 2048 --disk-size 50
podman machine start
```

---

## 二、目录结构

```
GraphNexus/
├── podman-compose.yml                 # 基础设施服务编排（主文件）
├── deployment/
│   ├── .env                            # 环境变量（密码、参数）
│   ├── deploy.md                       # 本文档
│   ├── nginx/
│   │   ├── nginx.conf                  # Nginx 主配置
│   │   └── conf.d/
│   │       └── graphnexus.conf         # GraphNexus 虚拟主机
│   ├── rabbitmq/
│   │   ├── rabbitmq.conf               # RabbitMQ 配置
│   │   └── definitions.json            # 队列/交换机/绑定预定义
│   └── redis/
│       └── redis.conf                  # Redis 配置
```

---

## 三、快速启动

### 3.1 启动所有基础设施服务

```bash
# 进入项目根目录
cd GraphNexus

# 启动（首次启动自动拉取镜像）
podman compose up -d

# 查看状态
podman compose ps

# 查看日志
podman compose logs -f
```

### 3.2 验证各服务健康状态

```bash
# 等待所有服务 healthy（约 60-90 秒）
watch -n 2 'podman compose ps'

# 逐一验证
# Neo4j
curl -s http://localhost:7474 | head -1

# MySQL
echo "SELECT 1" | podman exec -i graphnexus-mysql mysql -ugraphnexus -pgraphnexus123 graphnexus

# Redis
echo "PING" | podman exec -i graphnexus-redis redis-cli -a graphnexus123

# RabbitMQ
podman exec graphnexus-rabbitmq rabbitmq-diagnostics check_running

# MinIO
curl -s http://localhost:9000/minio/health/live

# Nginx
curl -s http://localhost:80/health
```

### 3.3 管理面板地址

| 服务 | URL | 认证 |
|------|-----|------|
| **Nginx API** | `http://localhost/api/` | — |
| **Neo4j Browser** | `http://localhost:7474` | `neo4j` / `graphnexus123` |
| **RabbitMQ UI** | `http://localhost:15672` | `graphnexus` / `graphnexus123` |
| **MinIO Console** | `http://localhost:9001` | `minioadmin` / `minioadmin123` |

---

## 四、常用操作

### 4.1 停止服务

```bash
# 停止所有容器（保留数据卷）
podman compose down

# 停止并删除所有数据卷（⚠️ 不可逆）
podman compose down -v
```

### 4.2 重启单个服务

```bash
podman compose restart neo4j
podman compose restart mysql
podman compose restart redis
podman compose restart rabbitmq
podman compose restart minio
podman compose restart nginx
```

### 4.3 查看服务日志

```bash
# 全部服务
podman compose logs -f --tail 100

# 单个服务
podman compose logs -f neo4j
podman compose logs -f mysql
```

### 4.4 进入容器调试

```bash
podman exec -it graphnexus-neo4j bash
podman exec -it graphnexus-mysql bash
podman exec -it graphnexus-redis sh
podman exec -it graphnexus-rabbitmq bash
podman exec -it graphnexus-minio sh
```

### 4.5 数据备份

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

### 4.6 重置数据

```bash
# 停止并清空数据卷
podman compose down -v

# 重新启动（自动重建所有数据）
podman compose up -d
```

---

## 五、Spring Boot 应用配置

应用连接中间件时，使用以下 Spring Boot 配置（`application.yml`）：

```yaml
spring:
  # ---- Neo4j 图数据库 ----
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: graphnexus123

  # ---- MySQL 关系数据库 ----
  datasource:
    url: jdbc:mysql://localhost:3306/graphnexus?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: graphnexus
    password: graphnexus123
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  # ---- Redis ----
  data:
    redis:
      host: localhost
      port: 6379
      password: graphnexus123
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4

  # ---- RabbitMQ ----
  rabbitmq:
    host: localhost
    port: 5672
    username: graphnexus
    password: graphnexus123
    virtual-host: graphnexus
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 5
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms

# ---- MinIO 对象存储 ----
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket:
    pdf: graphnexus-pdf
    export: graphnexus-export
    backup: graphnexus-backup
```

---

## 六、资源配置参考

| 容器 | 镜像大小 | 内存上限 | 磁盘建议 |
|------|---------|---------|---------|
| Neo4j | ~500MB | 512 MiB (heap 384m) | 5 GiB |
| MySQL | ~400MB | 256 MiB | 5 GiB |
| Redis | ~30MB | 64 MiB | 500 MiB |
| RabbitMQ | ~200MB | 128 MiB | 1 GiB |
| MinIO | ~80MB | 128 MiB | 5 GiB |
| Nginx | ~30MB | 32 MiB | — |
| **合计** | **~1.3 GB** | **~1.1 GiB** | **~17 GiB** |

---

## 七、故障排查

### Podman Machine 内存不足（macOS）

```bash
# 查看当前分配
podman machine inspect | grep -i memory

# 调整内存（必须停掉 Machine）
podman machine stop
podman machine set --memory 4096 --cpus 6
podman machine start

# 验证
podman machine ssh -- free -h
```

### 容器 OOMKilled（被系统杀死）

```bash
# 查看容器退出状态
podman compose ps -a

# 查看 OOM 记录
podman inspect graphnexus-neo4j | jq '.[0].State.OOMKilled'

# 按需启动服务（OOM 时逐个启动以降低瞬时内存峰值）
podman compose up -d mysql
sleep 30
podman compose up -d neo4j
sleep 30
podman compose up -d redis rabbitmq minio nginx
```

### 端口冲突

```bash
# 检查端口占用
lsof -i :3306
lsof -i :6379

# 修改 podman-compose.yml 中对应 ports 映射
```

### 数据卷权限问题（Linux SELinux）

如果遇到 `Permission denied`，确保挂载卷使用 `:Z` 标签：

```yaml
volumes:
  - ./deployment/nginx/nginx.conf:/etc/nginx/nginx.conf:Z,ro
```

### RabbitMQ 队列未创建

```bash
# 确认 definitions.json 已加载
podman exec graphnexus-rabbitmq rabbitmqctl list_queues -p graphnexus

# 如果未加载，手动导入
podman exec graphnexus-rabbitmq rabbitmqctl import_definitions /etc/rabbitmq/definitions.json
```

---

## 八、从 Docker 迁移到 Podman

```bash
# Podman 兼容 Docker Compose 语法，可直接使用
alias docker=podman
alias docker-compose=podman-compose

# 或使用 podman compose（内置子命令，Podman 4.x+）
podman compose up -d
```

---

## 九、下一步

- [ ] 部署 GraphNexus Spring Boot 应用容器（参考 [podman-compose.yml](../podman-compose.yml) 中 `graphnexus-app` 服务模板）
- [ ] 配置 Prometheus + Grafana 监控栈
- [ ] 配置 Loki + Promtail 日志采集
- [ ] 生产环境切换到 Podman Quadlet（systemd 集成）
- [ ] 配置 Pod 模式网络隔离（公网/内网）
