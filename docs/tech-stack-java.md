# GraphNexus Java 技术栈方案（JDK 17）

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 后端技术栈
>
> 版本：v1.0 | 创建日期：2026-06-08
>
> 参考文档：[开发视图](../../docs/design-view/dev-view/dev-view.md)、[逻辑视图](../../docs/design-view/logical-view/logical-view.md)、[物理视图](../../docs/design-view/physical-view/physical-view.md)

---

## 一、技术栈总览

| 类别 | 技术选型 | 版本 | 依据 |
|------|---------|------|------|
| **JDK** | Java 17 LTS | 17.0.x | 硬性要求 |
| **构建工具** | Maven | 3.9.x | 单模块 Maven 项目，包内分层架构，Spring Boot Starter Parent 统一版本管控 |
| **核心框架** | Spring Boot 3 | 3.3.x | JDK 17+ 原生支持，生态完整 |
| **Web 框架** | Spring MVC (Servlet) | 6.x | L2 API 层 REST 端点 |
| **图数据库** | Neo4j + Spring Data Neo4j | Neo4j 5.x, SDN 7.x | 宽图谱节点/关系/权重存储 |
| **关系数据库** | MySQL + Spring Data JPA + Hibernate 6 | MySQL 8.0, Hibernate 6.4+ | 用户/角色/权限/配置/审计日志 |
| **消息队列** | RabbitMQ + Spring AMQP | RabbitMQ 3.x, Spring AMQP 3.x | PDF/CSV 异步入库、权重衰减、通知 |
| **缓存** | Redis + Spring Data Redis (Lettuce) | Redis 7.x, Lettuce 6.x | Session/热点查询/图谱快照/Prompt 缓存 |
| **本地缓存** | Caffeine | 3.x | 配置数据、分类树本地缓存 |
| **LLM 集成** | Spring AI + 自研 LLMGateway | Spring AI 1.0.x | 模型路由/配额/降级/调用审计 |
| **LLM 调用** | Spring WebFlux WebClient | 6.x | 流式响应、HTTP 客户端 |
| **容错降级** | Resilience4j | 2.x | 熔断/重试/限流/隔离 |
| **文件存储** | MinIO Java Client / AWS S3 SDK | MinIO 8.x | PDF/导出文件/备份对象存储 |
| **PDF 解析** | Apache PDFBox | 3.x | 版面分析、文本提取 |
| **OCR** | Tesseract (JavaCPP/CLI) | — | 扫描版 PDF 降级策略 |
| **安全认证** | Spring Security + JWT (jjwt) | Spring Security 6.x, jjwt 0.12 | RBAC 五类角色、API 鉴权 |
| **参数校验** | Jakarta Validation + Hibernate Validator | 8.x | L2 应用层输入校验 |
| **序列化** | Jackson + JavaTime Module | 2.16+ | JSON 序列化/反序列化 |
| **对象映射** | MapStruct | 1.5.x | DTO/VO 编译期映射 |
| **代码简化** | Lombok / Java Records | Lombok 1.18.x | DTO/VO 优先使用 Records |
| **API 文档** | SpringDoc OpenAPI (Swagger UI) | 2.x | REST API 文档生成 |
| **可观测性** | Micrometer + Prometheus + Actuator | Micrometer 1.x | 指标采集与暴露 |
| **分布式追踪** | Micrometer Tracing + OpenTelemetry | 1.x | Trace ID 全链路追踪 |
| **日志** | SLF4J + Logback + Logstash JSON Encoder | Spring Boot 默认 | JSON 结构化日志 → Loki |
| **定时任务** | Spring @Scheduled + Quartz | Quartz 2.x | Cron 调度 + DAG 依赖编排 |
| **测试** | JUnit 5 + Mockito + Testcontainers | JUnit 5.10+, Testcontainers 1.19+ | 单元/集成测试 |
| **性能测试** | JMH | 1.x | 图算法性能基准 |

---

## 二、分层技术栈详解

### 2.1 基础框架层

| 技术 | 选型理由 |
|------|---------|
| **JDK 17 LTS** | 硬性要求；Records、Sealed Classes、Pattern Matching、Text Blocks 等现代 Java 特性 |
| **Maven 3.9.x** | 与 Spring Boot 3.x 最佳配套 |
| **Spring Boot 3.3.x** | JDK 17 基线，Jakarta EE 9+，对 Neo4j/Redis/RabbitMQ/MySQL 全部提供 Starter 自动配置 |

### 2.2 持久化层

