# ADR-042: 运行时配置热重载机制

- **状态**: accepted
- **日期**: 2026-06-23
- **决策者**: Architect（AI）+ 人工 review
- **关联**: `@.specs/config-management/DESIGN.md` D2, D4

---

## Context

配置管理模块需要支持管理员修改配置后，无需重启服务即可让新配置值生效（Apply 操作）。具体需求见 AC-6（应用配置）、AC-12（提示词应用后生效）。

Spring Boot 原生的 `@ConfigurationProperties` 在启动时一次性绑定，不支持运行时重绑定。需要一种机制在 Apply 时刷新运行中的配置值。

受影响的组件：
- 7 个 `@ConfigurationProperties` 类（FusionProperties、QueryProperties、MetricsProperties、MinerUProperties、FuzzyMatchProperties、TimeDecayProperties）
- 1 个 `@Value` 注入的 LLM 网关（Langchain4jLlmGateway）
- 9 个提示词模板加载路径

## Decision

**通过直接调用 @ConfigurationProperties Bean 的 setter 方法注入新值，配合 Caffeine 缓存全量刷新，实现运行时热重载。**

### 机制细节

1. **ConfigService 维护对所有 @ConfigurationProperties Bean 的引用**
   - 通过构造器注入获取 `FusionProperties`、`QueryProperties`、`MetricsProperties`、`MinerUProperties`、`FuzzyMatchProperties`、`TimeDecayProperties`
   - 维护 `Map<String, Consumer<String>>` 的 config_key → setter 映射表

2. **Apply 执行流程**:
   ```
   ConfigService.apply()
     → SystemConfigRepository.findAll()  // 从 DB 全量重载
     → 遍历每条记录:
         if config_value != null:
           查找 setter 映射 → 调用 setter.accept(config_value)
           更新 Caffeine cache
         else:
           使用 default_value（即 yml 默认值，不需要额外动作）
     → 检查 LLM 模型参数是否变更
         if 变更: langchain4jLlmGateway.reinitialize(model, temp, maxTokens)
     → 日志: "Config applied: N updated, M unchanged"
   ```

3. **LLM ChatModel 热更新**:
   - `Langchain4jLlmGateway` 新增 `reinitialize(String model, Double temperature, Integer maxTokens)` 方法
   - 创建新的 `OpenAiChatModel` 实例，原子替换 `private volatile OpenAiChatModel chatModel` 字段
   - `volatile` 确保并发调用的可见性
   - 重建失败时保留旧实例 + ERROR 日志，系统继续运行

### 备选方案

1. **Spring Cloud `@RefreshScope` + `ContextRefresher.refresh()`**
   - 优点：Spring 生态标准方案，自动处理 Bean 重建
   - 缺点：需引入 `spring-cloud-context` 依赖；Bean 销毁重建会短暂中断服务；需标记所有 Properties Bean 为 `@RefreshScope`
   - 否决理由：对 40 条配置引入 Spring Cloud 全家桶过度

2. **销毁并重建 Bean（ApplicationContext.getBeanFactory().destroyBean() + 重新 createBean()）**
   - 优点：原生 Spring，无需新依赖
   - 缺点：已注入到 15+ Service 的 Properties Bean 引用会变成悬空引用（旧实例被销毁但 Service 仍持有引用）
   - 否决理由：破坏性太大

3. **每个消费者改查 ConfigService（不注入 Properties Bean）**
   - 优点：最灵活的运行时配置切换
   - 缺点：需修改 15+ 个消费方，侵入性强，改动范围大
   - 否决理由：改动面太大，v1 不做大范围重构

## Consequences

### 正面

- 零新依赖，完全在 Spring Boot 原生能力范围内
- 不破坏既有 Bean 引用关系，所有已注入 Properties Bean 的 Service 自动看到新值
- setter 调用是同步的、O(1) 操作，Apply 总耗时 < 100ms
- 简单直观，易于理解和调试

### 负面

- ConfigService 需要知道每个 Properties Bean 的内部结构（注入 + 知道 setter 方法签名）。新增 Properties 类或字段时需同步更新 ConfigLoader/ConfigService 中的映射表
- 并发场景下（Apply 执行中途有业务线程读取 Properties），不同字段可能被读到"跨版本"组合（如 threshold 是新值但 strategy 还是旧值）。由于各配置字段相互独立（threshold 和 strategy 的控制面不同），实际影响可忽略
- `ChatModel` 重建期间（毫秒级），并发的 LLM 调用可能使用旧模型。`volatile` 保证重建完成后新调用立即看到新实例
- 不适用于需要原子性更新多个关联配置的场景（v2 可引入"配置组"概念）

### 合规

- 遵循 `CONTEXT.md` 的事务边界规范（Apply 无 @Transactional，直接操作 Bean + 缓存）
- 不引入异步/消息队列