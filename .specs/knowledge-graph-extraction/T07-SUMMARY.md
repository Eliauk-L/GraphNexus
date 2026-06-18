# T07-SUMMARY: ExtractionService — Prompt + JSON Schema + 校验

- **Task ID**: T07
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建抽取核心逻辑包 `application/graph/extraction/`，4 个文件：

| 文件 | 职责 |
|------|------|
| ExtractionPromptBuilder | System Prompt（角色+5实体类型+6关系+Few-shot+输出规则）+ User Message 拼接 |
| ExtractionRawResult | LLM 输出 JSON → POJO 映射（7 个顶层数组 + 内部类）|
| ExtractionValidator | entityType 枚举值/relationshipType 枚举值/索引越界/必填字段 校验 |
| ExtractionService | 编排：Prompt→LLM→预处理→反序列化→重试→校验→领域对象转换 |

**ExtractionService 流程**：
1. 构建 Prompt → LlmGateway.chat()
2. 首次失败 → 重试 1 次
3. 预处理：去 ```json 包裹
4. Jackson 反序列化 → 失败则再次清理重试
5. ExtractionValidator.validate()
6. convertToDomain()：索引→节点 ID→边对象

## 改动文件（4 个新增）

- `extraction/ExtractionPromptBuilder.java`
- `extraction/ExtractionRawResult.java`
- `extraction/ExtractionValidator.java`
- `extraction/ExtractionService.java`

## verify

`mvn compile -q` — 通过

## 越界检查

TASK write_files: 4 项 | diff: 4 项 | 越界: 0 ✅

## 完成判定

4 个抽取核心类编译通过；Prompt 含 6 段 + Few-shot（二次函数）；校验器覆盖 entityType/relationshipType 枚举 + 索引越界 + 必填字段。