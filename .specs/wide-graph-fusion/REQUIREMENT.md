# REQUIREMENT: 宽图谱融合 — 多源知识图谱合并与掌握度聚合

- **Change ID**: `wide-graph-fusion`
- **关联**: `@.specs/wide-graph-fusion/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为系统管理员，我想手动触发全量 KP 融合，以便在需要时将文档图谱和成绩图谱中的同名知识点合并为统一视图，并按最新成绩重算所有学生的 MASTERS 掌握度。
- **US-2**：作为系统（自动化流程），我想在新文档完成抽取或新 CSV 上传完成后自动触发增量融合，以便新增的知识点被即时合并、受影响学生的 MASTERS 边被即时更新，无需人工干预。
- **US-3**：作为系统管理员，我想根据融合日志回滚某次融合操作，以便在融合结果异常（如误合并了不相关的 KP）时恢复到融合前的 Neo4j 图状态。
- **US-4**：作为模块维护者，我想 KP 匹配策略和权重计算策略通过接口定义、通过配置切换实现类，以便后续接入向量相似度匹配、LLM 语义匹配、EWMA 等新算法时无需修改融合引擎核心代码。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 手动全量融合端到端

- **Given** Neo4j 中存在：
  - 文档抽取侧 KnowledgePoint：`KP-A`（name="二次函数顶点坐标", subject="数学", documentId="1"）
  - CSV 导入侧 KnowledgePoint：`KP-B`（name="二次函数顶点坐标", subject="数学", documentId=null）
  - `KP-A` 有 `ALIGNED_TO` 边指向 EntityNode `E1`，`BELONGS_TO` 边指向 KnowledgeCategory `CAT-函数`
  - `KP-B` 有 `TESTED` 边来自 Exam `EXAM-01`
  - `EXAM-01` 有 `ATTENDED` 边来自 Student `S1`（张三, studentNo="S2024001"）
  - MySQL `exam_record` 中 S1 对"二次函数顶点坐标"的得分记录：score=2, maxScore=10
- **When** 发送 `POST /api/v1/graph/fusion/execute`
- **Then** HTTP 200，响应体含 `fusionLogId`、`mergedKpCount`、`mastersEdgeCount`。Neo4j 中：
  - `KP-A` 和 `KP-B` 合并为一个 KnowledgePoint 节点（保留非空字段，含 `fusionSource` = 标记两个来源）
  - 该节点保留 `ALIGNED_TO→E1`、`BELONGS_TO→CAT-函数`、`TESTED` 边（来自 `EXAM-01`）
  - 旧 `KP-A` 和 `KP-B` 节点被删除
  - 存在 `(:Student {studentNo:"S2024001"})-[:MASTERS]->(:KnowledgePoint {name:"二次函数顶点坐标"})` 边，`weight` = 0.20（2/10），`description` 含单次考试摘要 JSON
  - MySQL `fusion_log` 新增一条记录，`fusion_detail_json` 包含源 KP ID 列表 → 目标 KP ID 映射 + 边重定向清单，`masters_snapshot_json` 包含 `(S2024001, 二次函数顶点坐标)` 的 oldWeight=null → newWeight=0.20
- **验证方式**: 准备上述 Neo4j 数据 → `curl -X POST http://localhost:8080/api/v1/graph/fusion/execute` → 用 Cypher 查询 `MATCH (kp:KnowledgePoint {name:"二次函数顶点坐标"})` 断言仅 1 个节点，`MATCH (:Student {studentNo:"S2024001"})-[m:MASTERS]->(kp)` 断言 weight=0.20，查询 `fusion_log` 断言记录存在且 JSON 字段完整

### AC-2 · KP 模糊匹配合并

- **Given** Neo4j 中存在：
  - 文档侧 KP `KP-C`（name="二次函数顶点坐标", subject="数学"）
  - CSV 侧 KP `KP-D`（name="顶点坐标公式", subject="数学"）— 名称不完全相同但指向同一知识点
- **When** 发送 `POST /api/v1/graph/fusion/execute`，模糊匹配阈值 = 0.85
- **Then** HTTP 200。KP-C 和 KP-D 被识别为同一融合组并合并为一个 KnowledgePoint 节点，"顶点坐标公式"与"二次函数顶点坐标"的编辑距离/Jaccard 相似度 ≥ 0.85 触发合并
- **验证方式**: 准备上述 KP → 触发融合 → Cypher 查询 `MATCH (kp:KnowledgePoint) WHERE kp.name CONTAINS '顶点' AND kp.subject='数学'` 断言仅 1 个节点，`fusion_log.fusion_detail_json` 包含 KP-C 和 KP-D 的 ID 在同一融合组

