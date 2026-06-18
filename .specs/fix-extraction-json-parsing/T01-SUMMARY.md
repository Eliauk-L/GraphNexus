# T01-SUMMARY: Jackson 宽松解析 + JSON 提取算法改进

- **Task ID**: T01
- **Change ID**: `fix-extraction-json-parsing`
- **日期**: 2026-06-16
- **状态**: ✅ 完成

---

## 做了什么

### 新建 `ExtractionJsonParser` 组件

`com.graphnexus.application.graph.extraction.ExtractionJsonParser`

四层防御机制：
1. **预处理**：去 BOM → 提取 markdown 代码块内容 → 移除 JS 注释（`//` 和 `/* */`，DOTALL 跨行）
2. **括号匹配提取**：用状态机算法精准提取最外层 JSON 对象，正确处理嵌套 `{}`、字符串内花括号、转义引号、单引号字符串
3. **Jackson 宽松解析**：配置 `JsonReadFeature` 容忍尾逗号、Java 注释、单引号、无引号字段名、未转义控制字符；忽略未知属性
4. **激进兜底**：宽松模式失败后，移除尾逗号 + 正则清理首尾非 JSON 字符 + 二次解析

### 修改 `ExtractionService`

- 注入 `ExtractionJsonParser` 替代 `ObjectMapper`
- 删除内联的 `parseResponse()` 和 `preprocessJson()` 方法（约 38 行）
- `extract()` 方法中调用简化为 `jsonParser.parse(llmResponse)`

### 测试

`ExtractionJsonParserTest` — 23 个测试用例，覆盖：
- 正常/最小 JSON
- 尾逗号（对象和数组）
- 单引号
- 无引号字段名
- 单行/多行 JS 注释
- Markdown 代码块（```json 和 ``` 两种形式）
- 首尾多余中文文字
- 组合错误（markdown + 尾逗号 + 注释 + 单引号 + 无引号字段名）
- 嵌套 metadata 花括号
- null/空字符串/无花括号边界情况
- BOM 前缀
- preprocess 和 extractJsonObject 方法独立测试

全部 23 个测试通过，9 个现有 ExtractionValidatorTest 无回归。

## 已知限制

- 不处理 LLM 输出被 max-tokens 截断的严重残缺 JSON（后续可加第 5 层：截断检测 + 错误反馈重试）
- 不启用 LangChain4j Structured Output（后续可在 LlmGateway 增加 `structuredChat()` 方法）