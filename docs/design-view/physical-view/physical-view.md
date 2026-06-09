# GraphNexus 物理部署图设计

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 南北向流量部署分析
>
> 版本：v1.4 | 创建日期：2026-06-09 | 修订日期：2026-06-09
>
> 参考文档：功能文档 · [user-stories-mvp.md](../../user-stories-mvp.md) · [logical-view.md](../../logical-view.md)

---

## 一、部署总览

### 1.1 什么是南北向流量

南北向流量是指 **用户请求从外部进入系统 → 经过内部处理 → 返回用户** 的完整链路。它是跨越系统边界的垂直流量。

```
                    ┌─────────────────────────────┐
                    │      用户 (浏览器)            │
                    └──────────────┬──────────────┘
                                   │
                          ╔════════╪════════╗
                          ║   南北向流量     ║  ← 跨越系统边界
                          ║   North-South   ║
                          ║       │         ║
              ┌───────────║───────▼───────║───────────┐
              │           ║    Nginx       ║           │
              │           ╚═══════════════╝           │
              │                   │                   │
              │          ┌────────┴────────┐          │
              │          ▼                 ▼          │
              │    ┌──────────┐     ┌──────────┐     │
              │    │ GraphNexus│     │ GraphNexus│     │
              │    │ 实例 1    │     │ 实例 2    │     │
              │    └────┬─────┘     └────┬─────┘     │
              │         │               │            │
              │         └───────┬───────┘            │
              │                 │                    │
              │    ┌────────────┼────────────┐       │
              │    ▼            ▼            ▼       │
              │ ┌──────┐  ┌──────────┐                │
              │                                     │
              │    ┌──────────┐ ┌──────────┐       │
              │    │  Redis   │ │  MinIO   │       │
              │    │  缓存     │ │ 文件存储  │       │
              │    └──────────┘ └──────────┘       │
              │                                     │
              │    ┌──────────┐                     │
              │    │RabbitMQ  │                     │
              │    │ 消息队列  │                     │
              │    └──────────┘                     │
              └─────────────────────────────────────┘
```

### 1.2 核心组件

| 组件 | 构造型 | 数量 | 端口 | 说明 |
|------|--------|------|------|------|
| **PC 客户端** | `«client»` | — | — | 五类用户通过浏览器访问系统 |
| **Nginx** | `«device»` | 1 | :443 | SSL 终结、反向代理、负载均衡、静态资源服务 |
| **GraphNexus 实例 1** | `«server»` | 1 | :8081 | 运行完整的后端服务（L2 应用层 + L3 领域层 + L4 适配器） |
| **GraphNexus 实例 2** | `«server»` | 1 | :8082 | 与实例 1 完全相同，Nginx 轮询负载均衡 |
| **Neo4j** | `«database»` | 1 | :7687 | 图数据库，存储宽图谱所有节点和关系 |
| **PostgreSQL** | `«database»` | 1 | :5432 | 关系数据库，存储用户/权限/配置/审计日志 |
| **Redis** | `«cache»` | 1 | :6379 | 缓存中间件，会话共享、查询缓存、限流计数 |
| **RabbitMQ** | `«queue»` | 1 | :5672 | 消息队列，异步解耦 PDF 解析、权重衰减、通知发送 |
| **MinIO** | `«storage»` | 1 | :9000 | 对象存储，PDF/CSV 文件、导出报告、图谱备份 |

---

## 二、起点与终点

### 2.1 起点：用户浏览器

依据用户故事文档，系统的流量起点是五类用户通过 **Web 浏览器** 发起的 HTTPS 请求：

| 用户 | 典型操作 |
|------|---------|
| **管理员** | PDF 上传、成绩 CSV 导入、实体对齐审核、知识体系管理 |
| **教师** | 归因查询、班级薄弱概览、作业/测验录入 |
| **学生** | 薄弱总览、复习推荐、进度追踪 |
| **运维人员** | 日志搜索、备份管理、定时任务管理 |
| **运营人员** | 文档产能统计、知识覆盖分析 |

### 2.2 终点：数据库、中间件

| 终点 | 类型 | 存储/处理内容 | 协议 |
|------|------|-------------|------|
| **Neo4j** | 图数据库 | 宽图谱节点/关系、掌握关系权重、事件节点 | Bolt/7687 |
| **PostgreSQL** | 关系数据库 | 用户/角色/权限、策略配置、审计日志、通知记录 | TCP/5432 |
| **Redis** | 缓存中间件 | 会话 Token、热点查询缓存、图谱快照、Prompt 渲染缓存、限流计数 | TCP/6379 |
| **RabbitMQ** | 消息队列 | PDF 处理任务、CSV 导入任务、权重衰减任务、通知发送任务 | AMQP/5672 |
| **MinIO** | 文件存储 | PDF 教辅文件、CSV 导入文件、导出报告、图谱备份文件 | HTTP/9000 |