| 技术 | 模块 | 存储内容 |
|------|------|---------|
| **Neo4j 5.x + Spring Data Neo4j** | L4 `infrastructure` → L3 `graph-core` | 宽图谱所有节点（Student/KP/Document/Event/Subject）和关系（MasteryRelation/PrerequisiteRelation/KnowledgeRelation/BelongsToRelation） |
| **Neo4j Java Driver 5.x** | L4 `infrastructure` | 复杂图算法（PageRank、社区发现、BFS 剪枝）的 Cypher 执行 |
| **MySQL 8.0 + Spring Data JPA** | L4 `infrastructure` | 用户/角色/权限、配置/策略/规则、导入/导出记录、审计日志、分享链接、公开审核、通知记录 |
| **Hibernate 6.4+** | L4 `infrastructure` | ORM 映射，原生支持 JDK 17 Record 类型和 Jakarta EE 9 |

> Spring Data Neo4j vs 原生 Driver 的使用边界：CRUD 操作使用 SDN Repository 抽象（`findNode`/`saveNode`/`saveEdge`），复杂图遍历和算法（`findSubgraph` BFS 展开、`computePageRank`）使用原生 Neo4j Java Driver 执行 Cypher。

### 2.3 消息与异步处理层

| 技术 | 队列 | 用途 |
|------|------|------|
| **RabbitMQ 3.x** | — | 消息中间件，镜像队列模式保证高可用 |
| **Spring AMQP 3.x** | `doc.ingestion` (direct) | PDF 文档异步解析流水线 |
| | `event.ingestion` (direct) | CSV 事件异步导入 |
| | `weight.decay` (topic) | 权重时间衰减批量计算 |
| | `notification` (fanout) | 异步通知发送（邮件/站内/Webhook） |
| | `llm.callback` (direct) | LLM 异步结果回调 |

**消息可靠性保障**：手动 ACK + 死信队列 (DLX) + Publisher Confirm + 消息持久化 (delivery_mode: 2)

### 2.4 缓存层

| 技术 | 缓存内容 | TTL |
|------|---------|-----|
| **Redis 7.x + Lettuce** | Session Token | 2h |
| | 热点 Cypher 查询结果 | 5min |
| | 图谱可视化快照 | 30min |
| | Prompt 模板渲染缓存 | 1h |
| | 限流计数器 (API Rate Limit) | 1min |
| **Caffeine 3.x** | 知识分类树（组合模式结构） | 永久（策略失效驱逐） |
| | LLM 模型配置 | 永久（事件驱动刷新） |
| | 剪枝策略配置 | 永久（事件驱动刷新） |

### 2.5 LLM/AI 集成层

| 技术 | 模块 | 用途 |
|------|------|------|
| **Spring AI 1.0.x** | L3 `ai-analysis` | Claude API 底层调用适配、流式响应处理、Token 计数 |
| **自研 LLMGateway** | L3 `ai-analysis` / M5-b | 模型路由（按 TaskType 分发到不同模型）、配额管理（日/周/月上限 Token 控制）、降级策略（超配额→备用模型→暂停非核心任务）、Prompt 模板版本管理、调用审计日志 |
| **Spring WebFlux WebClient** | L3 `ai-analysis` | 非阻塞 HTTP 客户端，支持 LLM 流式响应 `stream()` |
| **Resilience4j** | L3 `ai-analysis` | 熔断（LLM API 连续失败→打开熔断器）、重试（超时重试一次）、限流（保护 LLM API 不被突发流量打爆）、隔离（线程池隔离，避免 LLM 慢调用阻塞其他业务） |

> 架构依据：[logical-view.md §3.5.2] — ILLMGatewayService 的 9 个接口方法涵盖模型路由、配额查询、Prompt 模板版本化、调用重放等完整 LLM 网关能力。

### 2.6 文档处理层

| 技术 | 步骤 | 说明 |
|------|------|------|
| **Apache PDFBox 3.x** | LayoutAnalysisStep | 版面分析：正文/标题/表格/公式区域识别 |
| **LLM 驱动 (复用 Claude)** | NerStep | 实体抽取：概念/公式/定理/定义等知识点 |
| **LLM 驱动 (复用 Claude)** | ReStep | 关系抽取：引用/推导/包含/前置依赖 |
| **Spring Data Neo4j** | GraphImportStep | 图谱导入：创建节点和关系边 |
| **Tesseract** | 降级兜底 | 扫描版 PDF（无 OCR 识别层）的 OCR 识别 |
| **MinIO Java Client 8.x** | L4 `infrastructure` | PDF 原始文件存储、导出报告存储、备份文件存储 |

