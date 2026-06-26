# ADR-025: 可插拔意图识别策略链（LLM-first + 关键词 fallback）

## Context

`intelligent-qa` 的意图识别（`QueryServiceImpl.recognizeIntent()`）使用 5 个硬编码中文关键词（薄弱/加强/掌握/诊断/分析学生）匹配用户问题。存在的问题：

1. **覆盖盲区**：不含关键词的问题直接抛错 A0019（如"帮我看看李四数学怎么样"）
2. **无法区分语义**："分析学生张三数学掌握情况" → 关键词"掌握"命中 → 路由到薄弱点诊断，但用户本意可能是全局掌握概览
3. **扩展需改代码**：每增一种意图都要改关键词 Map + 重新部署。未来新增 `KP_ANALYSIS`、`CLASS_OVERVIEW` 等意图时，关键词 Map 会膨胀且冲突加剧

CHANGE.md 已预留 LLM 分类扩展点。同时，现有的 `extractViaLlm` → `extractViaRegex` fallback 模式已在同一文件中验证可行，应保持一致。

核心问题：
1. 如何设计意图识别架构使其对 v2 多意图扩展友好？
2. LLM 分类的 prompt 如何动态感知已注册的意图类型？
3. fallback 链路如何编排才能不增加 `QueryServiceImpl` 复杂度？

## Decision

### D0: 可插拔策略链 — `IntentRecognitionStrategy` 接口 + 优先级链

```java
// 伪代码（非完整实现）
public interface IntentRecognitionStrategy {
    /**
     * 尝试从用户问题中识别意图。
     * @return QueryIntent 若识别成功；null 若本策略无法判定（交由下一策略）
     */
    QueryIntent recognize(String question);

    /**
     * 优先级（越小越先执行）。内置策略保留 1-99，扩展策略从 100 起。
     */
    default int priority() { return 100; }
}
```

**内置实现**：

| 策略 | priority | 角色 | 注册方式 |
|------|----------|------|---------|
| `LlmIntentRecognitionStrategy` | 10 | 主链路：调 LLM 分类为 `QueryIntent` | `@Component` → Spring 自动发现 |
| `KeywordIntentRecognitionStrategy` | 20 | 降级链路：关键词匹配兜底 | `@Component` → Spring 自动发现 |

**编排器 `IntentRecognitionService`**：

```java
// 伪代码
@Service
public class IntentRecognitionService {
    private final List<IntentRecognitionStrategy> strategies; // Spring 注入，按 priority 排序

    public QueryIntent recognize(String question) {
        for (IntentRecognitionStrategy strategy : strategies) {
            QueryIntent result = strategy.recognize(question);
            if (result != null) {
                log.debug("意图识别成功: strategy={}, intent={}", strategy.getClass().getSimpleName(), result);
                return result;
            }
        }
        // 所有策略均返回 null → 无法识别
        throw new BusinessException(A0019, "无法识别查询意图...");
    }
}
```

**扩展方式（v2 添加新策略）**：

```java
// 例：未来添加 embedding 向量相似度策略
@Component
public class EmbeddingIntentRecognitionStrategy implements IntentRecognitionStrategy {
    @Override
    public int priority() { return 15; } // 介于 LLM 和关键词之间

    @Override
    public QueryIntent recognize(String question) {
        // 向量相似度匹配逻辑
        // 置信度 < 阈值 → return null（交给下一策略）
    }
}
// Done. IntentRecognitionService 自动发现，零改动。
```

**选择策略链而非硬编码 if-else 的理由**：
- 与 `SubgraphPruningStrategy` 接口 + `PruningStrategyRegistry` 模式一致（ADR-027），降低项目认知负担
- 新增识别策略只需实现接口 + `@Component`，不修改编排逻辑
- 优先级机制使策略顺序显式可控（LLM > embedding > keyword），便于调试和 A/B 测试
- 各策略独立可测试（mock 其他策略即可隔离测试目标策略）

### D1: `LlmIntentRecognitionStrategy` — LLM 分类

```java
@Component
public class LlmIntentRecognitionStrategy implements IntentRecognitionStrategy {
    private final LlmGateway llmGateway;
    private final PromptTemplateService promptTemplateService;

    @Override
    public int priority() { return 10; }

    @Override
    public QueryIntent recognize(String question) {
        try {
            String systemPrompt = promptTemplateService.load("intent-classification-system");
            String response = llmGateway.chat(systemPrompt, question);
            return parseClassificationResponse(response); // JSON 解析，失败返回 null
        } catch (Exception e) {
            log.warn("LLM 意图分类失败，交由下一策略: {}", e.getMessage());
            return null; // 不抛异常，交给 KeywordStrategy
        }
    }
}
```