### 2.3 中间件的作用

| 中间件 | 为什么需要 | 解决什么问题 |
|--------|----------|------------|
| **Redis** | ① 两个实例共享会话状态（否则 Nginx 轮询时用户可能被登出）；② 缓存热点查询结果，减少 Neo4j 压力；③ API 限流计数器 | 无状态实例的会话共享 + 性能优化 |
| **RabbitMQ** | ① PDF 解析耗时数秒到数十秒，同步等待用户体验差；② 权重衰减是批处理任务，不应阻塞请求线程；③ 通知发送不应影响主业务流程 | 耗时操作异步化，解耦核心链路与后台任务 |
| **MinIO** | ① PDF/CSV 文件需要持久化存储；② 导出报告需要文件存储；③ 与业务数据库分离，避免大文件影响数据库性能 | 二进制大对象的独立存储 |

### 2.4 起点→终点 路径映射

| 核心用例 | 路径 |
|---------|------|
| **PDF 上传与图谱构建** | 管理员 → Nginx → GraphNexus实例 → **MinIO**(存文件) → **RabbitMQ**(发解析任务) → 异步消费 → Neo4j |
| **成绩 CSV 导入** | 管理员 → Nginx → GraphNexus实例 → **MinIO**(存文件) → **RabbitMQ**(发导入任务) → 异步消费 → Neo4j + PostgreSQL |
| **实体对齐审核** | 管理员 → Nginx → GraphNexus实例 → Neo4j |
| **归因查询** | 教师 → Nginx → GraphNexus实例 → **Redis**(查缓存) → Neo4j(查图谱) |
| **班级薄弱概览** | 教师 → Nginx → GraphNexus实例 → Neo4j |
| **学生自助查询** | 学生 → Nginx → GraphNexus实例 → **Redis**(查缓存) → Neo4j |
| **通知发送** | 各模块 → **RabbitMQ**(发通知任务) → 异步消费 → 邮件/站内/Webhook |

---

## 三、逐跳分析

### 3.1 第 ① 跳：用户浏览器（起点）

用户通过浏览器访问 `https://graphnexus.example.com`，所有请求统一使用 HTTPS。

### 3.2 第 ② 跳：Nginx 反向代理

| 维度 | 说明 |
|------|------|
| **物理形态** | 单台 Nginx 服务器 |
| **职责** | ① SSL 终结（HTTPS → HTTP 内网） ② 负载均衡（分发到 2 个 GraphNexus 实例） ③ 静态资源直接返回 |
| **负载均衡算法** | 轮询（round-robin） |

```
# Nginx 核心配置

upstream graphnexus_backend {
    # 轮询负载均衡
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
}

server {
    listen 443 ssl;
    server_name graphnexus.example.com;

    ssl_certificate     /etc/ssl/graphnexus.crt;
    ssl_certificate_key /etc/ssl/graphnexus.key;

    # 静态资源直接返回
    location /assets/ {
        root /var/www/frontend;
    }

    # API 请求转发到后端
    location /api/ {
        proxy_pass http://graphnexus_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 3.3 第 ③ 跳：GraphNexus 实例 ×2（业务处理）

| 维度 | 说明 |
|------|------|
| **物理形态** | 2 台服务器（或 2 个 Podman 容器），各自运行完整的 GraphNexus 后端 |
| **端口** | 8081、8082 |
| **副本作用** | ① 负载分担——Nginx 轮询分发请求 ② 故障转移——一个实例宕机时 Nginx 自动将流量切到另一个 |
| **内部结构** | 每个实例内部包含 L2 应用层（API 入口 + 用例编排）+ L3 领域层（图谱核心 M4、AI 分析 M5、主数据 M1、知识体系 M2、数据入库 M3）+ L4 基础设施适配器 |

```
GraphNexus 实例 1 (192.168.1.10:8081)     GraphNexus 实例 2 (192.168.1.11:8082)
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│  L2 应用层                       │       │  L2 应用层                       │
│  ├─ API 入口 (JWT认证/路由)       │       │  ├─ API 入口 (JWT认证/路由)       │
│  └─ 用例编排 (权限/业务调度)       │       │  └─ 用例编排 (权限/业务调度)       │
│                                 │       │                                 │
│  L3 领域层                       │       │  L3 领域层                       │
│  ├─ M4 图谱核心 (融合/剪枝/权重)   │       │  ├─ M4 图谱核心 (融合/剪枝/权重)   │
│  ├─ M5 AI分析 (上下文/生成)        │       │  ├─ M5 AI分析 (上下文/生成)        │
│  ├─ M1 主数据 (学生/教师/班级)    │       │  ├─ M1 主数据 (学生/教师/班级)    │
│  ├─ M2 知识体系 (分类/对齐/依赖)  │       │  ├─ M2 知识体系 (分类/对齐/依赖)  │
│  └─ M3 数据入库 (PDF/CSV解析)    │       │  └─ M3 数据入库 (PDF/CSV解析)    │
│                                 │       │                                 │
│  L4 基础设施适配器                │       │  L4 基础设施适配器                │
│  ├─ Neo4j 连接池                 │       │  ├─ Neo4j 连接池                 │
│  ├─ PostgreSQL 连接池 (HikariCP)  │       │  ├─ PostgreSQL 连接池 (HikariCP)  │
│  ├─ Redis 客户端 (Lettuce)       │       │  ├─ Redis 客户端 (Lettuce)       │
│  ├─ RabbitMQ 生产者/消费者        │       │  ├─ RabbitMQ 生产者/消费者        │
│  ├─ MinIO 客户端                 │       │  ├─ MinIO 客户端                 │
└─────────────────────────────────┘       └─────────────────────────────────┘
         │               │                        │               │
         └───────┬───────┘                        └───────┬───────┘
                 │                                        │
                 ▼                                        ▼
          共享的 Neo4j + PostgreSQL + Redis
                + RabbitMQ + MinIO
