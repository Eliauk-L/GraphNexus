# DESIGN: 搭建可运行的项目框架

- **Change ID**: `init-platform`
- **关联**: `@.specs/init-platform/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 CONTEXT.md「已锁技术决策」锁定，不重选。

- **选定**：Java 17 + Spring Boot 3.3 + Maven 单模块（已锁）
- **后端框架**：Spring Boot 3.3.5 / Spring MVC 6.x / Spring Security 6.x
- **数据库**：Neo4j 5.x + MySQL 8.0（本次仅依赖，不连接）
- **缓存**：Redis 7.x (Lettuce) + Caffeine 3.x（本次仅依赖）
- **消息队列**：RabbitMQ 3.x + Spring AMQP（本次仅依赖）
- **部署**：本地开发（Podman Compose 待后续 change 启用）
- **关键依赖**：Lombok 1.18.34 / MapStruct 1.5.5 / JWT (jjwt 0.12) / Resilience4j 2.2 / Spring AI 1.0.0-M4 / SpringDoc OpenAPI 2.6
- **理由**：团队已基于 JDK 17 锁定全栈 Java 生态，`pom.xml` 已配置完毕，无需再选
- **明确排除**：不引入 Kotlin/Groovy（团队统一 Java）；不引入 Gradle（已锁 Maven）

---

## 0.5 既有架构对齐

> 本项目为从零搭建，非 brownfield。`docs/项目规范.md` 已定义完整架构蓝图，本 change 是将其首次落地。
> 因此本节用半成品对齐：标注「已有 vs 本次落地的界限」。

### 0.5.1 本次 change 触碰/创建的模块

```
已存在（之前会话已生成，本次不修改）：
- pom.xml                                        （禁动 · 依赖全量已配置）
- src/main/java/com/graphnexus/GraphNexusApplication.java  （需微调）
- src/main/resources/application.yml             （需微调）
- src/main/resources/application-dev.yml         （需微调）
- src/main/resources/logback-spring.xml          （需微调 traceId 占位符）

本次新建：
- src/main/java/com/graphnexus/api/              （L1 层目录树）
- src/main/java/com/graphnexus/application/      （L2 层目录树）
- src/main/java/com/graphnexus/infrastructure/   （L3 层目录树）
- src/main/java/com/graphnexus/common/           （公共模块全部类）
- src/test/java/com/graphnexus/architecture/     （ArchUnit 测试）

禁动清单（与本次无关，AI 不许碰）：
- pom.xml                                        （依赖全量已配置，禁止增删改）
- docs/项目规范.md                               （规范基准，不可改）
- docs/tech-stack-java.md                        （技术决策基准，不可改）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| Spring Boot 启动入口 | `GraphNexusApplication.java` 已有 | **沿用**，只加 `@SpringBootApplication` exclude 项 |
| 应用配置 | `application.yml` + `application-dev.yml` 已有 | **沿用**，只加 lazy/optional 配置 |
| 日志配置 | `logback-spring.xml` 已有 | **沿用**，只确认 traceId MDC 占位符 |
| 异常体系 | 没有 | **新建**（第一次落地） |
| Trace ID | 没有 | **新建**（第一次落地） |
| 统一响应体 | 没有 | **新建**（第一次落地） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 依赖注入：**沿用** 构造器注入 + @RequiredArgsConstructor（CONTEXT 已锁）
- 异常处理：**引入新模式** → ControllerAdvice 全局异常处理（第一次落地，Spring Boot 标准模式）
- 日志追踪：**引入新模式** → OncePerRequestFilter + MDC（Spring 标准模式，非自创）
- 响应格式：**引入新模式** → ApiResponse<T> record 包装（Java 17 record 是项目已锁的代码简化手段）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 无基础设施时启动策略：排除对应 AutoConfiguration | 用 `@SpringBootApplication(exclude = {...})` 逐个排除 | 精确控制，不引入额外依赖 | 新增基础设施需手动取消 exclude（一次性成本） |
| D2 | 包占位方式：`package-info.java` | 空 `.gitkeep` 文件 | `package-info.java` 是 Java 标准做法，可同时写包级 Javadoc 和 `@NonNullApi` 注解 | 创建 20+ 个文件，但每个只需 3~5 行 |
| D3 | 错误码设计：枚举 `ErrorCode` | 字符串常量 / 接口常量 | 枚举类型安全，IDE 可自动补全，switch 语句可穷举 | 新增错误码需改枚举类（但这是低频操作） |
| D4 | `ApiResponse` 用 Java `record` 而非普通 class | Lombok `@Data` class | record 是 Java 17 标准特性，不可变，字段自带 accessor，与项目「代码简化」目标一致 | record 不支持继承，但统一响应体不需要继承 |
| D5 | `GlobalExceptionHandler` 放在 `common/exception/` 而非 `api/` | 放在 `api/gateway/` | 异常处理是被所有 L1 Controller 共用的横切关注点，归入 common 更符合分层定义；`docs/项目规范.md` §2.4.1 也将其列入 common | 无实际代价 |
| D6 | ArchUnit 测试放在 `src/test` 而非独立模块 | 独立 `architecture-test` Maven 模块 | 单模块项目引入多模块会过度工程化；ArchUnit 作为 test scope 依赖已够用 | 架构规则变更和业务测试共享 CI 环节，无法独立跑 |
| D7 | 健康检查策略：排除所有基础设施健康指示器 | 用 `management.health.defaults.enabled=false` 全局禁用 | 排除具体组件而非全部关闭，Actuator 自身的 health 仍可用，便于后续逐个启用 | 新增基础设施需在 yml 中启用对应 health check——风险可控 |

