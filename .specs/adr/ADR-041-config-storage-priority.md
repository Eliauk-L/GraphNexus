# ADR-041: 配置存储与优先级模型

- **状态**: accepted
- **日期**: 2026-06-23
- **决策者**: Architect（AI）+ 人工 review
- **关联**: `@.specs/config-management/DESIGN.md` D1, D6

---

## Context

系统需要运行时配置管理能力。当前所有业务参数、LLM 提示词、LLM 模型参数都硬编码在 `application-*.yml` 或 `classpath:/prompts/*.md` 文件中。需要引入一种持久化存储机制，让管理员通过前端修改配置后，修改能持久保留并覆盖 yml 默认值。

核心诉求：
1. 配置修改后持久化（服务重启不丢失）
2. DB 自定义值优先级高于 yml 默认值
3. yml 文件保留作为 fallback（DB 为空或首次部署时）
4. 不需要额外的外部配置中心（如 Nacos/Apollo）

## Decision

**使用 MySQL `system_config` 表作为配置持久化存储，键值对模型，通过 Caffeine 全量内存缓存加速读取。**

### 存储模型

- 表结构：`config_key`（唯一键）+ `config_value`（TEXT，NULL 表示未自定义）+ `config_type` + `category` + `default_value`（yml 默认值）+ `validation_rule`（JSON）
- 优先级链：**DB `config_value`（非 NULL）> yml 默认值**
- `config_value IS NULL` = 未自定义，业务使用 yml 默认值
- `config_value IS NOT NULL` = 已自定义，业务使用 DB 值

### 缓存策略

- Caffeine 全量缓存（`Cache<String, SystemConfigDO>`），key = `configKey`
- 无 TTL（配置修改频率极低），Apply 时主动 `putAll` 刷新
- 启动时全量加载，后续 GET API 直接读缓存，不查 DB

### 启动加载

- `ConfigLoader`（`ApplicationRunner`，`@Order(0)`）在 Spring 启动后、正常接受请求前执行
- 通过 Spring Data JPA 从 DB 加载所有 `config_value IS NOT NULL` 的记录
- 逐条映射到对应 `@ConfigurationProperties` bean 的 setter
- 同步加载到 Caffeine 缓存

### 备选方案

1. **Redis Hash 存储** — 读取更快，但 Redis 非持久存储（重启丢失），需要额外保证 Redis 与 MySQL 数据一致。否决理由：配置数据需要 ACID 持久化保证，MySQL 是系统记录源
2. **Spring Cloud Config + Git** — 成熟方案，但引入新基础设施（Config Server）+ Git 仓库管理，对当前 40 条配置项规模严重过度。否决理由：过度工程
3. **@ConfigurationProperties 不变 + 每个消费者改读 ConfigService** — 运行时最灵活，但需修改 15+ 个消费方代码。否决理由：改动面太大

## Consequences

### 正面

- 配置持久化在 MySQL，与项目其他业务数据在同一库，运维简单
- yml 文件保持不动，现有启动流程零改动
- Caffeine 缓存确保配置读取性能（GET API < 50ms）
- `config_value IS NULL` 的语义清晰区分"未自定义"和"自定义为空字符串"

### 负面

- `ConfigLoader` 中需维护 config_key → Properties bean setter 的手动映射表（约 30 条），新增 yml 配置项时需同步追加映射。v2 可引入 `@ConfigBinding` 注解自动化
- 启动时短暂窗口（ApplicationRunner 执行前）使用 yml 默认值。若 DB 不可达，该窗口变为永久（回退到全 yml 默认值运行）
- 单表键值对模型无法表达配置间的依赖关系（如 "A=5 时 B 必须 > A"）。v1 不做跨配置校验

### 合规

- 不引入新基础设施（复用 MySQL）
- 不新增 Maven 依赖
- 不修改现有 yml 文件结构和 `@ConfigurationProperties` 类字段定义