```

> **关键设计**：两个实例**无状态**——会话状态存储在 Redis 中，请求可被任意实例处理。文件存储在 MinIO 中，两个实例共享访问。

### 3.4 第 ④ 跳：Neo4j 图数据库（终点）

| 维度 | 说明 |
|------|------|
| **连接方式** | Bolt 协议，端口 7687 |
| **连接池** | 每个实例独立连接池（20 个连接），两个实例共 40 个连接 |
| **存储内容** | 学生/知识点/文档/事件节点、掌握关系/前置依赖关系/知识关联关系、权重值 |
| **核心操作** | Cypher 图查询（剪枝子图提取、权重读取、邻域展开）、图写入（节点/关系创建、权重更新） |

### 3.5 第 ⑤ 跳：PostgreSQL 关系数据库（终点）

| 维度 | 说明 |
|------|------|
| **连接方式** | JDBC，端口 5432 |
| **连接池** | 每个实例 HikariCP 连接池（10 个连接） |
| **存储内容** | 用户账号/角色/权限、剪枝策略/权重规则配置、审计日志、通知记录、分享链接 |

### 3.6 第 ⑥ 跳：Redis 缓存中间件

| 维度 | 说明 |
|------|------|
| **连接方式** | TCP，端口 6379，Lettuce 客户端 |
| **连接池** | 每个实例 8 个连接 |
| **缓存内容** | |

| 缓存键模式 | 内容 | TTL | 作用 |
|-----------|------|-----|------|
| `session:{token}` | 用户会话信息（userId, roles） | 2h | 两个实例共享会话，任意实例可校验 Token |
| `cache:query:{hash}` | 热点查询结果（班级概览、薄弱排行） | 5min | 减少 Neo4j 重复查询 |
| `cache:snapshot:{id}` | 图谱快照序列化数据 | 1h | 图谱对比功能加速 |
| `cache:prompt:{version}` | Prompt 模板渲染缓存 | 30min | 减少模板引擎重复渲染 |
| `ratelimit:{ip}:{api}` | API 限流计数器 | 1min | 防止单个 IP 高频调用 |

### 3.7 第 ⑦ 跳：RabbitMQ 消息队列

| 维度 | 说明 |
|------|------|
| **连接方式** | AMQP 协议，端口 5672 |
| **交换器类型** | direct（点对点任务）、fanout（通知广播） |
| **消息持久化** | 开启（delivery_mode=2），队列和消息均持久化，重启不丢失 |
| **消费确认** | 手动 ACK，处理成功后才确认，失败消息进入死信队列（DLX） |

| 队列名 | 生产者 | 消费者 | 说明 |
|--------|-------|--------|------|
| `doc.ingestion` | 实例（PDF 上传后） | 实例（M3 文档解析） | PDF 异步解析：版面分析→NER→RE→图谱导入 |
| `event.ingestion` | 实例（CSV 导入后） | 实例（M3 事件导入） | CSV 异步导入：校验→匹配→创建事件节点 |
| `weight.decay` | M7 定时任务 | 实例（M4 权重引擎） | 批量权重时间衰减计算 |
| `notification.send` | 各模块（事件发布） | 实例（M7 通知服务） | 异步发送邮件/站内消息/Webhook |

```
RabbitMQ 消息流：

  PDF上传 ──▶ MinIO(存文件) ──▶ doc.ingestion ──▶ M3消费者(解析) ──▶ Neo4j(写入)
  CSV导入 ──▶ MinIO(存文件) ──▶ event.ingestion ──▶ M3消费者(导入) ──▶ Neo4j(写入)
  定时触发 ──────────────────▶ weight.decay ──▶ M4消费者(衰减) ──▶ Neo4j(更新权重)
  业务事件 ──────────────────▶ notification.send ──▶ M7消费者(发送) ──▶ 邮件/站内/Webhook
