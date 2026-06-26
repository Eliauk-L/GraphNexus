# ADR-022: 三阶段流水线编排

- **状态**: superseded（修订为两阶段，见下方「修订记录」）
- **日期**: 2026-06-20
- **来源**: `graph-construction-refactor` DESIGN § D5

---

## 修订记录（2026-06-21）

**原设计为三阶段（构建→实体对齐→融合），现修订为两阶段（构建→融合），移除独立的实体对齐阶段。**

### 修订原因

实现 phase2（跨文档实体对齐）后发现两个问题：

1. **匹配策略语义不成立**：phase2 用 `entity.name` 模糊匹配 `kp.name`，但 Entity 是具体片段名（如"配方法步骤"），KP 是标准概念（如"配方法"），两者语义维度不同，直接模糊匹配易误匹配或漏匹配。

2. **与融合重复**：`FusionGraphRepository.redirectEdges` 在融合合并 KP 时，会动态发现并重定向所有入边（含 `ALIGNED_TO: Entity→KP`）到规范 KP。因此跨文档的 Entity 最终都会指向规范 KP，phase2 提前创建的 ALIGNED_TO 边在融合后变成冗余（Entity 双重指向同一 KP）。

### 修订后的设计

```
ConstructionServiceImpl.extract(docId):
  phase1_build(docId)   → 图谱构建（EXTRACTING → EXTRACTED）
  phase2_fuse(docId)    → 图谱融合（FUSING → COMPLETED）
```

- **phase1**：LLM 抽取 + 单文档内 Entity→KP 的 ALIGNED_TO 边（由 LLM 语义产出）+ SubjectNode + BELONGS_TO_SUBJECT 边
- **phase2**：增量融合，合并跨源重复 KP；`redirectEdges` 自动重定向 ALIGNED_TO 边到规范 KP，隐式完成跨文档实体对齐
- **FileStatus**：回退到 v2（移除 ALIGNING/ALIGNED），流转 `EXTRACTING→EXTRACTED→FUSING→COMPLETED`

### 保留的原三阶段设计（历史记录）

以下为原 ADR 内容，保留供参考。

---

## Context

当前 `GraphServiceImpl.extract()` 将图谱构建、实体对齐（LLM 内隐式）和增量融合内嵌在一个 80 行的方法中：

- 对齐仅限单文档内（LLM 输出的 entity→KP 映射）
- 融合被作为"附加步骤"嵌入 extract 末尾，失败静默吞掉
- 无状态追踪——调用方无法知道当前处理到哪一阶段
- 不符合 SRP

## Decision

在 `ConstructionServiceImpl` 内部采用方法级编排，将 extract 拆分为三个显式阶段：

```
ConstructionServiceImpl.extract(docId):
  phase1_build(docId)     → 图谱构建（EXTRACTING → EXTRACTED）
  phase2_align(docId)     → 实体对齐（ALIGNING → ALIGNED）
  phase3_fuse(docId)      → 图谱融合（FUSING → COMPLETED）
```

### 阶段一：图谱构建 (phase1_build)

```
职责: LLM 抽取 + 创建单文档内节点/边
输入: 文档 textContent + 元数据
输出: FileNode + EntityNodes + KnowledgePointNodes + 
      KnowledgeCategoryNodes + 单文档内关系边（ALIGNED_TO/BELONGS_TO_CATEGORY/...）
      + SubjectNode(新) + BELONGS_TO_SUBJECT 边(新)

状态: EXTRACTING → EXTRACTED
失败: 回退到 PARSED 或标记 FAILED

依赖: ExtractionService + ConstructionGraphRepository
Neo4j: 事务内（deleteByDocId + saveAll）
```

### 阶段二：实体对齐 (phase2_align)

```
职责: 新 Entity → 图谱已有 KP 的跨文档对齐
输入: 当前文档的 EntityNode 列表 + 当前 Subject
输出: 额外的 ALIGNED_TO 边 (Entity → 已有 KP)

策略: 对每个新 Entity 名称，通过 QueryGraphRepository 查询同 Subject 下已有 KP，
      使用 KpMatchingStrategy (FuzzyMatch, threshold=0.85) 匹配，
      命中则创建 ALIGNED_TO 边

候选剪枝: 仅匹配同 Subject 节点下的 KP，避免跨学科误匹配

状态: ALIGNING → ALIGNED
失败: 回退到 EXTRACTED（对齐失败不阻塞后续流程，但状态标记 failReason）
     （对齐是非破坏性操作，仅追加 ALIGNED_TO 边，失败不影响已构建的子图）

依赖: QueryGraphRepository + KpMatchingStrategy
Neo4j: 事务内（CREATE ALIGNED_TO edges）
```

### 阶段三：图谱融合 (phase3_fuse)

```
职责: 跨源 KP 合并 + MASTERS 重算
输入: 本次新建 KP 的名称列表 + Subject
输出: 合并后的规范 KP 节点 + 更新的 MASTERS 边

策略: 
  1. 精确匹配前置 pass（DOCUMENT ↔ CSV_IMPORT，name + Subject 节点引用）
  2. FuzzyMatch 常规融合
  3. MASTERS 重算

原子性: 整个阶段三在一个 Neo4j 事务中执行（见 ADR-020）

状态: FUSING → COMPLETED (成功) 或 FUSING → ALIGNED (失败回退)
失败: 不静默吞掉——在响应中返回 fusionWarning，document.status 回退到 ALIGNED

依赖: FusionService.fuseIncremental() + FusionGraphRepository
Neo4j: 事务包装（ADR-020）
```

### 错误处理策略

| 阶段 | 失败行为 | 文档状态 | API 响应 |
|------|---------|---------|---------|
| phase1 失败 | 抛异常，Neo4j 事务回滚 | 回退到 PARSED 或 FAILED | HTTP 200 + failReason |
| phase2 失败 | 记录 warn 日志，不阻塞 | EXTRACTED → ALIGNING → EXTRACTED（回退） | HTTP 200 + alignmentWarning |
| phase3 失败 | 记录 error 日志 | FUSING → ALIGNED（回退） | HTTP 200 + fusionWarning |

### 为什么不用 Pipeline 接口

当前三阶段固定：构建→对齐→融合。各阶段职责明确且不可替换。引入 Pipeline 接口 + 策略模式增加抽象层级而无实际价值。如果未来出现：
- 不同的阶段顺序（先融合再对齐）
- 可选的阶段（跳过对齐）
- 新增阶段（如人工审核阶段）

则届时重构为接口模式。根据 YAGNI 原则，当前方法级拆分足够。

## Consequences

### 正面

- 每阶段独立事务，职责清晰
- 阶段二（对齐）失败不阻塞阶段三（融合）——对齐是非破坏性追加操作
- 状态机可追踪完整进度
- 每阶段可独立测试

### 负面

- 三个阶段 = 至少 3 个独立的 Neo4j 事务，extract 整体耗时可能略增（但每阶段内部批量操作，差异 ≤5%）
- ConstructionServiceImpl 仍需管理整体编排逻辑（~60 行 vs 原来的 ~80 行）
- FileStatus 枚举新增 2 个值 + 状态转换规则变更
