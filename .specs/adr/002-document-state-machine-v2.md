# ADR-002: 文档状态机 v2 — 8 状态扩展

- **状态**: accepted
- **日期**: 2026-06-18
- **Change**: `extensible-file`
- **决策者**: AI Architect + 人工 review

---

## Context

当前 `FileStatus` 仅有 4 个状态：`UPLOADED → PROCESSING → COMPLETED/FAILED`（+ `DELETING` 独立于处理状态）。同步链路引入后，处理流程从单步"解析"扩展为三步"解析 → 抽取 → 融合"，现有 `PROCESSING` 状态无法区分当前处于哪个子步骤。

需求要求（AC-4/AC-5/AC-6）：
1. 用户可查询文档看到具体阶段状态（而非笼统的 `PROCESSING`）
2. 中途失败保留已成功步骤的产物，从失败点继续
3. 手动 `/process` 从当前状态断点续跑

## Decision

扩展 `FileStatus` 为 8 状态模型：

```
UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED
                                                        ↕
                                                     FAILED
```

**状态语义**：

| 状态 | 含义 | 数据保证 |
|------|------|---------|
| `UPLOADED` | 文件已存 MinIO + MySQL 记录已创建 | MinIO 文件存在，`minio_path` 非空 |
| `PARSING` | 解析进行中 | —（中间态） |
| `PARSED` | 解析完成，文本内容已入库 | `text_content` 非空，`page_count` 已填 |
| `EXTRACTING` | LLM 知识抽取进行中 | —（中间态） |
| `EXTRACTED` | 抽取完成，Neo4j 子图已写入 | Neo4j 存在对应 EntityNode + KnowledgePointNode |
| `FUSING` | 融合进行中 | —（中间态） |
| `COMPLETED` | 全链路成功 | 上述全部 + MASTERS 边已更新 |
| `FAILED` | 不可恢复错误 | 视具体失败步骤，部分数据可能已入库 |

**失败回退规则**（区别于 `FAILED`）：

- 可恢复失败（网络超时、LLM 限流、Neo4j 暂时不可用）→ 状态回退到上一个 `*ED` 状态 + `failReason` 记录失败步骤和原因
- 不可恢复失败（文件损坏、格式不支持、内容不足以抽取）→ 状态设为 `FAILED` + `failReason`
- 判断标准：同一操作重试 ≤3 次仍失败 → 标记为 `FAILED`；首次失败 → 回退到上一个 `*ED`

**回退示例**：

```
PARSING  → (MinerU 超时) → UPLOADED + failReason="parse: MinerU poll timeout after 300s"
EXTRACTING → (LLM 429)    → PARSED   + failReason="extraction: LLM rate limited, retry after 60s"
FUSING  → (Neo4j 连接断)  → EXTRACTED + failReason="fusion: Neo4j connection lost"
```

**retry() 断点续跑**：

```
UPLOADED  + failReason="parse:*"       → 从 PARSING 开始
PARSED    + failReason="extraction:*"  → 从 EXTRACTING 开始
EXTRACTED + failReason="fusion:*"      → 从 FUSING 开始
FAILED                                  → 从头开始（PARSING）
COMPLETED                               → 从头开始（重新处理）
```

## Consequences

- **正面**：状态名即进度，用户和管理员可直接从 status 字段理解当前阶段，无需解析 `metadata_json`
- **正面**：失败定位精确到步骤，`retry()` 从失败点继续而非重做全部，节省时间和 LLM Token 费用
- **正面**：已成功的步骤产物（如 MinIO 文件、`text_content`、Neo4j 子图）不丢失
- **负面**：状态数从 4 增加到 8，`validateTransition()` 转换规则从 ~6 条增加到 ~20 条，复杂度上升
- **负面**：旧代码中如有 `switch (status)` 或状态比较逻辑，需逐一检查是否兼容新状态值
- **负面**：`*ING` 状态是瞬态（通常持续数秒到数十秒），只在同步处理期间可被并发 GET 请求观测到，日常查询中很少出现，增加了状态枚举的"噪音"