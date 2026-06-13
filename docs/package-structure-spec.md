# GraphNexus 包结构规范

> 版本：v1.0 | 日期：2026-06-12 | 基于 `refine-package-structure` DESIGN.md
>
> 本文档是对 `docs/项目规范.md` §1.4.2 和 §2.2 的细化补充，定义四层架构下每一层每个模块的标准子包模板。
> 所有 DEV 任务须按此模板放置代码。

---

## 1. 四层架构总览

```
L1  api/            ──调用──▶  L2  application/  ──调用──▶  L3  infrastructure/
 │                               │                               │
 └───────────────────────────────┼───────────────────────────────┘
                                 ▼
                           common/（被所有层依赖）
```

| 层 | 目录 | 职责 | 依赖方向 |
|:--|:--|:--|:--|
| **L1 API 层** | `api/` | 接收 HTTP 请求，参数校验，DTO/VO 转换，调用 L2 服务 | 依赖 L2 |
| **L2 应用层** | `application/` | 业务逻辑编排，事务管理，调用 L3 基础设施 | 依赖 L3 |
| **L3 基础设施层** | `infrastructure/` | 数据持久化，外部服务调用，消息通信，缓存 | 依赖 common |
| **公共模块** | `common/` | 横切关注点：异常、日志、监控、公共配置 | 被所有层依赖 |

**禁止**：L1 不直接调用 L3；L3 不包含业务逻辑。

---

## 2. L1 API 层包模板

### 2.1 标准子包

```
api/<module>/
├── controller/          # REST 控制器（@RestController）
│   └── XxxController.java
├── dto/                 # DTO + VO 对象
│   ├── XxxVO.java       #   响应展示对象
│   ├── XxxRequest.java  #   请求体 DTO
│   └── ...
└── package-info.java
```

### 2.2 模块清单

| 模块 | 路径 | 职责 |
|------|------|------|
| gateway | `api/gateway/` | 安全配置（Spring Security、JWT 过滤器、CORS） |
| document | `api/document/` | 文档上传、解析、状态查询 API |
| graph | `api/graph/` | 图构建、图查询 API |
| analysis | `api/analysis/` | 图分析（PageRank、归因分析等）API |
| query | `api/query/` | 智能查询（NL2Cypher 等）API |
| basic | `api/basic/` | 基础数据（健康检查等）API |

### 2.3 特殊说明

- `gateway/` 模块不含 `dto/`，使用 `config/` 子包存放安全配置类
- DTO 与 VO 统一放 `dto/` 子包，通过类名后缀区分（`XxxRequest` vs `XxxVO`）

---

## 3. L2 Application 层包模板

### 3.1 标准子包（基础模板，所有模块必须遵循）

```
application/<module>/
├── service/             # 服务接口（interface）
│   └── XxxService.java
├── service/impl/        # 服务实现（@Service + Impl 后缀）
│   └── XxxServiceImpl.java
├── model/               # BO + Query + 领域值对象
│   ├── XxxBO.java       #   Business Object
│   ├── XxxQuery.java    #   查询条件对象（参数 > 2 时使用）
│   └── ...              #   其他领域模型（如 ParseResult）
└── package-info.java
```

### 3.2 模块扩展（按需添加，不进基础模板）

模块可在基础模板之上添加扩展子包。示例：

| 模块 | 扩展子包 | 内容 | 添加时机 |
|------|----------|------|----------|
| document | `parser/` | `DocumentParser` 接口 + `PdfBoxDocumentParser` 实现 | `document-process-pdf-minimal`（ADR-001 预见） |
| graph | `strategy/`（预留） | 图剪枝策略接口 + 实现 | 当出现 ≥2 种剪枝策略时添加 |
| query | `builder/`（预留） | Cypher 查询构建器 | 当查询构建逻辑复杂到需要独立类时添加 |

**扩展规则**：
1. 扩展子包命名用单数名词（`parser` 非 `parsers`）
2. 接口与实现同包（如 `parser/DocumentParser.java` + `parser/PdfBoxDocumentParser.java`）
3. 新增扩展子包需在对应 change 的 DESIGN.md § 2 中声明

### 3.3 模块清单

| 模块 | 路径 | 职责 |
|------|------|------|
| document | `application/document/` | PDF 解析编排、文档图谱构建、NLP 抽取流水线 |
| graph | `application/graph/` | 图构建、图查询、图更新业务逻辑 |
| analysis | `application/analysis/` | PageRank、社区发现、归因分析算法编排 |
| query | `application/query/` | NL2Cypher、结果聚合、上下文组装 |
| basic | `application/basic/` | 健康检查、系统状态 |
| llmgateway | `application/llmgateway/` | LLM 模型路由、配额控制、降级策略、调用审计 |

### 3.4 放置规则速查

| 你要写的代码 | 放哪里 |
|-------------|--------|
| Service 接口 | `<module>/service/XxxService.java` |
| Service 实现 | `<module>/service/impl/XxxServiceImpl.java` |
| 业务输出对象（BO） | `<module>/model/XxxBO.java` |
| 查询条件对象（>2 参数） | `<module>/model/XxxQuery.java` |
| 领域值对象/结果对象 | `<module>/model/XxxResult.java` |
| 模块特有的策略/适配器接口+实现 | `<module>/<扩展包>/` |

