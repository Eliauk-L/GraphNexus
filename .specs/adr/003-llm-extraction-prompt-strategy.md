# ADR-003: LLM 抽取 Prompt 策略 — 单次调用 + Few-shot + JSON Schema

- **状态**: accepted
- **日期**: 2026-06-13
- **关联**: `knowledge-graph-extraction` DESIGN § D3、§ D4

---

## Context

`knowledge-graph-extraction` 需要从中文教辅文档文本中，通过 LLM 一次性抽取 EntityNode、KnowledgePointNode、KnowledgeCategoryNode 及全部关系边。核心问题：**如何设计 Prompt 使得 LLM 输出稳定、结构合规、且对中文教辅领域有足够的抽取精度？**

Prompt 设计直接影响 AC-3（JSON Schema 校验通过率）和 AC-1（端到端抽取的实体/关系覆盖率）。

## Decision

**选择方案**：单一 System Prompt + Few-shot 示例（二次函数）+ JSON Schema 约束输出 + Jackson/Bean Validation 后校验。

### Prompt 结构

```
┌─────────────────────────────────────────────┐
│ System Prompt（约 1500 tokens）               │
│                                              │
│ §1 角色设定：你是教育领域的知识图谱构建专家        │
│ §2 实体类型定义（5 种 + 各含字段说明）           │
│ §3 关系类型定义（6 种边 + 方向 + 含义）          │
│ §4 Few-shot 示例（二次函数的完整抽取结果 JSON）   │
│ §5 JSON Schema（输出格式约束）                  │
│ §6 特殊规则：                                   │
│    - 数学公式用 LaTeX 表示（如 $y=ax^2+bx+c$）  │
│    - 忽略页眉页脚/页码等非正文内容               │
│    - entityType 严格使用枚举值，禁止自创         │
│    - 每类至少抽取 1 个，实在没有返回空数组        │
│    - 仅输出纯 JSON，禁止 markdown 包裹           │
├─────────────────────────────────────────────┤
│ User Message                                 │
│                                              │
│ 文档名称：《{docName}》                        │
│ 页数：{pageCount}                             │
│ 学科：{subject}                               │
│                                              │
│ 文本内容：                                     │
│ {textContent}                                 │
└─────────────────────────────────────────────┘
```

### JSON Schema 约束的结构

```json
{
  "entities": [{
    "entityType": "DEFINITION|FORMULA|CONCEPT|EXAMPLE|SOLUTION",
    "name": "简洁名称（≤50字）",
    "originalText": "原文片段（≤500字）",
    "pageNumber": 2,
    "metadata": {"key": "value"}  // 可选额外信息
  }],
  "knowledgePoints": [{
    "name": "标准知识点名称",
    "description": "一句话说明",
    "subject": "数学",       // 继承自文档
    "gradeLevel": "初中"     // 如果可推断
  }],
  "categories": [{
    "name": "分类名称（如 二次函数）",
    "parentName": "父分类名称（如 函数，根节点为 null）",
    "level": 3  // 分类层级深度
  }],
  "alignments": [{
    "entityIndex": 0,
    "knowledgePointIndex": 0
  }],
  "entityRelations": [{
    "sourceEntityIndex": 0,
    "targetEntityIndex": 1,
    "type": "DERIVES|CONTAINS|REFERENCES",
    "description": "关系说明"
  }],
  "prerequisites": [{
    "sourceKnowledgePointIndex": 0,
    "targetKnowledgePointIndex": 1,
    "strength": 0.95,
    "description": "为什么 B 依赖 A"
  }],
  "categoryRelations": [{
    "childCategoryIndex": 0,
    "parentCategoryIndex": 1
  }]
}
```

### 校验流程

```
LLM 原始响应
  │
  ├─ 预处理：去除 ```json ... ``` 包裹（如存在）
  │
  ├─ Jackson 反序列化 → ExtractionRawResult 对象
  │   └─ 失败 → 重试一次（带"请严格输出纯 JSON"指令）
  │       └─ 再次失败 → throw BusinessException(A0010, "LLM 输出非合法 JSON")
  │
  ├─ Bean Validation (@NotNull/@Size/@Pattern)
  │   ├─ entityType 必须在 [DEFINITION,FORMULA,CONCEPT,EXAMPLE,SOLUTION] 内
  │   ├─ relationshipType 必须在 [DERIVES,CONTAINS,REFERENCES] 内
  │   ├─ entityIndex/knowledgePointIndex 不越界
  │   └─ 失败 → throw BusinessException(A0010, 具体字段名 + 违规值)
  │
  └─ 通过 → 转换为领域对象（EntityNode, KnowledgePointNode...）进入 Neo4j 写入
```

## Consequences

**正向**：
- 单次 LLM 调用完成全部抽取，延迟最优（约 5-15s 取决于文档长度）
- Few-shot 示例锚定了输出风格和格式，降低 LLM 自由发挥导致的不稳定
- JSON Schema 约束 + 双重校验（结构校验 + 语义枚举校验）拦截大部分格式错误
- 重试一次的策略在不显著增加延迟的前提下提高了鲁棒性

**负向**：
- System Prompt 长约 1500 tokens，加上文档文本和输出 JSON，单次调用 token 消耗大。15 页 PDF 约 15K-20K tokens 总消耗
- Few-shot 示例（二次函数）可能过度锚定 LLM 的输出模式——如果文档是物理/化学而非数学，LLM 可能仍倾向找"公式"和"例题"。缓解：Prompt §6 注明"示例仅为格式参考，实体类型按实际文本内容判断"
- 单次调用 = 单点故障：LLM 一次失败则整个抽取失败。方案 A（Pipeline 分步调用）可局部重试，但延迟 ×4

**被否决的方案**：

| 方案 | 否决理由 |
|:--|:--|
| 多步 Pipeline（先抽 Entity → 再对齐 KP → ...） | 延迟和成本 ×4，v1 优先验证端到端可行性。后续如果单次调用精度不达标，可重构为 Pipeline 架构，但 v1 不做 |
| 无 Few-shot，纯 Zero-shot | 中文教辅领域特异性高（"配方法"既是解法名又是动词），通用 LLM NER 容易误判。Few-shot 成本低收益高 |
| 使用 JSON Mode / Function Calling | Claude API 的 JSON Mode 可确保输出合法 JSON，但 Spring AI 1.0.0-M4 的 tool calling API 不够稳定。v1 用纯文本 Prompt + 后处理校验，后续升级到 Spring AI GA 后切换到 JSON Mode |

---

> 推翻本 ADR 的触发条件：① 实测发现 Few-shot 示例导致 LLM 过度拟合数学领域，物理/化学文档抽取效果差 → 改为领域自适应的 Few-shot 选取；② 单次调用失败率 > 20% → 引入 Pipeline 分步抽取。