```

### 3.8 第 ⑧ 跳：MinIO 文件存储

| 维度 | 说明 |
|------|------|
| **连接方式** | HTTP/9000（S3 兼容 API） |
| **Bucket 划分** | |

| Bucket | 存储内容 | 生命周期 |
|--------|---------|---------|
| `documents` | PDF 教辅原始文件 | 永久保存 |
| `csv-imports` | 成绩 CSV 导入文件 | 保存 90 天 |
| `exports` | 归因报告导出文件（PDF/HTML/Markdown） | 保存 30 天 |
| `backups` | Neo4j 图数据库备份文件 | 保留最近 7 份 |


---

## 四、完整请求链路

### 4.1 同步链路：归因查询

```
时间 →

用户浏览器      Nginx        GraphNexus实例1      Redis        Neo4j
    │             │                │                │            │
    │── HTTPS ───▶│                │                │            │
    │             │── 轮询选择 ────▶│                │            │
    │             │                │                │            │
    │             │                │── ① JWT认证     │            │
    │             │                │── ② 权限校验    │            │
    │             │                │                │            │
    │             │                │── ③ 查缓存 ───▶│            │
    │             │                │◀── 未命中 ─────│            │
    │             │                │                │            │
    │             │                │── ④ 剪枝查询 ──────────────▶│
    │             │                │◀── 子图数据 ────────────────│
    │             │                │                │            │
    │             │                │── ⑤ 写缓存 ───▶│            │
    │             │                │── ⑥ 组装 VO    │            │
    │             │                │                │            │
    │             │◀── Response ───│                │            │
    │◀── HTTPS ───│                │                │            │
```

### 4.2 异步链路：PDF 上传与图谱构建

```
时间 →

用户浏览器      Nginx        GraphNexus实例1     MinIO      RabbitMQ      M3消费者        Neo4j
    │             │                │               │            │             │             │
    │── HTTPS ───▶│                │               │            │             │             │
    │  POST /api/ │                │               │            │             │             │
    │  admin/     │                │               │            │             │             │
    │  documents  │                │               │            │             │             │
    │  (multipart)│                │               │            │             │             │
    │             │── 轮询选择 ────▶│               │            │             │             │
    │             │                │               │            │             │             │
    │             │                │── ① JWT认证    │            │             │             │
    │             │                │── ② 存文件 ───▶│            │             │             │
    │             │                │◀── fileId ────│            │             │             │
    │             │                │               │            │             │             │
    │             │                │── ③ 写 PostgreSQL (document记录)        │             │
    │             │                │               │            │             │             │
    │             │                │── ④ 发消息 ──────────────▶│             │             │
    │             │                │   doc.ingestion           │             │             │
    │             │                │               │            │             │             │
    │             │◀── 202 Accepted│               │            │             │             │
    │◀── HTTPS ───│   {docId,      │               │            │             │             │
    │             │    status:     │               │            │             │             │
    │             │    "processing"}               │            │             │             │
    │             │                │               │            │             │             │
    │             │                │               │     ┌──────┘             │             │
    │             │                │               │     │ 异步消费消息         │             │
    │             │                │               │     ▼                    │             │
    │             │                │               │  ┌────────┐              │             │
    │             │                │               │  │ M3消费者│              │             │
    │             │                │               │  └───┬────┘              │             │
    │             │                │               │      │                   │             │
    │             │                │               │      │── ⑤ 从MinIO读文件 ─▶│             │
    │             │                │               │      │◀── PDF数据 ───────│             │
    │             │                │               │      │                   │             │
    │             │                │               │      │── ⑥ 版面分析       │             │
    │             │                │               │      │── ⑦ NER实体抽取    │             │
    │             │                │               │      │── ⑧ RE关系抽取     │             │
    │             │                │               │      │── ⑨ 图谱导入 ────────────────▶│
    │             │                │               │      │                   │             │
    │             │                │               │      │── ⑩ 更新状态(COMPLETED) ──▶ PostgreSQL
    │             │                │               │      │                   │             │
    │             │                │               │      │── ⑪ 发通知 ──────▶│             │
    │             │                │               │      │   notification.send│             │
    │             │                │               │      │                   │             │
    │             │                │  (用户轮询或WebSocket获取处理完成通知)      │             │