---

## 4. L3 Infrastructure 层包模板

### 4.1 子包规则

```
infrastructure/<技术域>/
├── config/              # 配置类（@Configuration, @ConfigurationProperties）
│   └── ...
├── <module>/            # 按业务模块分组的数据对象（保持扁平，文件 < 5 时不拆子包）
│   ├── XxxDO.java       #   Data Object（与数据库表对应）
│   ├── XxxRepository.java  # Spring Data Repository 接口
│   ├── XxxStatus.java   #   枚举（状态机等）
│   └── ...
└── package-info.java
```

### 4.2 各技术域骨架子包

| 技术域 | 路径 | 子包 | 内容说明 |
|--------|------|------|----------|
| MySQL | `infrastructure/mysql/` | `config/`、`<module>/` | JPA 审计/数据源配置；按模块分组的 DO + Repository + 枚举 |
| Neo4j | `infrastructure/neo4j/` | `config/`、`node/`、`edge/`、`repository/` | Driver 配置；图节点实体；图边实体；SDN Repository |
| Redis | `infrastructure/redis/` | `config/` | Lettuce 连接配置、CacheManager、序列化策略 |
| RabbitMQ | `infrastructure/mq/` | `config/`、`queue/`、`exchange/` | 连接/消息转换配置；Queue 声明；Exchange + Binding |
| MinIO | `infrastructure/storage/` | `config/` | MinIO 客户端配置、Bucket 策略；`FileStorageService` 放根路径 |
| LLM API | `infrastructure/llm/` | `config/`、`client/` | WebClient 配置/Prompt 模板；模型调用/响应解析 |

### 4.3 放置规则速查

| 你要写的代码 | 放哪里 |
|-------------|--------|
| Spring `@Configuration` 类 | `<技术域>/config/XxxConfig.java` |
| `@ConfigurationProperties` 类 | `<技术域>/config/XxxProperties.java` |
| 数据库实体（DO） | `mysql/<module>/XxxDO.java` |
| Spring Data JPA Repository | `mysql/<module>/XxxRepository.java` |
| 数据库相关枚举 | `mysql/<module>/XxxStatus.java` |
| 图节点实体 | `neo4j/node/XxxNode.java` |
| 图边实体 | `neo4j/edge/XxxEdge.java` |
| Neo4j Repository | `neo4j/repository/XxxRepository.java` |
| 缓存管理 | `redis/config/` |
| 消息队列定义 | `mq/queue/`、`mq/exchange/` |
| LLM HTTP 客户端 | `llm/client/` |
| 文件操作服务 | `storage/FileStorageService.java`（根路径） |

---

## 5. Common 公共模块

```
common/
├── ApiResponse.java         # 统一 API 响应体
├── PageResult.java          # 统一分页响应体
├── config/                  # 公共配置（Jackson、CORS、Actuator）
├── exception/               # 统一异常体系（BusinessException、ErrorCode、ErrorResponse、GlobalExceptionHandler）
├── logging/                 # 日志（TraceIdFilter、MDC）
└── monitoring/              # 监控（Micrometer 指标注册）
```

> common 下的类已在 `init-platform` change 中稳定，本次 `refine-package-structure` 不修改。

---

## 6. 数据对象转换链

```
前端 JSON 请求
  → L1 api/<module>/dto/XxxRequest.java    （@Valid 校验）
  → L2 application/<module>/model/XxxBO.java  / XxxQuery.java
  → L3 infrastructure/mysql/<module>/XxxDO.java  / XxxRepository.java
  → 数据库

数据库返回
  → L3 XxxDO.java
  → L2 XxxBO.java / XxxResult.java
  → L1 api/<module>/dto/XxxVO.java
  → 前端 JSON 响应
```

**禁止跨层直接传 DO**：L1 不 import L3 的 DO；L2 不把 DO 直接返回给 L1。

---

## 7. 反模式（已消除的结构债务）

以下是本次 `refine-package-structure` 消灭的模式，新代码**禁止**再现：

| ❌ 反模式 | ✅ 正确做法 |
|----------|------------|
| 把 Service 接口、实现、BO、Parser 全部塞在 `service/` 包下 | 按职责分入 `service/`（接口）、`service/impl/`（实现）、`model/`（BO）、`<扩展包>/`（如 parser） |
| 配置类（`@Configuration`）和数据对象（DO）混放在模块根路径 | 配置类统一放 `config/` 子包 |
| L3 config 类被 L2 直接 import（绕过 Spring 组件扫描） | Spring `@Configuration` 由组件扫描发现，不需要显式 import |
| 测试类放在与被测类不同的包路径 | test 包路径 = main 包路径镜像 |
| 只建空目录不写 `package-info.java` | 每个包必须含 `package-info.java`（含 Javadoc + @NonNullApi） |

---

## 8. 维护约定

- 新增业务模块 → 按本文档 §2（L1）和 §3（L2）创建对应子包骨架
- 新增模块扩展子包 → 在对应 change 的 DESIGN.md § 2 中声明，命名用单数
- 本文档更新触发时机 → 任何 change 引入/修改子包结构时，更新对应章节
- 与 `docs/项目规范.md` 的关系 → 本文档是 §1.4.2/§2.2 的细化补充；若冲突，以 `docs/项目规范.md` 为准，但应发起评审同步更新