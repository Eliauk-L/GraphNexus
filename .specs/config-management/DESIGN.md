# DESIGN: 配置管理模块 — 运行时动态配置管理

- **Change ID**: `config-management`
- **关联**: `@.specs/config-management/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 技术栈已在 `CONTEXT.md` 中锁定，直接沿用，不重新选型。

- **选定**：Java 17 + Spring Boot 3.3.x（既有栈）
- **前端**：Vue 3 + TypeScript + Vite + Naive UI（既有）
- **后端**：Spring Boot 3.3.x / Spring Data JPA / Spring Security / Spring Web
- **数据库**：MySQL 8.0（既有 `graphnexus` 库，新增 `system_config` 表）
- **缓存**：Caffeine 本地缓存（既有，无需引入新依赖）
- **关键依赖**：Spring Boot Configuration Processor（既有）、Lombok（既有）、jackson-databind（既有，用于 `validation_rule` JSON 解析）
- **理由**：配置管理模块完全在既有技术栈能力范围内，无需新增任何 Maven 依赖或基础设施
- **明确排除**：不引入 Spring Cloud Config / Spring Cloud Context（过度工程）；不引入 Nacos/Apollo（项目规模不匹配）

---

## 0.5 既有架构对齐

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 确认的实际文件）：
- src/main/java/.../application/analysis/fusion/config/FusionProperties.java（要通过 setter 注入 DB 值）
- src/main/java/.../application/analysis/fusion/config/FuzzyMatchProperties.java（同上）
- src/main/java/.../application/analysis/fusion/config/TimeDecayProperties.java（同上）
- src/main/java/.../application/query/chat/config/QueryProperties.java（同上）
- src/main/java/.../infrastructure/neo4j/gds/config/MetricsProperties.java（同上）
- src/main/java/.../application/file/.../mineru/config/MinerUProperties.java（同上）
- src/main/java/.../application/query/prompt/service/PromptTemplateService.java（loadTemplate 改为优先 DB）
- src/main/java/.../application/graph/construction/extract/ExtractionPromptBuilder.java（loadFewshot 改为优先 DB）
- src/main/java/.../infrastructure/llm/client/Langchain4jLlmGateway.java（model/temperature/maxTokens 改为可运行时重建 ChatModel）
- src/main/java/.../application/query/chat/intent/LlmIntentRecognitionStrategy.java（间接：通过 PromptTemplateService）

新增模块：
- api/config/controller/ConfigController.java
- api/config/dto/ConfigResponse.java / UpdateConfigRequest.java
- application/config/service/ConfigService.java + impl/ConfigServiceImpl.java
- application/config/loader/ConfigLoader.java
- application/config/validation/ConfigValidator.java
- infrastructure/mysql/config/entity/SystemConfigDO.java
- infrastructure/mysql/config/repository/SystemConfigRepository.java
- frontend/src/views/settings/SettingsConfigPage.vue
- frontend/src/api/config.ts
- frontend/src/stores/configStore.ts

禁动清单（与本次无关，AI 不许"顺手"碰）：
- pom.xml（不新增依赖）
- docs/项目规范.md / docs/tech-stack-java.md（禁动清单中的文件）
- application.yml / application-dev.yml / application-prod.yml（保留为 fallback，不删除现有配置项）
- prompts/*.md（保留为 fallback，不删除）
- 任何 Service/Strategy 的业务逻辑（只改配置读取方式，不改业务逻辑）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 数据库持久化 | `JpaRepository` + DO 模式（既有 `infrastructure/mysql/*/entity/*DO.java`）| **沿用** |
| 本地缓存 | Caffeine（既有 `MetricsServiceImpl` 用 `Cache<String, List<MetricResult>>`）| **沿用** |
| 配置属性绑定 | `@ConfigurationProperties`（既有 7 个 Properties 类）| **沿用**（只通过 setter 注入新值，不新增字段） |
| 权限控制 | `@PreAuthorize("hasRole('ADMIN')")`（既有 Controller 类级别注解）| **沿用** |
| 异常处理 | `BusinessException` + `GlobalExceptionHandler`（既有）| **沿用** |
| API 响应格式 | `ApiResult<T>` + `PageResult<T>`（既有）| **沿用**（`ApiResult`） |
| 构造器注入 | `@RequiredArgsConstructor` + `private final`（既有范式）| **沿用** |
| 前端 HTTP 客户端 | `frontend/src/api/client.ts`（既有 axios 实例）| **沿用** |
| 前端状态管理 | Pinia（`frontend/src/stores/`）| **沿用** |
| 前端组件库 | Naive UI（既有）| **沿用** |
| 提示词加载 | `ResourceLoader.getResource("classpath:/prompts/...")` | **扩展**：改为双源（DB优先→classpath fallback） |
| LLM 模型管理 | `@Value` + `@PostConstruct` 创建 `OpenAiChatModel` | **扩展**：新增 `reinitialize()` 方法供 Apply 调用 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：沿用 Repository 模式（SystemConfigRepository extends JpaRepository）
- 错误处理：沿用 BusinessException + ErrorCode 枚举
- API 路由：沿用 /api/v1/config 风格
- 分层架构：沿用 L1(api) → L2(application) → L3(infrastructure) 四层
- 缓存管理：沿用 Caffeine Cache 模式（参考 MetricsServiceImpl）
- 事件通知：不引入（Apply 直接调用 setter，无需事件总线）
- 配置值注入 Spring：引入新模式 → 直接通过 @ConfigurationProperties bean setter 注入 DB 值（理由：Spring 原生不支持运行时 Properties 重绑定，直接 setter 是最小侵入方案）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | **启动加载**：ApplicationRunner 通过 Spring Data JPA 从 DB 加载配置 → 更新所有 @ConfigurationProperties Bean setter + Caffeine 缓存 | ① EnvironmentPostProcessor + raw JDBC（bean 创建前注入 PropertySource）；② 每个消费者自己查 ConfigService | 选 ApplicationRunner：① 无需 raw JDBC、可利用既有 Repository + DI；② 比方案 ① 晚几百 ms（ApplicationRunner 在 Web Server 启动后执行），但该窗口内请求使用 yml 默认值属可接受行为；方案 ③ 改动面太大 | 极短暂的启动窗口（毫秒级）内 DB 值未加载，请求使用 yml 默认值。对 v1 可接受——这是首次部署 DB 为空时的正常行为 |
| D2 | **运行时 Apply**：ConfigService.apply() 直接从 DB 重载 → 更新 @ConfigurationProperties Bean setter → 刷新 Caffeine → 重建 LLM ChatModel（如需） | ① Spring Cloud `@RefreshScope` + `ContextRefresher`；② 销毁并重建 Bean；③ 每个消费者读 ConfigService 而非注入 Properties | 选直接 setter：① 不引入 Spring Cloud 依赖；② Bean 销毁重建会破坏既有引用（已在几十个 Service 中注入）；③ 改动面太大。直接 setter 利用 Lombok @Data 已有的 setter，所有注入方立即看到新值 | 并发修改场景下（极罕见），一个 Properties 对象的多个字段可能被读到"中间态"（部分字段更新、部分未更新）。Apply 是低频操作（日均≤5次），且 Properties 各字段相互独立，影响可忽略 |
| D3 | **提示词加载**：`PromptTemplateService.loadTemplate()` 改为先查 ConfigService DB 缓存 → 未自定义则 fallback classpath 文件 | ① Prompt 存 DB 后删除 classpath 文件；② 使用独立的 PromptRepository 不经过 ConfigService | 选双源 fallback：① classpath 文件作为初始模板和回滚基线必须保留；② ConfigService 统一管理所有配置，不另外建仓储 | 提示词加载多一层缓存查询（Caffeine get O(1)），延迟可忽略。需修改 `PromptTemplateService` 和 `ExtractionPromptBuilder` 两个类的 load 逻辑 |
| D4 | **LLM 模型参数热更新**：`Langchain4jLlmGateway` 新增 `reinitialize(model, temperature, maxTokens)` 方法，Apply 检测到模型参数变更时调用 | ① 每次 chat 调用前重新创建 ChatModel；② 通过 Spring 环境变量注入 + `@RefreshScope` | 选 on-demand 重建：① 每次重建的开销（TLS 握手、http client 初始化）在每次调用时不可接受；② 同 D2 不引入 Spring Cloud。Apply 时重建一次，正常情况下（未改模型参数）不重建 | `reinitialize()` 创建新 ChatModel 期间（毫秒级），并发的 LLM 调用可能读到旧的 `chatModel` 引用。`chatModel` 字段用 `volatile` 修饰确保可见性 |
| D5 | **配置校验**：PUT 时执行类型校验（NUMBER→parseDouble / BOOLEAN→"true"/"false"）+ 范围校验（validation_rule JSON min/max）+ 非空校验（required=true） | ① 仅前端校验；② 引入 Hibernate Validator + 自定义 ConstraintValidator | 选后端校验：① 仅前端校验不安全（API 可绕过）；② 引入 Bean Validation 框架对 ≤ 100 条配置过度。校验逻辑 ≤ 50 行，手写即可 | 校验规则扩展（如正则匹配、依赖校验 A>B）需改代码（v2 路线图已列） |
| D6 | **配置初始化（Seed）**：DDL 创建 `system_config` 表后，INSERT 所有可管理配置项（`config_value = NULL`，表示未自定义，使用 yml 默认值） | ① 启动时动态扫描 yml 并自动 INSERT；② 首次部署手动执行 SQL | 选手动 Seed SQL：① 启动时动态 INSERT 复杂（需解析 yml 树结构）；② 配置项列表稳定（≤40 条），一次性 SQL 脚本即可。后续新增配置项通过新 SQL 追加 | 新增 yml 配置项时需同步追加 seed 行（v1 手动，v2 可考虑自动化） |
| D7 | **前端「系统配置」页面布局**：左侧分类 Tab（业务参数/提示词/模型配置）+ 右侧配置项列表 + 底部固定「应用配置」按钮（含未应用 Badge） | ① 单页全列表不分 Tab；② 侧边栏二级菜单 | 选 Tab 布局：三类配置性质差异大（数值输入 vs 代码编辑器 vs 文本输入），分离展示减少视觉噪音。极简风格，对齐现有管理后台 | 三类 Tab 切换时列表组件需重新渲染，性能影响可忽略（≤40 条配置） |

---

## 2. 数据流 / 架构图

### 2.1 启动时配置加载流程

```
Spring Boot 启动
      │
      ▼
application.yml 加载 → @ConfigurationProperties Bean 绑定 yml 默认值
      │
      ▼
ApplicationRunner(ConfigLoader) 执行（@Order(0)）
      │
      ├─→ SystemConfigRepository.findAll()
      │       │
      │       ▼
      │   system_config 表（MySQL）
      │       │
      │       ▼
      │   筛选 config_value IS NOT NULL 的记录
      │       │
      │       ├─→ 更新 FusionProperties bean（setThreshold/setStrategy/...）
      │       ├─→ 更新 QueryProperties bean（setWeakThreshold/setMaxInputTokens/...）
      │       ├─→ 更新 MetricsProperties bean（setTtlMinutes/...）
      │       ├─→ 更新 MinerUProperties bean（setEnabled/...）
      │       ├─→ 更新 FuzzyMatchProperties bean（setAlpha/...）
      │       └─→ 更新 TimeDecayProperties bean（setFactor）
      │
      └─→ Caffeine 缓存全量加载（key=configKey, value=SystemConfigDO）
              │
              ▼
         日志："Loaded N configs from database"
              │
              ▼
         Web Server 正常接受请求（后续请求使用 DB 值）
```

### 2.2 配置修改 → Apply 流程

```
ADMIN 用户（前端 SettingsConfigPage）
      │
      ▼
PUT /api/v1/config/fusion.kp-matching.threshold
  {"configValue": "0.80"}
      │
      ▼
ConfigController.update() → ConfigValidator.validate(NUMBER, 0.80, {min:0,max:1})
      │
      ▼
ConfigServiceImpl.update()
  ├─→ SystemConfigRepository.save(DO)     // 持久化到 MySQL
  └─→ Caffeine cache.put(key, DO)          // 更新缓存
  ⚠️ 此时 applied=false（DB 值 ≠ 内存中 FusionProperties 值）
      │
      ▼
前端显示 Badge："1 项待应用"
      │
      ▼
ADMIN 点击「应用配置」→ POST /api/v1/config/apply
      │
      ▼
ConfigServiceImpl.apply()
  ├─→ SystemConfigRepository.findAll()     // 全量从 DB 重载
  ├─→ 逐条对比：DB config_value vs 当前 Properties bean 值
  ├─→ 更新所有 @ConfigurationProperties bean（调用 setter）
  ├─→ 检测 llm.model / llm.temperature / llm.max-tokens 是否变更
  │       └─→ 是 → Langchain4jLlmGateway.reinitialize(model, temp, maxTokens)
  ├─→ Caffeine 缓存全量刷新（putAll）
  └─→ 日志："Config applied: N updated, M unchanged"
      │
      ▼
HTTP 200 → 前端清除 Badge → 所有后续请求使用新配置值
```

### 2.3 提示词加载流程（双源）

```
业务代码调用 PromptTemplateService.loadTemplate("student-diagnosis-system")
      │
      ▼
构建 configKey = "prompt.student-diagnosis-system"
      │
      ▼
ConfigService.getPromptText(configKey)
      │
      ├─→ Caffeine 缓存命中 + config_value != null？
      │       │ YES → 返回 DB 中的提示词文本 ✅
      │       │ NO  ↓
      │       └─→ ResourceLoader.getResource("classpath:/prompts/student-diagnosis-system.md")
      │               │
      │               └─→ 返回 classpath 文件内容（fallback）
      │
      └─→ 返回最终文本 → 继续变量替换 → 组装 PromptPair
```

### 2.4 模块依赖图

```
┌──────────────────────────────────────────────────┐
│                    L1 API                         │
│  api/config/controller/ConfigController.java      │
│  @PreAuthorize("hasRole('ADMIN')")                │
└─────────────────────┬────────────────────────────┘
                      │ 注入
┌─────────────────────▼────────────────────────────┐
│                  L2 Application                    │
│  application/config/service/ConfigService          │
│  application/config/loader/ConfigLoader            │
│  application/config/validation/ConfigValidator     │
│                                                    │
│  依赖（仅注入接口/Properties）:                      │
│  ├─→ FusionProperties / QueryProperties / ...     │
│  ├─→ Langchain4jLlmGateway（reinitialize）         │
│  ├─→ SystemConfigRepository（L3）                  │
│  └─→ Caffeine Cache（本地）                        │
└─────────────────────┬────────────────────────────┘
                      │ 注入
┌─────────────────────▼────────────────────────────┐
│               L3 Infrastructure                    │
│  infrastructure/mysql/config/                      │
│  ├─ entity/SystemConfigDO.java                     │
│  └─ repository/SystemConfigRepository.java         │
│                                                    │
│  infrastructure/llm/client/                        │
│  └─ Langchain4jLlmGateway.java                     │
│     └─ reinitialize(model, temp, maxTokens)  [新增] │
└──────────────────────────────────────────────────┘

依赖方向：全部自上而下，ConfigService 不反向注入 graph/query/analysis Service
```

---

## 3. ADR

本次涉及 3 个可逆性较低的架构决策，分别记录为独立 ADR：

| ADR | 标题 | 文件 |
|-----|------|------|
| ADR-041 | 配置存储与优先级模型 | `@.specs/adr/ADR-041-config-storage-priority.md` |
| ADR-042 | 运行时配置热重载机制 | `@.specs/adr/ADR-042-runtime-config-reload.md` |
| ADR-043 | 提示词模板双源加载 | `@.specs/adr/ADR-043-prompt-dual-source-loading.md` |

详见各 ADR 文件。

---

## 4. 风险

| # | 风险 | 类型 | 概率 | 影响 | 缓解方案 |
|---|---|---|---|---|---|
| R1 | **并发读写 Properties Bean**：Apply 更新 setter 中途，业务线程读到"半更新"状态 | 实现风险 | 低 | 低 | Properties 各字段相互独立（如 threshold 和 strategy 无关），半更新不影响正确性。Apply 操作本身在单线程中顺序执行，耗时 < 100ms。接受此风险 |
| R2 | **ChatModel 重建失败**：Apply 时 LLM 参数（如 model 名）无效 → `OpenAiChatModel.build()` 抛异常 | 上线风险 | 低 | 高 | `reinitialize()` 用 try-catch 包裹：失败时保留旧 `chatModel` 引用 + ERROR 日志 + 返回失败信息给前端。系统继续用旧模型运行 |
| R3 | **启动时 DB 不可达**：ConfigLoader 启动时 MySQL 连接失败 → 所有配置使用 yml 默认值 | 上线风险 | 低 | 中 | ConfigLoader 捕获异常 + WARN 日志 + 不阻断启动。业务使用 yml 默认值正常运行。DB 恢复后需手动 Apply 一次加载 DB 值 |
| R4 | **config_value 格式错误**：管理员通过 API 直接写入非法格式值（如 NUMBER 类型填了 "abc"），绕过了前端校验 | 实现风险 | 中 | 低 | 后端 PUT 端点强制校验（ConfigValidator），非法值拒绝写入。已存在 DB 中的脏数据（如直接 SQL 修改）在启动加载时 WARN 跳过 + 使用默认值 |
| R5 | **配置项膨胀**：随着业务增长，可配置项从 40 → 200+，seed SQL、ConfigLoader 的 setter 映射、前端页面性能均面临压力 | 长期债务 | 中 | 低 | v1 的 40 条规模下手动维护可接受。若增长至 100+，v2 引入：① `@ConfigBinding` 注解自动发现 + setter 映射；② 前端分页/搜索；③ seed SQL 自动生成工具 |
| R6 | **配置漂移**：dev/prod 环境的 system_config 表各自独立修改，长期后同一个 key 在两环境差异大，排查问题困难 | 长期债务 | 高 | 中 | v1 接受此风险（已排除多环境同步）。缓解：运维文档记录标准配置集；出问题时对比两环境 `/api/v1/config` 输出 |

---

## 5. 不在范围内（本次 DESIGN 不解决）

- 配置加密存储（敏感值仍走 yml 环境变量）
- 跨实例配置同步（单实例部署假设下的 Apply 不广播到其他节点）
- 配置导出/导入 API（v2）
- 配置变更审计日志写入独立表（v1 仅 INFO 日志输出，v2 引入 `config_audit_log` 表）
- 配置回滚到历史版本（v2）
- `@ConfigurationProperties` 字段到 config_key 的自动映射（v1 手动维护 ConfigLoader 中的映射表）
- EnvironmentPostProcessor 方案：经评估后放弃（需 raw JDBC + 启动早期 DB schema 可能不存在），改用 ApplicationRunner

---

## 6. 数据库 DDL

```sql
-- system_config 表：存储可动态管理的系统配置项
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT DEFAULT NULL COMMENT '自定义值，NULL表示使用yml默认值',
    config_type VARCHAR(16) NOT NULL DEFAULT 'STRING' COMMENT 'NUMBER|STRING|BOOLEAN|TEXT',
    category VARCHAR(32) NOT NULL COMMENT 'BUSINESS_PARAM|LLM_PROMPT|LLM_MODEL',
    config_name VARCHAR(64) NOT NULL COMMENT '中文显示名',
    description VARCHAR(256) DEFAULT '' COMMENT '配置说明',
    default_value VARCHAR(512) DEFAULT NULL COMMENT 'yml默认值，前端展示用',
    required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    validation_rule JSON DEFAULT NULL COMMENT '校验规则JSON，如{"min":0,"max":1}',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '前端展示排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_category_sort (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';
```

### Seed Data（初始 INSERT，config_value 全部 NULL = 使用 yml 默认值）

```
BUSINESS_PARAM 类（约 18 项）:
  fusion.kp-matching.strategy, fusion.kp-matching.threshold,
  fusion.weight.strategy, fusion.rollback.weight-tolerance,
  fusion.strategy.fuzzy.alpha, fusion.strategy.fuzzy.beta, fusion.strategy.fuzzy.gamma,
  fusion.strategy.time-decay.factor,
  graph.metrics.cache.ttl-minutes, graph.metrics.cache.max-size,
  graph.metrics.page-rank.max-iterations, graph.metrics.page-rank.damping-factor,
  mineru.enabled, mineru.api.poll-timeout, mineru.api.poll-interval,
  query.token-budget.max-input-tokens, query.token-budget.chars-per-token,
  query.pruning.weak-threshold, query.pruning.max-prerequisite-hops,
  query.retry.max-retries, query.retry.retry-delay-ms,
  query.output-format

LLM_PROMPT 类（9 项）:
  prompt.extraction-system, prompt.extraction-user,
  prompt.extraction-fewshot-default, prompt.extraction-fewshot-math,
  prompt.student-diagnosis-system, prompt.student-diagnosis-user,
  prompt.student-diagnosis-system-html, prompt.student-diagnosis-user-html,
  prompt.intent-classification-system

LLM_MODEL 类（3 项）:
  llm.model, llm.temperature, llm.max-tokens
```

---

## 9. 架构沉淀建议

### 9.1 新增可复用抽象

| 抽象 | 路径 | 复用场景 | 复用条件 |
|------|------|---------|---------|
| `ConfigValidator` | `application/config/validation/` | 未来任何需要运行时配置输入校验的场景 | 输入 key + value + type → 输出 通过/异常 |
| `ConfigLoader` 模式（ApplicationRunner + setter 注入） | `application/config/loader/` | 未来有新的 @ConfigurationProperties 类需要从 DB 加载默认值时 | 新增 Properties 类 → ConfigLoader 追加 setter 映射 |

### 9.2 项目级技术决策

- **配置优先级**：DB 自定义值 > yml 默认值，本项目所有动态配置统一遵循此优先级
- **配置热重载方式**：直接更新 @ConfigurationProperties Bean setter，本项目后续所有配置热更新统一使用此方式，不引入 Spring Cloud

### 9.3 跨模块契约

| 契约 | 类型 | 说明 |
|------|------|------|
| `GET/PUT /api/v1/config` | REST API | 配置管理端点，仅 ADMIN |
| `POST /api/v1/config/apply` | REST API | 配置应用端点，仅 ADMIN |
| `ConfigService.getPromptText(key)` | Java 接口 | 提示词双源加载入口，供 PromptTemplateService 和 ExtractionPromptBuilder 调用 |

### 9.4 依赖变动

本 change 不新增 Maven 依赖。复用既有 Caffeine、Spring Data JPA、jackson-databind。

### 9.5 禁动清单变动

本 change 无新增禁动路径。