### AC-3 · 模糊匹配不误合——名称部分重叠但属不同知识点

- **Given** Neo4j 中存在：
  - KP `KP-E`（name="二次函数图像", subject="数学"）
  - KP `KP-F`（name="一次函数图像", subject="数学"）— 共享"函数图像"后缀，但属不同知识点
- **When** 发送 `POST /api/v1/graph/fusion/execute`，模糊匹配阈值 = 0.85
- **Then** HTTP 200。KP-E 和 KP-F **不被合并**（字符 Jaccard = 5/7 ≈ 0.714 < 0.85），两者保持独立节点
- **验证方式**: 触发融合 → Cypher 查询 `MATCH (kp:KnowledgePoint) WHERE kp.name CONTAINS '函数图像' AND kp.subject='数学'` 断言保留 2 个独立节点
- **已知局限**: 若两个不同 KP 名称仅差一字且其他字完全相同（如"一次函数定义" vs "二次函数定义"，Levenshtein ≈ 0.83），v1 模糊匹配处于灰色地带。此类边界 case 属于 v2 的 LLM 语义匹配解决范围。v1 通过阈值配置（`fusion.kp-matching.threshold`）允许运维调参缓解

### AC-4 · MASTERS 时间衰减加权聚合

- **Given** Neo4j 中存在：
  - Student `S2`（studentNo="S2024002"）
  - 同一 KP"对称轴"在两次考试中被考查：
    - EXAM-01（examDate=2024-09-15，距今更远）：rawScore=3, maxScore=8，得分率 0.375
    - EXAM-02（examDate=2024-11-15，距今更近）：rawScore=7, maxScore=10，得分率 0.700
  - `application-dev.yml` 中 `fusion.weight.time-decay-factor = 0.9`（月衰减）
  - 当前日期 = 2024-12-15
- **When** 触发融合（手动或增量）
- **Then** MASTERS weight 按时间衰减公式计算：
  - EXAM-01 距今 3 个月 → 衰减权重 = 0.9³ = 0.729，衰减得分 = 0.375 × 0.729 = 0.273
  - EXAM-02 距今 1 个月 → 衰减权重 = 0.9¹ = 0.900，衰减得分 = 0.700 × 0.900 = 0.630
  - MASTERS weight = (0.273 + 0.630) / (0.729 + 0.900) = 0.903 / 1.629 ≈ 0.554
  - 最终 MASTERS weight ≈ 0.55（保留 2 位小数）
- **验证方式**: 准备上述数据 → 触发融合 → Cypher 查询 MASTERS 边，断言 `abs(weight - 0.55) < 0.01`，`description` JSON 含 `examCount=2`、`lastExamDate="2024-11-15"`

### AC-5 · 缺考不计入 MASTERS

- **Given** Student `S3` 在某次考试中 KP"判别式"标记为缺考（MySQL `score_details` 中 score=NULL 或 score="-/-"）
- **When** 触发融合
- **Then** 该次考试的缺考记录不参与 MASTERS weight 计算（不计入分子也不计入分母），其他正常考试记录正常聚合
- **验证方式**: S3 有 2 次考试记录（1 次缺考 + 1 次得分率 0.8）→ 融合后 MASTERS weight = 0.8（而非被缺考拉低），`description` JSON 的 `examCount=1`（不计缺考）

### AC-6 · 上传后自动增量融合

- **Given** 系统中已有融合后的 KP"对称轴"（一个规范节点），Student `S1` 已有 MASTERS 边指向该 KP（weight=0.35）
- **When** 上传新 CSV 文件（含考试 EXAM-03，S1 在"对称轴"得分 8/10）
- **Then** 上传完成后：
  - CSV 导入产生的 KP"对称轴"（无 documentId）与已有规范 KP 自动合并（不产生新 KP 节点）
  - S1 的 MASTERS weight 自动重算：旧记录（得分率 0.375, 时间衰减后）+ 新记录（得分率 0.80, 时间衰减 1.0）→ newWeight > oldWeight
  - 其他不受影响的学生（如 S2）MASTERS 边不被重算
- **验证方式**: 上传新 CSV（`POST /api/v1/document/upload`）→ 等待同步返回 → Cypher 查询 MASTERS 边 weight 已更新，查询 `fusion_log` 有新的增量融合记录（`trigger_type='AUTO_INCREMENTAL'`），S2 的 MASTERS 边 weight 未变

### AC-7 · 融合日志记录完整快照