```

---

## 五、Nginx 负载均衡与故障转移

### 5.1 正常情况：轮询分发

```
请求1 ──▶ Nginx ──▶ 实例1 (处理)
请求2 ──▶ Nginx ──▶ 实例2 (处理)
请求3 ──▶ Nginx ──▶ 实例1 (处理)
请求4 ──▶ Nginx ──▶ 实例2 (处理)
...
```

### 5.2 实例故障：自动摘除

```
                    ┌──────────┐
                    │  Nginx   │
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
              ▼          │          ▼
        ┌──────────┐    │    ┌──────────┐
        │ 实例1     │    │    │ 实例2     │
        │ (宕机)    │    │    │ (正常)    │
        └──────────┘    │    └──────────┘
                        │
        健康检查失败      │      所有请求
        max_fails=3     │      全部转发到实例2
                        │
              Nginx 自动摘除实例1
              后续请求仅发往实例2
```

Nginx 健康检查配置：

```nginx
upstream graphnexus_backend {
    server 192.168.1.10:8080 max_fails=3 fail_timeout=30s;  # 3次失败后摘除30秒
    server 192.168.1.11:8080 max_fails=3 fail_timeout=30s;
}
```

---

## 六、部署拓扑图

> 以下拓扑图与 [deployment-diagram.drawio](diagrams/deployment/deployment-diagram.drawio) 保持一致，使用 UML 构造型标注各组件类型。

```
                          ┌─────────────────┐
                          │  «client»        │
                          │   PC 客户端       │
                          │  浏览器 :443      │
                          └────────┬────────┘
                                   │
                                   │ HTTPS :443
                                   │
                          ┌────────▼────────┐
                          │  «device»        │
                          │  Nginx           │
                          │                  │
                          │  SSL 终结         │
                          │  负载均衡          │
                          │  静态资源          │
                          │  :443            │
                          └────────┬────────┘
                                   │
                                   │ HTTP (内网)
                                   │
               ┌───────────────────┼───────────────────┐
               │                   │                   │
               │   ┌───────────────┴───────────────┐   │
               │   │      GraphNexus 业务服务        │   │
               │   │                               │   │
               ▼   ▼                               ▼   ▼
     ┌──────────────┐                     ┌──────────────┐
     │  «server»    │                     │  «server»    │
     │  GraphNexus  │                     │  GraphNexus  │
     │  实例 1       │                     │  实例 2       │
     │              │                     │              │
     │  L2 应用层    │                     │  L2 应用层    │
     │  L3 领域层    │                     │  L3 领域层    │
     │  L4 适配器    │                     │  L4 适配器    │
     │              │                     │              │
     │  :8081       │                     │  :8082       │
     └──────┬───────┘                     └──────┬───────┘
            │                                    │
            └──────────────┬─────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         │ Bolt:7687  JDBC:5432  TCP:6379   │
         ▼                 ▼                 ▼
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │ «database»   │ │ «database»   │ │  «cache»     │
  │   Neo4j      │ │ PostgreSQL   │ │   Redis      │
  │              │ │              │ │              │
  │ 宽图谱存储    │ │ 用户/权限     │ │ 会话共享      │
  │ 权重/事件     │ │ 配置/审计     │ │ 查询缓存      │
  │  :7687       │ │  :5432       │ │  :6379       │
  └──────────────┘ └──────────────┘ └──────────────┘

         ┌─────────────────┼─────────────────┐
         │                 │                 │
         │ AMQP:5672  HTTP:9000              │
         ▼                 ▼                 │
  ┌──────────────┐ ┌──────────────┐          │
  │  «queue»     │ │ «storage»    │          │
  │  RabbitMQ    │ │   MinIO      │          │
  │              │ │              │          │
  │ 异步解耦      │ │ PDF/CSV      │          │
  │ 任务编排      │ │ 备份/导出     │          │
  │  :5672       │ │  :9000       │          │
  └──────────────┘ └──────────────┘          │
                                             │
  构造型图例:
  ┌──────────────┬──────────────────────────────────────┐
  │  «client»    │  客户端（用户浏览器）                   │
  │  «device»    │  网络设备（反向代理/负载均衡器）          │
  │  «server»    │  服务器（业务应用实例）                  │
  │  «database»  │  数据库（图数据库/关系数据库）            │
  │  «cache»     │  缓存中间件                            │
  │  «queue»     │  消息队列中间件                         │
  │  «storage»   │  对象存储                              │
  └──────────────┴──────────────────────────────────────┘