> M3 的文档解析采用 **流水线模式 (Pipeline Pattern)**，每个 `DocumentProcessStep` 是独立可替换的接口实现，通过 `DocumentContext` 在步骤间传递状态。

### 2.7 安全层

| 技术 | 用途 |
|------|------|
| **Spring Security 6.x** | 统一的认证和授权框架 |
| **JWT (jjwt 0.12.x)** | 无状态 Token，适配 K8s 多 Pod 场景（Session 状态下沉到 Redis） |
| **BCrypt** | 用户密码哈希（Spring Security 默认） |
| **Spring Security ACL / 自研 RBAC** | 五类角色（管理员/教师/学生/运维/运营）的权限映射与服务层强制鉴权 |
| **Jakarta Validation 8.x** | L2 应用层 `InputSanitizer` 的声明式参数校验 |

### 2.8 可观测性层

| 技术 | 指标/日志 | 用途 |
|------|---------|------|
| **Micrometer 1.x** | API QPS/延迟/错误率 | 应用指标采集门面 |
| **Prometheus** | Neo4j 慢查询、LLM 调用延迟、JVM GC | 指标存储与告警规则 |
| **Micrometer Tracing + OpenTelemetry** | Trace ID | 全链路追踪（API → 剪枝 → Neo4j Cypher → LLM Prompt → 响应组装） |
| **Logback + Logstash JSON Encoder** | JSON 结构化日志 | 推送 Loki 日志平台 |
| **Spring Boot Actuator** | /health/liveness, /health/readiness | K8s 健康探针 |
| **Grafana** | 6 个看板的仪表盘 | 业务/API/图谱/LLM/基础设施/数据存储可视化 |

### 2.9 定时任务与调度层

| 技术 | 任务 | Cron |
|------|------|------|
| **Spring @Scheduled** | 权重时间衰减计算 | 每日凌晨 02:00 |
| | 宽图谱健康度度量 | 每周一 03:00 |
| | 僵尸文档检测标记 | 每日凌晨 04:00 |
| **Quartz 2.x** | 图数据库全量备份 | 每日凌晨 02:00 |
| | LLM 配额周期重置 | 每日/每周一/每月1日 00:00 |
| | 运营数据定时报告 | 每月 1 日 08:00 |
| **自研 DAG 调度器** | 任务依赖编排 | 上游失败自动跳过下游 |

> 基础 Cron 任务使用 Spring `@Scheduled`，需要 DAG 依赖编排的任务（如"权重衰减"依赖"图谱增量更新"）使用 Quartz + 自定义 Job 依赖拓扑。

---

## 三、参考资料

| 文档 | 路径 | 内容 |
|------|------|------|
| 开发视图 | [dev-view.md](../../docs/design-view/dev-view/dev-view.md) | 19 个代码模块、四层架构、依赖矩阵 |
| 分层架构图 | [dev-view-architecture.md](../../docs/design-view/dev-view/dev-view-architecture.md) | 分层全景图、L3 内部依赖详图、接口实现分离图 |
| 组件图 | [dev-view-component-diagrams.md](../../docs/design-view/dev-view/dev-view-component-diagrams.md) | 组件接口契约、依赖关系全局矩阵 |
| 逻辑视图 | [logical-view.md](../../docs/design-view/logical-view/logical-view.md) | 51 个抽象实体、8 个模块、完整接口定义 |
| 类图设计 | [logical-view-class-diagrams.md](../../docs/design-view/logical-view/logical-view-class-diagrams.md) | 关键类建模、设计模式应用、枚举与值对象汇总 |
| 状态图 | [logical-view-state-diagrams.md](../../docs/design-view/logical-view/logical-view-state-diagrams.md) | 11 个核心实体状态机 |
| 物理视图 | [physical-view.md](../../docs/design-view/physical-view/physical-view.md) | 部署拓扑、通信协议、伸缩策略、高可用设计 |
| 用例视图 | [use-case-view.md](../../docs/design-view/use-case-view/use-case-view.md) | MVP 6 个核心用例、用例与接口映射 |
| 时序图 | [process-view-sequence-diagrams.md](../../docs/design-view/process-view/process-view-sequence-diagrams.md) | 7 个关键运行时场景 |
| MVP Story | [user-stories-mvp.md](../../docs/user-stories/user-stories-mvp.md) | MVP 阶段 6 个 User Story |