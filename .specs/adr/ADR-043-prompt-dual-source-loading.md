# ADR-043: 提示词模板双源加载

- **状态**: accepted
- **日期**: 2026-06-23
- **决策者**: Architect（AI）+ 人工 review
- **关联**: `@.specs/config-management/DESIGN.md` D3

---

## Context

系统当前有 9 个 LLM 提示词模板文件存放在 `classpath:/prompts/*.md`，通过 `ResourceLoader` 在运行时加载。配置管理模块需要让 ADMIN 能通过前端修改这些提示词文本并 Apply 生效。

核心挑战：
1. classpath 文件是系统发布的一部分，不可在运行时被修改（JAR 内只读）
2. 需要保留 classpath 文件作为初始模板和回滚基线
3. 提示词文本可能很长（数千字符），需要支持多行编辑
4. 修改后需即时生效（Apply 后下一次 LLM 调用使用新提示词）

## Decision

**采用双源加载策略：优先从 DB（ConfigService）读取，未自定义时 fallback 到 classpath 文件。**

### 加载流程

```
loadTemplate(name)
  → 构建 configKey = "prompt." + name
  → ConfigService.getPromptText(configKey)
      → Caffeine 缓存命中 + config_value != null?
          YES → 返回 DB 自定义文本
          NO  → ResourceLoader.getResource("classpath:/prompts/" + name + ".md")
                 → 返回 classpath 文件内容
```

### 修改点

1. **PromptTemplateService.loadTemplate()** — 现有逻辑为直接从 classpath 读取。改为：先调用 `ConfigService.getPromptText(configKey)`，若返回非 null 则用 DB 值；否则走原 classpath 路径
2. **ExtractionPromptBuilder** — 同样使用 `ResourceLoader` 加载 few-shot 和 system/user 模板。改为：先查 ConfigService，fallback classpath
3. **ConfigService.getPromptText()** — 新增方法，从 Caffeine 缓存读取指定 key 的 `config_value`

### 缓存一致性

- 提示词文本也走 Caffeine 缓存（与其他配置一致）
- PUT 修改提示词 → 更新 DB + 更新 Caffeine（单条 put）
- Apply → 全量从 DB 重载 Caffeine（确保与其他配置一致的原子性）
- 提示词加载是每次 LLM 调用的热路径，Caffeine get O(1) 不影响性能

### 备选方案

1. **提示词统一迁移到 DB，删除 classpath 文件**
   - 优点：单一数据源，逻辑简单
   - 缺点：classpath 文件作为初始模板和离线参考必须保留；首次部署 DB 为空时无 fallback；开发者本地调试需先插入 DB 记录才能改提示词
   - 否决理由：classpath 文件的 fallback 角色不可替代

2. **ConfigService 返回 Optional，由调用方决定是否 fallback classpath**
   - 优点：ConfigService 与 classpath 加载解耦
   - 缺点：每个调用方需重复 fallback 逻辑；现有 `loadTemplate` 和 `ExtractionPromptBuilder` 已有不同的 classpath 加载实现
   - 采用：部分采纳——ConfigService.getPromptText() 返回 null 时由 PromptTemplateService 内部走原有 classpath 路径

## Consequences

### 正面

- 最小化代码改动：仅 `PromptTemplateService` 和 `ExtractionPromptBuilder` 两处需要修改
- classpath 文件 100% 保留，开发者本地调试、CI 测试不受影响
- 回滚简单：将 DB 中对应的 `config_value` 设为 NULL 即可恢复使用 classpath 文件
- 提示词与其他配置共享同一套 Apply 机制和 Caffeine 缓存

### 负面

- 提示词有两份"源"（DB 和 classpath），长期运营后可能出现 DB 中版本远偏离 classpath 版本的情况，排查问题需注意
- 新增提示词模板时（如新增意图类型），需同时创建 classpath 文件 + INSERT seed 数据，多一步操作
- `ExtractionPromptBuilder` 内部有独立的 classpath loader（不与 PromptTemplateService 共享），需分别修改

### 合规

- 遵循 ADR-011（classpath:/prompts/*.md 文件管理范式），本 ADR 是 ADR-011 的扩展而非替代
- 不改动 classpath 文件内容（禁动清单）