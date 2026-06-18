# TASK: LLM 抽取 JSON 解析鲁棒性增强

- **Change ID**: `fix-extraction-json-parsing`
- **阶段**: DEV
- **角色**: Dev

---

## T01 · Jackson 宽松解析 + JSON 提取算法改进

### 目标

新建 `ExtractionJsonParser` 组件，替换 `ExtractionService` 中内联的 `parseResponse`/`preprocessJson`，实现分层 JSON 解析防御。

### 实施步骤

#### 1. 新建 `ExtractionJsonParser` 组件

位置：`com.graphnexus.application.graph.extraction.ExtractionJsonParser`

职责：
- 预处理 LLM 原始响应（去 BOM、去 markdown 包裹、去 JS 注释、去首尾非 JSON 文本）
- 用括号匹配算法精准提取最外层 JSON 对象
- 用宽松配置的 ObjectMapper 反序列化
- 失败时尝试更激进的清理 + 二次解析

#### 2. 预处理逻辑

```
输入：LLM 原始响应字符串
  → stripBom()
  → extractMarkdownCodeBlock()  // 提取 ```json ... ``` 内容
  → stripJsComments()           // 移除 // 和 /* */ 注释
  → extractJsonObject()         // 括号匹配找最外层 {}
  → fixTrailingCommas()         // 移除 } 和 ] 前的尾逗号
输出：干净的 JSON 字符串
```

#### 3. 括号匹配算法

替代当前 `replaceAll("^[^{]*", "").replaceAll("[^}]*$", "")` 的正则方式：

```
cursor = indexOf('{')
depth = 0
for i in [cursor, len):
  ch = raw[i]
  if ch == '{' && !inString: depth++
  if ch == '}' && !inString: depth--; if depth == 0 → return raw[cursor..i+1]
```

正确处理字符串内的 `{}` 和转义引号。

#### 4. Jackson 宽松 ObjectMapper

```java
new ObjectMapper()
    .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
    .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
    .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

#### 5. 兜底策略

宽松 ObjectMapper 解析失败时：
- 尝试激进正则清理 (回退到当前 `^[^{]*` / `[^}]*$` 逻辑)
- 仍失败 → 抛出 BusinessException(A0010)，log 中包含原始响应前 500 字符

#### 6. 修改 ExtractionService

- 注入 `ExtractionJsonParser` 替代 `ObjectMapper`
- 删除内联的 `parseResponse()` 和 `preprocessJson()` 方法
- `extract()` 方法中 `parseResponse(llmResponse)` 改为 `jsonParser.parse(llmResponse)`

### verify

1. 单元测试通过：`mvn test -pl . -Dtest="ExtractionJsonParserTest"`（新建）
2. 所有现有测试无回归：`mvn test`
3. 以下畸形 JSON 输入均能正确解析：
   - 尾逗号：`{"entities": [{"name": "x",}],}`
   - 单引号：`{'entities': [{'name': 'x'}]}`
   - 注释：`{"entities": [/* comment */{"name": "x"}]}`
   - 无引号字段名：`{entities: [{"name": "x"}]}`
   - Markdown 包裹：` ```json\n{"entities":[]}\n``` `
   - 首尾多余文字：`结果：{"entities":[]}完成了`
   - 组合错误：`Here is result:\n\`\`\`json\n{entities:[{name:'test',/*id*/value:1,},],}\n\`\`\`\nDone.`

### done

- [ ] ExtractionJsonParser 组件实现
- [ ] ExtractionService 重构完成
- [ ] 单元测试全部通过
- [ ] 现有测试无回归