- **Given** 执行一次融合操作（含 3 组 KP 合并 + 5 条 MASTERS 边更新）
- **When** 融合完成后查询 `GET /api/v1/graph/fusion/status`
- **Then** HTTP 200，响应体含最近一次融合的：
  - `fusionLogId`、`triggerType`（MANUAL_FULL 或 AUTO_INCREMENTAL）、`executedAt`
  - `mergedKpGroupCount: 3`、`mastersEdgeCount: 5`
  - `fusionDetailJson`（可解析为 JSON）：每组含 `sourceKpIds[]` → `targetKpId`、`redirectedEdges[]`（边类型 + 数量）
  - `mastersSnapshotJson`（可解析为 JSON）：每条含 `studentNo`、`kpName`、`oldWeight`、`newWeight`
- **验证方式**: 执行融合 → `curl http://localhost:8080/api/v1/graph/fusion/status` → 用 `jq` 断言 `.fusionDetailJson | fromjson | length == 3`、`.mastersSnapshotJson | fromjson | length == 5`

### AC-8 · 基于日志回滚

- **Given** AC-7 的融合日志已记录（`fusionLogId=F001`），融合前 Neo4j 状态已知（从 `fusionDetailJson` 和 `mastersSnapshotJson` 可重建）
- **When** 发送 `POST /api/v1/graph/fusion/rollback/F001`
- **Then** HTTP 200。Neo4j 恢复到融合前状态：
  - 融合产生的规范 KP 节点被删除
  - 被删除的源 KP 节点被重建（含原始属性 + 原始入边/出边）
  - MASTERS 边权重恢复到 `oldWeight`（若融合前不存在则 MASTERS 边被删除）
  - `fusion_log` 中 F001 标记为 `rolled_back = true`
- **验证方式**: 触发融合 → 记录 Neo4j 状态 → 调用 rollback → Cypher 查询源 KP 节点已存在、规范 KP 节点已删除、MASTERS weight 恢复原值 → 查询 `fusion_log` 确认 `rolled_back=true`

### AC-9 · 回滚幂等——重复回滚无副作用

- **Given** AC-8 回滚已执行（F001 已回滚）
- **When** 再次发送 `POST /api/v1/graph/fusion/rollback/F001`
- **Then** HTTP 200（幂等成功）。Neo4j 状态与第一次回滚后一致，不产生额外节点/边/错误
- **验证方式**: 连续两次 rollback → 第二次返回 HTTP 200 → Neo4j 中无重复节点

### AC-10 · 回滚前校验——日志不存在时拒绝

- **Given** `fusionLogId=F999` 在 MySQL `fusion_log` 表中不存在
- **When** 发送 `POST /api/v1/graph/fusion/rollback/F999`
- **Then** HTTP 404（或 400），错误码 `A0012`（资源不存在），Neo4j 无变更
- **验证方式**: `curl -X POST .../rollback/F999` → 断言 HTTP 4xx + 错误码 A0012

### AC-11 · KP 匹配策略可配置替换

- **Given** `application-dev.yml` 中 `fusion.kp-matching.strategy = fuzzy`（v1 默认），同时存在备选实现 `exactMatch`（精确名称匹配，用作测试桩）
- **When** 修改配置为 `fusion.kp-matching.strategy = exactMatch` 并重启服务，触发融合
- **Then** 系统使用 `ExactMatchStrategy`（而非 `FuzzyMatchStrategy`）执行 KP 匹配。同名同 subject 的 KP 合并，"二次函数顶点坐标"与"顶点坐标公式"不被合并（精确匹配不命中）
- **验证方式**: 修改 yml → 重启 → 触发融合 → 验证"顶点坐标公式"和"二次函数顶点坐标"保持 2 个独立节点（与 AC-2 行为相反）→ 改回 fuzzy 后恢复模糊匹配合并行为

### AC-12 · 权重计算策略可配置替换

- **Given** `application-dev.yml` 中 `fusion.weight.strategy = time-decay`（v1 默认），同时存在备选实现 `simple-average`（简单算术平均，用作测试桩）
- **When** 修改配置为 `fusion.weight.strategy = simple-average` 并重启服务，触发融合
- **Then** 系统使用 `SimpleAverageStrategy` 计算 MASTERS weight。AC-4 中 S2 的两次得分率 0.375 和 0.700 → MASTERS weight = (0.375 + 0.700) / 2 = 0.5375（不再有时间衰减因子）
- **验证方式**: 修改 yml → 重启 → 触发融合 → Cypher 查询 MASTERS weight ≈ 0.54（与 AC-4 的 0.55 不同，差异来自无时间衰减）

### AC-13 · MASTERS 边覆盖更新