---

## 2. 数据流 / 架构图

### 2.1 分层架构

```
                    HTTP Request
                         │
                         ▼
┌──────────────────────────────────────────────────┐
│  L1  api/                                         │
│      TraceIdFilter (OncePerRequestFilter)          │
│      → 生成/提取 traceId → MDC                    │
│      → Controller (预留，本次不创建)               │
│      → GlobalExceptionHandler (兜底)               │
└────────────┬─────────────────────────────────────┘
             │ 调用 (预留)
             ▼
┌──────────────────────────────────────────────────┐
│  L2  application/                                 │
│      → Service 接口 + Impl (预留，本次不创建)      │
└────────────┬─────────────────────────────────────┘
             │ 调用 (预留)
             ▼
┌──────────────────────────────────────────────────┐
│  L3  infrastructure/                              │
│      → neo4j/  mysql/  redis/  mq/  storage/      │
│      → llm/                                       │
│      (仅目录，本次不创建具体类)                     │
└──────────────────────────────────────────────────┘
       │                       │
       └───────────────────────┼──────────────┐
                               ▼              ▼
                         common/          common/
                     exception/        logging/
                     config/           monitoring/
```

### 2.2 异常处理流

```
Controller (L1) ──throw──> BusinessException
                                │
                                ▼
                      GlobalExceptionHandler
                      @RestControllerAdvice
                                │
                     ┌──────────┼──────────┐
                     ▼          ▼          ▼
              BusinessEx   ValidationEx   Exception
              → 4xx        → 400        → 500
                     │          │          │
                     └──────────┼──────────┘
                                ▼
                          ErrorResponse
                    (errorCode, errorMessage,
                     userTip, traceId, timestamp)
                                │
                                ▼
                          HTTP Response (JSON)
```

### 2.3 Trace ID 流

```
Client Request
  │
  ├─ 有 X-Trace-Id header? ──Yes──> 使用客户端传入的值
  │                                  │
  └─ No ──> UUID.randomUUID()       │
              │                      │
              └──────────────────────┘
                         │
                         ▼
              MDC.put("traceId", traceId)
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         Controller   Service    Repository
         (日志带     (日志带    (日志带
          traceId)    traceId)   traceId)
                         │
                         ▼
              ErrorResponse 中返回 traceId
                         │
                         ▼
              MDC.remove("traceId") (finally)
```

---

## 3. 关键状态机

本 change 无业务状态机。唯一的系统状态是 Actuator `/actuator/health`：`UP` / `DOWN`，由 Spring Boot 自动管理。

---

## 4. ADR 索引