```

---

## 七、核心配置清单

### 7.1 Nginx 完整配置

```nginx
upstream graphnexus_backend {
    # 轮询
    server 192.168.1.10:8080 max_fails=3 fail_timeout=30s;
    server 192.168.1.11:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 443 ssl http2;
    server_name graphnexus.example.com;

    # SSL
    ssl_certificate     /etc/ssl/graphnexus.crt;
    ssl_certificate_key /etc/ssl/graphnexus.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    # 静态资源（前端 SPA）
    location /assets/ {
        root /var/www/frontend;
        expires 7d;
    }

    # API 转发
    location /api/ {
        proxy_pass http://graphnexus_backend;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_read_timeout 60s;
        proxy_connect_timeout 5s;
    }
}
```

### 7.2 GraphNexus 实例配置

```yaml
# 每个实例的 application.yml (核心配置)
server:
  port: 8080

spring:
  # Neo4j 图数据库
  neo4j:
    uri: bolt://192.168.1.20:7687
    authentication:
      username: neo4j
      password: ${NEO4J_PASSWORD}
    pool:
      max-connection-pool-size: 20

  # PostgreSQL 关系数据库
  datasource:
    url: jdbc:postgresql://192.168.1.30:5432/graphnexus
    username: graphnexus
    password: ${PG_PASSWORD}
    hikari:
      maximum-pool-size: 10

  # Redis 缓存
  data:
    redis:
      host: 192.168.1.40
      port: 6379
      password: ${REDIS_PASSWORD}
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

  # RabbitMQ 消息队列
  rabbitmq:
    host: 192.168.1.50
    port: 5672
    username: graphnexus
    password: ${RABBITMQ_PASSWORD}
    # 生产者确认 + 消费者手动ACK
    publisher-confirm-type: correlated
    listener:
      simple:
        acknowledge-mode: manual

# MinIO 文件存储
minio:
  endpoint: http://192.168.1.60:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  buckets:
    documents: documents
    csv-imports: csv-imports
    exports: exports
    backups: backups

```

### 7.3 Podman Compose 部署文件

> Podman 与 Docker 的最大差异在于 Podman 是 **无守护进程（daemonless）** 的，以普通用户身份运行，天然支持 rootless 模式。Podman 使用 `podman-compose` 或 `podman compose` 命令启动容器编排。

```yaml
# podman-compose.yml
# 启动命令: podman-compose -f podman-compose.yml up -d
# 或: podman compose -f podman-compose.yml up -d
version: '3.8'

services:
  # ============ 反向代理 ============
  nginx:
    image: docker.io/library/nginx:1.25-alpine
    container_name: graphnexus-nginx
    ports:
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro,Z
      - ./nginx/ssl:/etc/ssl:ro,Z
      - ./frontend/dist:/var/www/frontend:ro,Z
    depends_on:
      - graphnexus-1
      - graphnexus-2
    # rootless Podman 绑定特权端口 (<1024) 需设置:
    # sysctl net.ipv4.ip_unprivileged_port_start=443
    # 或改用高端口映射: "8443:443"

  # ============ 业务实例 ×2 ============
  graphnexus-1:
    image: graphnexus:latest
    container_name: graphnexus-instance-1
    ports:
      - "8081:8080"
    environment:
      - NEO4J_URI=bolt://neo4j:7687
      - PG_URL=jdbc:postgresql://postgres:5432/graphnexus
      - REDIS_HOST=redis
      - RABBITMQ_HOST=rabbitmq
      - MINIO_ENDPOINT=http://minio:9000
    depends_on:
      - neo4j
      - postgres
      - redis
      - rabbitmq
      - minio
    # Podman rootless: 容器内默认以 root 运行会被映射为宿主机普通用户
    # 若需以非 root 运行，在 Containerfile 中使用 USER 指令

  graphnexus-2:
    image: graphnexus:latest
    container_name: graphnexus-instance-2
    ports:
      - "8082:8080"
    environment:
      - NEO4J_URI=bolt://neo4j:7687
      - PG_URL=jdbc:postgresql://postgres:5432/graphnexus
      - REDIS_HOST=redis
      - RABBITMQ_HOST=rabbitmq
      - MINIO_ENDPOINT=http://minio:9000
    depends_on:
      - neo4j
      - postgres
      - redis
      - rabbitmq
      - minio

  # ============ 图数据库 ============
  neo4j:
    image: docker.io/library/neo4j:5-community
    container_name: graphnexus-neo4j
    ports:
      - "7687:7687"
      - "7474:7474"
    environment:
      - NEO4J_AUTH=neo4j/${NEO4J_PASSWORD}
    volumes:
      - neo4j_data:/data:Z

  # ============ 关系数据库 ============
  postgres:
    image: docker.io/library/postgres:16-alpine
    container_name: graphnexus-postgres
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=graphnexus
      - POSTGRES_USER=graphnexus
      - POSTGRES_PASSWORD=${PG_PASSWORD}
    volumes:
      - pg_data:/var/lib/postgresql/data:Z

  # ============ 缓存中间件 ============
  redis:
    image: docker.io/library/redis:7-alpine
    container_name: graphnexus-redis
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data:Z

  # ============ 消息队列 ============
  rabbitmq:
    image: docker.io/library/rabbitmq:3-management-alpine
    container_name: graphnexus-rabbitmq
    ports:
      - "5672:5672"     # AMQP
      - "15672:15672"   # 管理界面
    environment:
      - RABBITMQ_DEFAULT_USER=graphnexus
      - RABBITMQ_DEFAULT_PASS=${RABBITMQ_PASSWORD}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq:Z

  # ============ 文件存储 ============
  minio:
    image: docker.io/minio/minio:latest
    container_name: graphnexus-minio
    ports:
      - "9000:9000"     # API
      - "9001:9001"     # 控制台
    command: server /data --console-address ":9001"
    environment:
      - MINIO_ROOT_USER=${MINIO_ACCESS_KEY}
      - MINIO_ROOT_PASSWORD=${MINIO_SECRET_KEY}
    volumes:
      - minio_data:/data:Z

