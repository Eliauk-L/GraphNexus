# ADR-011: Prompt 模板文件化管理 + Markdown 输出约束 + 格式校验重试

## Context

`intelligent-qa` 需要将剪枝后的子图数据 + 用户问题组装为 LLM Prompt，并确保 LLM 输出符合 Markdown 格式要求（中文、无前导语、含标题 + 列表）。

核心问题：
1. System prompt（角色设定 + 输出格式约束 + 示例）长度 1500~3000 字符，如何管理？
2. 如何确保 LLM 输出格式稳定？
3. 格式不合规时如何处理？

## Decision

### D1: classpath 文件模板 + `{{variable}}` 占位符

- 模板文件放在 `src/main/resources/prompts/`，命名规则：`{intent小写}-{system/user}.md`
- 占位符格式：`{{variableName}}`（此模式在 LLM 训练数据中极少出现，不会与 prompt 内容冲突）
- `PromptTemplateService` 通过 Spring `ResourceLoader` 加载 → `String.replace("{{var}}", value)` 替换
- 双文件模式：system prompt（角色 + 格式约束 + 示例）和 user prompt（子图数据 + 用户问题）分开管理

不选模板引擎（Thymeleaf/FreeMarker）的理由：
- Prompt 模板不需要条件/循环/布局等高级功能，纯字符串替换足够
- 零额外依赖
- `.md` 文件可在 IDE 中直接编辑预览

### D2: Markdown 输出格式约束

System prompt 中明确要求：
```
你必须以 Markdown 格式输出分析报告，遵守以下规则：
1. 以 # 或 ## 标题开头，直接开始报告正文
2. 至少包含一个列表（- 或 1. ）
3. 禁止出现前导语（如"根据提供的数据……"、"以下是分析报告……"等）
4. 禁止编造子图数据中不存在的知识点名称或关系

正确示例：
## 学生张三 — 数学薄弱点诊断
...
### 薄弱知识点
- **顶点坐标** (掌握度: 20%) ...
```

### D3: 格式校验 + 重试 + 降级

LLM 返回后校验 3 项：非空 → 以 `#` 开头 → 含列表标记。不通过则重试（≤2 次），每次重试在 system prompt 末尾追加格式约束。仍失败则降级返回原始文本。

## Consequences

### 正面
- 模板文件独立于 Java 代码，修改 Prompt 无需重新编译
- 格式约束 + 示例（few-shot）显著提高 LLM 输出稳定性
- 降级策略保证用户总能拿到结果（不会因格式问题报错阻塞）

### 负面
- `{{variable}}` 占位符无类型安全——拼写错误在运行时才暴露。通过单元测试覆盖
- 每次重试 = 额外 LLM 调用（最多 +2 次），增加延迟和成本
- 格式校验规则硬编码在 Java 中（`startsWith("#")`、`contains("- ")`），对新格式（如表格为主的分析报告）需要更新校验规则

### 风险缓解
- `qa.max-retries` 可配置为 0 完全禁用重试
- 模板文件中的 `{{variable}}` 清单在 `PromptTemplateService` 中集中管理，新增模板时通过测试验证变量完整性
- 格式校验不通过时记录 WARN 日志（含原始 LLM 输出前 200 字符），便于事后分析改进 Prompt