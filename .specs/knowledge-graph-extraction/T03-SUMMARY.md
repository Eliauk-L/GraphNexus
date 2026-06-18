# T03-SUMMARY: LlmGateway 接口 + SpringAiLlmGateway 实现 + yml 配置

- **Task ID**: T03
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

1. **LlmGateway.java** (L2 接口) — `application/llmgateway/service/`
   - 单方法 `String chat(String systemPrompt, String userMessage)`
   - 隔离 Spring AI API，为路由/配额/降级留扩展点

2. **SpringAiLlmGateway.java** (L3 实现) — `infrastructure/llm/client/`
   - 注入 `ChatModel`，通过 `ChatClient` 调用 LLM API
   - LLM 返回空响应 → `BusinessException(C0001)`
   - 网络异常/API 错误 → `BusinessException(C0001)`
   - 日志携带 Prompt 长度/响应长度

3. **application.yml** — 激活 Neo4j 自动配置
   - 移除 3 项 Neo4j autoconfigure exclude
   - 移除 1 项 Neo4j health exclude
   - `management.health.neo4j.enabled` → true

4. **application-dev.yml** — 新增 DeepSeek API 配置
   - `spring.ai.openai.base-url: https://api.deepseek.com`
   - `spring.ai.openai.api-key: ${DEEPSEEK_API_KEY:}`
   - `spring.ai.openai.chat.options.model: deepseek-chat`
   - `spring.ai.openai.chat.options.temperature: 0.3`
   - `spring.ai.openai.chat.options.max-tokens: 4096`
   - 配置合并进已有 `spring:` 块（避免 YAML 重复 key）

## 改动文件

- `src/main/java/com/graphnexus/application/llmgateway/service/LlmGateway.java`（新增）
- `src/main/java/com/graphnexus/infrastructure/llm/client/SpringAiLlmGateway.java`（新增）
- `src/main/resources/application.yml`（修改：移除 Neo4j exclude）
- `src/main/resources/application-dev.yml`（修改：新增 spring.ai.openai 配置段）

## verify 输出

```
$ mvn compile -q
（无错误输出，编译通过）
```

## 6 维自查

- **R1 认知过载**：LlmGateway 接口 1 个方法，SpringAiLlmGateway 1 个方法 ~30 行，简单直接
- **R2 变更传播**：无越界，仅 llmgateway/ llm/ 新文件 + 2 个 yml
- **R3 知识重复**：无
- **R4 偶然复杂**：v1 不做重试/降级/路由，ChatClient 直接调用
- **R5 依赖混乱**：接口 L2 → 实现 L3，符合四层架构；L3 注入 Spring AI ChatModel 合法
- **R6 领域扭曲**：命名 LlmGateway/SpringAiLlmGateway 领域清晰

✅ 沿用既有抽象 grep（R6.4）：
- Spring AI：项目首次使用 Spring AI ChatClient → 新建（pom.xml 已有依赖，yml 已有预留配置）
- 异常处理：沿用 `BusinessException(ErrorCode)` 模式
- 配置读取：沿用 `spring.*` + `@Value` / `@ConfigurationProperties` 模式

## 越界检查（R6.5）

- TASK write_files：4 项
- 实际 diff 涉及：4 项
- 越界：0 ✅

## 完成判定

LlmGateway 接口 + SpringAiLlmGateway 实现编译通过；yml 中 Neo4j exclude 已移除、DeepSeek API 配置就位。