- **Given** S1 已有 MASTERS 边指向 KP"对称轴"（weight=0.35），S1 后续新增一次考试记录
- **When** 触发融合（全量或增量）
- **Then** S1 的 MASTERS weight 被全量重算覆盖（非累加），新 weight 反映所有历史考试记录（含新增）的聚合结果
- **验证方式**: 融合前记录 oldWeight → 新增考试 → 融合 → 断言 newWeight ≠ oldWeight → 再次融合（无新数据） → 断言 weight 与前次一致（幂等）

### AC-14 · 跨学科 KP 不合并

- **Given** Neo4j 中存在 KP"函数定义"（subject="数学"）和 KP"函数定义"（subject="物理"），名称完全相同但 subject 不同
- **When** 触发融合
- **Then** 两个 KP 不被合并（subject 是第一分组键，跨学科不融合），各自保留独立节点
- **验证方式**: 触发融合 → Cypher `MATCH (kp:KnowledgePoint {name:"函数定义"})` 返回 2 个节点，各自 subject 不同

---

## 范围切分

### v1（本次必做）

- KP 模糊匹配融合（`KpMatchingStrategy` 接口 + `FuzzyMatchStrategy` v1 实现）
- MASTERS 边时间衰减加权聚合（`WeightCalculationStrategy` 接口 + `TimeDecayStrategy` v1 实现）
- 手动全量融合 API（`POST /api/v1/graph/fusion/execute`）
- 上传后自动增量融合（新文档抽取或新 CSV 上传后触发）
- MySQL `fusion_log` 表 + 完整融合明细 JSON + MASTERS 变更快照 JSON
- 基于日志回滚（`POST /api/v1/graph/fusion/rollback/{fusionLogId}`）
- 融合状态查询（`GET /api/v1/graph/fusion/status`）
- 文档优先字段冲突解决
- 策略可通过 `application-dev.yml` 配置切换

### v2（下一轮考虑，不本次）

- 向量相似度匹配的 `KpMatchingStrategy` 实现（需接入 embedding 服务）
- LLM 语义匹配的 `KpMatchingStrategy` 实现
- EWMA（指数加权移动平均）`WeightCalculationStrategy` 实现
- 贝叶斯推断 `WeightCalculationStrategy` 实现
- 融合历史趋势查询（多时间点 weight 变化曲线）
- 大规模融合的异步执行（MQ 解耦，避免 HTTP 超时）

### out（永远不做）

- 跨学科 KP 融合（数学 vs 物理"函数定义"）
- EventNode / HAS_EVENT / RELATES_TO 边的引入（沿用简化三元模型）
- 融合冲突人工审核 UI
- 前端图可视化
- 融合操作需要审批（工作流）

---

## 非功能性需求

- **性能**: 全量融合在 100 Student × 50 KP 规模下 ≤ 5 秒；增量融合（单次上传后）≤ 2 秒；回滚在同等规模下 ≤ 3 秒
- **可访问性**: 无（纯后端 API）
- **安全**: 融合和回滚端点需管理员权限（Spring Security `@PreAuthorize` 校验），防止未授权修改图数据
- **兼容性**: 不破坏已有 `knowledge-graph-extraction` 和 `csv-grade-import` 的 API 行为；`GraphNodeRepository` 现有方法不变
- **可观测性**: 融合操作记录 INFO 日志（含触发方式、融合 KP 数、MASTERS 边数、耗时）；融合失败记录 ERROR 日志（含异常堆栈和当前已处理进度）；`fusion_log` 表提供完整的可审计融合历史

## 依赖与假设

- **依赖**:
  - `knowledge-graph-extraction` 的 Neo4j 图节点/边抽象体系（`GraphNode`/`GraphEdge`/`EdgeType`/`NodeType`/`GraphNodeRepository`）
  - `csv-grade-import` 的 Student/Exam/KnowledgePoint 节点 + ATTENDED/TESTED 边 + MySQL `exam_record` 表
  - `.specs/CONTEXT.md` 中定义的全局删除约束 C1-C5
- **假设**:
  - Neo4j 中 CSV 导入的 KnowledgePoint 节点通过 `documentId IS NULL` 或 `fusionSource = 'CSV_IMPORT'` 可区分来源
  - MySQL `exam_record.score_details` JSON 结构稳定（每个 ScoreDetail 含 `kpName` + `rawScore` + `maxScore` 字段）
  - 同一考试中同一 KP 仅出现一次（一道题关联多个 KP 时，每个 KP 的得分在 CSV 解析阶段已独立计算）
  - 融合在同步 HTTP 请求内完成（与既有同步处理模式一致），大规模数据场景的异步融合属于 v2

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。