关键行为：
- 任何异常（超时/JSON解析失败/LLM返回未知意图）→ 返回 `null`，不抛异常
- 返回 `null` = "我搞不定，下一个策略来"
- 这与 `extractViaLlm` 的异常处理模式一致

### D2: 意图分类 Prompt 设计（动态感知意图注册表）

Prompt 中**不硬编码意图列表**。改为由 `PromptTemplateService` 在加载时注入当前 `QueryIntent` 枚举的所有值：

```
你是意图识别助手。分析用户问题，判断其查询意图。
仅返回 JSON: {"intent":"<意图名>"}，不要其他内容。

当前支持的意图：
{{intentList}}
（格式：INTENT_NAME — 中文描述）

以下情况返回 {"intent":null}：
- 无法判断意图
- 问题与教育诊断无关（如闲聊、天气、新闻）
```

`{{intentList}}` 由 `QueryIntent` 枚举动态生成：

```java
// PromptTemplateService 新增方法
public String buildIntentList() {
    return Arrays.stream(QueryIntent.values())
        .map(i -> "- " + i.name() + " — " + i.getDescription())
        .collect(Collectors.joining("\n"));
}
```

这样新增 `QueryIntent` 枚举值后，LLM prompt 自动包含新意图描述，无需改模板文件。

### D3: `KeywordIntentRecognitionStrategy` — 关键词兜底

```java
@Component
public class KeywordIntentRecognitionStrategy implements IntentRecognitionStrategy {
    // 每个意图关联一组关键词（从 QueryIntent 枚举动态构建）
    private final Map<String, QueryIntent> keywordMap;

    public KeywordIntentRecognitionStrategy() {
        this.keywordMap = buildKeywordMap();
    }

    @Override
    public int priority() { return 20; }

    @Override
    public QueryIntent recognize(String question) {
        if (question == null || question.isBlank()) return null;
        for (var entry : keywordMap.entrySet()) {
            if (question.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null; // 关键词未命中，整个链失败 → A0019
    }

    private static Map<String, QueryIntent> buildKeywordMap() {
        // v1 仅 STUDENT_DIAGNOSIS；v2 每个意图加自己的关键词
        Map<String, QueryIntent> map = new LinkedHashMap<>();
        map.put("薄弱", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("加强", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("掌握", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("诊断", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("分析学生", QueryIntent.STUDENT_DIAGNOSIS);
        return map;
    }
}
```

v2 扩展时，直接在 `buildKeywordMap()` 中追加新意图的关键词即可。

### D4: JSON 解析容错

```java
// LlmIntentRecognitionStrategy 内部
private QueryIntent parseClassificationResponse(String llmResponse) {
    try {
        String json = llmResponse.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        JsonNode node = objectMapper.readTree(json);
        String intentName = node.has("intent") && !node.get("intent").isNull()
                ? node.get("intent").asText() : null;
        if (intentName == null) return null;
        try {
            return QueryIntent.valueOf(intentName);
        } catch (IllegalArgumentException e) {
            log.warn("LLM 返回未知意图: {}", intentName);
            return null;
        }
    } catch (Exception e) {
        log.warn("LLM 意图分类 JSON 解析失败: {}", e.getMessage());
        return null;
    }
}
```

## Consequences

### 正面
- **真正可插拔**：新增意图识别方式只需实现 `IntentRecognitionStrategy` + `@Component`，不修改编排代码。与 `SubgraphPruningStrategy`（ADR-027）形成一致的扩展模式
- 覆盖盲区：不含关键词的自然语言问题被 LLM 正确分类（AC-1）
- Fallback 链天然支持多级降级（LLM → Embedding → Keyword），后续加新策略无需改 `IntentRecognitionService`
- 各策略独立可测试（mock 其他策略 = 隔离测试目标策略的 `recognize()` 方法）
- Prompt 中意图列表由枚举动态生成，新增意图自动反映到 LLM 分类 prompt

### 负面
- 对仅 2 个策略的场景显得过度设计（接口 + 2 实现 + 编排器 = 4 个文件），但为 v2 多意图扩展铺路
- 策略链顺序依赖 `priority` 约定（而非显式配置），调试时需查看日志确认执行顺序
- 每次问答增加 1 次 LLM 调用（意图分类），配额消耗 +1

### 风险缓解
- `IntentRecognitionService` 构造时 `log.info` 打印策略链顺序（含优先级 + 类名），启动即可验证
- 意图分类 prompt 精简（< 500 tokens 输入 + < 50 tokens 输出），轻量调用
- `KeywordIntentRecognitionStrategy` 保留作为最终兜底，LLM 不可用时系统不挂
- 日志记录每次链路执行情况：哪个策略成功 / 所有策略失败（WARN 级别含原始问题文本）