volumes:
  neo4j_data:
  pg_data:
  redis_data:
  rabbitmq_data:
  minio_data:
```

### 7.4 Podman vs Docker 关键差异

| 差异点 | Docker | Podman | 本文档处理方式 |
|--------|--------|--------|-------------|
| **守护进程** | dockerd 守护进程 | 无守护进程（daemonless），直接 fork/exec | 无需额外处理，Podman 天然支持 |
| **运行权限** | 默认 root（dockerd 以 root 运行） | 支持 rootless（普通用户运行容器） | 卷挂载加 `:Z` 标签适配 SELinux；443 端口需设 `net.ipv4.ip_unprivileged_port_start=443` 或改用高端口 |
| **镜像引用** | `nginx:1.25-alpine`（自动从 Docker Hub 拉取） | 需完整路径 `docker.io/library/nginx:1.25-alpine` | compose 文件中已使用完整镜像路径 |
| **卷挂载 SELinux** | 自动处理 | 需显式加 `:Z` 标签（`/data:Z`） | compose 文件中所有卷挂载已加 `:Z` |
| **Compose 命令** | `docker-compose up -d` | `podman-compose up -d` 或 `podman compose up -d` | 两者均支持 |
| **容器网络** | 默认 bridge 网络 | 默认使用 slirp4netns（rootless）或 netavark（root） | 容器间通过服务名互访，无需额外配置 |
| **开机自启** | `restart: always` + dockerd 自启 | 需配合 `systemd --user` 生成 unit 文件：`podman generate systemd` | 生产环境建议通过 systemd 管理 |

### 7.5 Podman 容器构建（Containerfile）

```dockerfile
# Containerfile (兼容 Dockerfile 语法，Podman 原生支持)
# 构建命令: podman build -t graphnexus:latest -f Containerfile .

# ============ 第一阶段：构建 ============
FROM docker.io/library/eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./gradlew bootJar --no-daemon

# ============ 第二阶段：运行 ============
FROM docker.io/library/eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户（Podman rootless 最佳实践）
RUN addgroup -S graphnexus && adduser -S graphnexus -G graphnexus

COPY --from=builder /build/build/libs/graphnexus-*.jar app.jar

USER graphnexus
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.6 Podman Pod 部署模式（可选）

Podman 独有的 **Pod** 概念可将关联容器编组，共享网络命名空间（类似 K8s Pod）：

```bash
# 创建 Pod 并一次性部署所有容器
podman pod create \
  --name graphnexus \
  --publish 443:443 \
  --publish 8080:8080

# 在 Pod 内启动各容器（共享 localhost 网络）
podman run -d --pod graphnexus --name nginx    nginx:1.25-alpine
podman run -d --pod graphnexus --name backend-1 graphnexus:latest
podman run -d --pod graphnexus --name backend-2 graphnexus:latest
podman run -d --pod graphnexus --name neo4j     neo4j:5-community
podman run -d --pod graphnexus --name postgres  postgres:16-alpine
podman run -d --pod graphnexus --name redis     redis:7-alpine
podman run -d --pod graphnexus --name rabbitmq  rabbitmq:3-management-alpine
podman run -d --pod graphnexus --name minio     minio/minio

# 生成 systemd 单元文件，实现开机自启
podman generate systemd --name graphnexus > ~/.config/systemd/user/graphnexus-pod.service
systemctl --user enable graphnexus-pod.service
```