本 change 不涉及不可逆架构决策。所有决策（D1~D7）均为实现选择，可随时调整，不单独写 ADR。

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **Spring AI Milestone 仓库代理/墙导致依赖拉取失败** | `mvn compile` 失败，所有后续 DEV 阻塞 | 中 | ① 已配置 Maven mirror（`settings.xml`）指向阿里云/华为云；② Spring AI 依赖实际在 compile 时不需要（只需 classpath 上有 jar），可临时注释该依赖让框架先跑起来 |
| R2 | **Lombok + MapStruct 注解处理器冲突** | 编译警告/错误，mapstruct 生成的 impl 类缺失 | 低 | `pom.xml` 已按正确顺序配置 annotationProcessorPaths（lombok → mapstruct），若遇冲突可调顺序 |
| R3 | **Neo4j AutoConfiguration 排除不完全** | 启动时 Neo4j Driver 仍然尝试连接，抛异常阻止启动 | 中 | 需要排除 `Neo4jAutoConfiguration` + `Neo4jDataAutoConfiguration` + `Neo4jHealthContributorAutoConfiguration`；同时在 `application-dev.yml` 设置 `spring.neo4j.pool.enabled=false` 和 `management.health.neo4j.enabled=false`——双保险 |
| R4 | **ArchUnit 基线缺失——以后新 change 没人写架构测试** | 分层约束无自动校验，架构退化 | 中 | 本次在 `src/test` 建立 ArchUnit 测试样例，后续 change 的 TASK 阶段可复制模板 |
| R5 | **`package-info.java` 数量多导致第一次 MR 看起来很重** | 代码 review 疲劳 | 低 | 每个文件仅 3~5 行（包声明 + Javadoc + NonNullApi 注解），按 package 分组 commit，commit message 写清楚"仅包骨架" |

---

## 6. 不在范围

- **不设计任何 API 端点** — Controller 类不创建（仅建目录）
- **不设计数据库 Schema** — 无 DDL、无 Neo4j 约束/索引
- **不设计 LLM Prompt 模板** — LLM 模块仅建目录
- **不设计消息队列 Exchange/Queue/Binding** — MQ 模块仅建目录
- **不设计 MinIO Bucket 策略** — Storage 模块仅建目录
- **不设计安全规则** — Spring Security 依赖已引入但无任何配置（所有端点开放），安全规则在后续 change 单独设计

---

## 9. 架构沉淀建议

> 本 change 首次将 `docs/项目规范.md` 的抽象设计落地为代码骨架，以下为 `A-evolve` 可扫的素材。

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `common/exception/BusinessException.java` | 统一业务异常（errorCode + userTip） | 所有 L2 Service 层抛业务错误 | 后续所有 change 直接 throw，不自行 new RuntimeException |
| `common/exception/ErrorResponse.java` | 统一错误响应体（record） | GlobalExceptionHandler 使用 | 所有异常路径返回此格式 |
| `common/exception/GlobalExceptionHandler.java` | @RestControllerAdvice 全局异常处理 | 所有 HTTP 请求异常路径 | 新增异常类型只需加 `@ExceptionHandler` 方法，不改调用方代码 |
| `common/logging/TraceIdFilter.java` | OncePerRequestFilter 全链路 Trace ID | 每个 HTTP 请求 | 日志自动带 traceId，后续接入 ELK/Jaeger 时只需改 Logback encoder |
| `ApiResponse<T>` + `PageResult<T>` | 统一响应体 + 分页体（record） | 所有 Controller 返回 | 禁止 Controller 返回裸数据，必须用 ApiResponse 包装 |

### 9.2 新增的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 无基础设施启动策略 | `@SpringBootApplication(exclude = {...})` | 所有本地开发环境 | 低——引入新基础设施时取消对应 exclude 即可，纯配置变更 |
| 包占位文件格式 | `package-info.java` + `@NonNullApi` | 所有新建包 | 低——改为 `.gitkeep` 只需批量替换 |
| 错误码编码规则 | 来源(A/B/C) + 4位数字 | 所有错误场景 | 中——已有业务代码的错误码需全部重编号 |

### 9.3 新增的跨模块契约

```
- 所有 HTTP 异常响应体必须包含 5 个字段：errorCode / errorMessage / userTip / traceId / timestamp
- 所有 HTTP 正常响应体必须包含 5 个字段：code / message / data / traceId / timestamp
- 所有分页响应体必须包含 4 个字段：list / total / pageNum / pageSize
```

### 9.4 依赖变动

无新增依赖。所有依赖已在 `pom.xml` 中预配置，本次仅首次让它们被实际 import 使用。

### 9.5 禁动清单变化

```
- 新增禁动：pom.xml（依赖变更必须走独立 change，不可在业务 change 中顺手加依赖）
- 新增禁动：docs/项目规范.md（规范变更需全员评审，不可在实现过程中修改）
- 新增禁动：docs/tech-stack-java.md（技术选型变更需架构评审）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。