> Pod 模式优势：所有容器共享 `localhost` 网络，实例间通过 `localhost:8080` 互访，无需关注容器 IP。Nginx 直接 `proxy_pass http://localhost:8080` 即可轮询后端（需配合额外的负载均衡方案）。

---

## 八、故障场景与恢复

| 故障场景 | 影响 | 恢复方式 |
|---------|------|---------|
| **实例 1 宕机** | 无影响 | Nginx 自动将全部流量切到实例 2，实例 1 恢复后自动重新加入 |
| **实例 2 宕机** | 无影响 | 同上，Nginx 自动切换 |
| **两个实例同时宕机** | 服务不可用 | 需手动恢复实例，排查根因 |
| **Nginx 宕机** | 服务不可用 | 单点故障——生产环境可加 Keepalived VIP 实现 Nginx 主备 |
| **Neo4j 宕机** | 全部功能不可用 | 恢复 Neo4j 服务，数据从 MinIO 备份恢复 |
| **PostgreSQL 宕机** | 登录/配置功能不可用，图谱核心功能不受影响 | 恢复 PostgreSQL 服务 |
| **Redis 宕机** | ① 会话共享失效，用户可能被登出 ② 缓存未命中，查询变慢 ③ 限流失效 | Redis 恢复后自动重建缓存；会话丢失需用户重新登录 |
| **RabbitMQ 宕机** | ① PDF/CSV 上传可接受但处理暂停 ② 通知发送暂停 ③ 权重衰减暂停 | 恢复 RabbitMQ 后积压消息自动消费；持久化消息不丢失 |
| **MinIO 宕机** | ① PDF/CSV 上传失败 ② 报告导出不可用 ③ 备份不可用 | 恢复 MinIO 服务，数据不丢失 |
---

## 附录

### A. 组件端口总览

| 组件 | 构造型 | 端口 | 协议 | 用途 |
|------|--------|------|------|------|
| PC 客户端 | `«client»` | — | HTTPS | 用户浏览器访问入口 |
| Nginx | `«device»` | 443 | HTTPS | SSL 终结 + 反向代理 + 负载均衡 |
| GraphNexus 实例 1 | `«server»` | 8081 | HTTP | 后端 API 服务（对外映射端口，内部 :8080） |
| GraphNexus 实例 2 | `«server»` | 8082 | HTTP | 后端 API 服务（对外映射端口，内部 :8080） |
| Neo4j | `«database»` | 7687 | Bolt | 图数据库查询 |
| Neo4j | `«database»` | 7474 | HTTP | Neo4j Browser 管理界面 |
| PostgreSQL | `«database»` | 5432 | TCP | 关系数据库 |
| Redis | `«cache»` | 6379 | TCP | 缓存服务 |
| RabbitMQ | `«queue»` | 5672 | AMQP | 消息队列 |
| RabbitMQ | `«queue»` | 15672 | HTTP | RabbitMQ 管理界面 |
| MinIO | `«storage»` | 9000 | HTTP | 对象存储 API |
| MinIO | `«storage»` | 9001 | HTTP | MinIO 控制台 |
### B. 文档修订历史

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | 2026-06-09 | 初始版本：简化南北向部署设计，聚焦 Nginx + 2 实例 + 数据库 |
| v1.1 | 2026-06-09 | 新增中间件：Redis（缓存/会话共享）、RabbitMQ（异步消息）、MinIO（文件存储）；新增异步链路时序图；完善 Docker Compose |
| v1.2 | 2026-06-09 | 容器化部署从 Docker 切换为 Podman：Docker Compose → Podman Compose、新增 Containerfile、Podman vs Docker 差异对照表、Podman Pod 部署模式 |
| v1.3 | 2026-06-09 | 对齐 [deployment-diagram.drawio](diagrams/deployment/deployment-diagram.drawio)：① 组件表新增 UML 构造型列和端口列；② 部署拓扑图重绘，加入实例包围框、构造型图例；③ PC 客户端显式标注；④ 实例端口改为 :8081/:8082 |
| v1.4 | 2026-06-09 | 删除 LLM API：移除组件表/终点表/路径映射中的 LLM API 行、删除 3.9 节、简化时序图、移除拓扑图中外部 LLM 区域和分隔线、配置文件中删除 `llm.*` 和 `ANTHROPIC_API_KEY` |

### C. 参考文档

- [基于图谱技术的 AI 上下文处理与精准问答系统.md](../../基于图谱技术的 AI 上下文处理与精准问答系统.md) — 功能文档
- [user-stories-mvp.md](../../user-stories-mvp.md) — MVP 用户故事
- [logical-view.md](../../logical-view.md) — 逻辑视图
