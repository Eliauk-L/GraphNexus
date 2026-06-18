# CHANGE: 宽图谱融合 — 多源知识图谱合并与掌握度聚合

- **Change ID**: `wide-graph-fusion`
- **创建日期**: 2026-06-15
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: confirmed
- **用户决策**:
  - Q1 融合触发: C — 手动全量融合 + 上传时自动增量融合
  - Q2 MASTERS 公式: B — 时间衰减加权平均（默认策略，可替换）
  - Q3 KP 字段冲突: A — 文档优先（文档抽取 KP 的非空字段覆盖 CSV 侧空字段）
  - Q4 KP 匹配: 模糊匹配 + 策略模式扩展点（便于后续接入向量相似度/LLM 语义匹配）
  - Q5 权重算法: 策略模式扩展点（便于后续切换其他聚合算法）
  - Q6 融合日志: 记录完整融合明细，支持基于日志回滚

---

## Why（为什么做）

`knowledge-graph-extraction` 已落地文档图谱（PDF → Entity → KnowledgePoint → KnowledgeCategory + PREREQUISITE_OF），`csv-grade-import` 已落地成绩事件图谱（Student → Exam → KnowledgePoint + ATTENDED + TESTED）。两条数据链路各自独立运行，产出的 KnowledgePoint 节点**互不融合**——文档抽取的 KP 带 `documentId`，CSV 导入的 KP 不带 `documentId`，同名同 subject 的 KP 在 Neo4j 中是两条独立记录。

这导致三个关键能力缺失：

1. **KP 孤岛**：同一知识点"二次函数顶点坐标"在文档侧和考试侧是两个节点，无法从考试薄弱点追溯到原始教辅中的定义/公式/例题
2. **无掌握度视图**：学生成绩散落在多条 TESTED 边中，没有 `(:Student)-[:MASTERS]->(:KnowledgePoint)` 聚合边，无法快速回答"学生A哪些知识点薄弱"
3. **无归因分析**：无法利用 PREREQUISITE_OF 前置依赖链 + 成绩证据做根因推断（如"顶点坐标得分低是因为对称轴未掌握"）

本 change 是宽图谱的**聚合层**：将文档图谱和成绩事件图谱在 KnowledgePoint 层面融合为统一视图，产出 MASTERS 聚合边，为后续任务驱动剪枝和智能诊断提供可直接消费的图数据。

参考文档 `docs/examples/知识图谱构建示例-初中数学教辅.md` 第五步。

## What（做什么）

基于既有四层架构和图节点/边抽象体系，实现 **KP 融合引擎 + MASTERS 边聚合**：

1. **KnowledgePoint 融合引擎**：
   - **KP 匹配策略（可扩展）**：定义 `KpMatchingStrategy` 接口（契约：输入两个 KP → 输出相似度 0~1），v1 首发实现为**模糊匹配**（基于名称归一化 + 编辑距离/分词 Jaccard + subject 同科目约束），阈值可配置（默认 0.85）。接口设计预留扩展点，后续可插拔切换为向量相似度匹配或 LLM 语义匹配
   - 匹配后以 `name + subject` 为合并键分组，融合为规范 KP 节点
   - 融合后保留一个规范 KP 节点，包含所有来源的边（ALIGNED_TO + TESTED + BELONGS_TO + PREREQUISITE_OF）
   - 原 KP 节点的入边/出边重定向到融合后的规范节点，旧 KP 节点删除
   - 支持手动触发融合 + 增量融合（新文档/新考试上传后自动融合相关 KP）

2. **MASTERS 边聚合**：
   - 新增 `MASTERS` 边类型：`(:Student)-[:MASTERS]->(:KnowledgePoint)`
   - **权重计算策略（可扩展）**：定义 `WeightCalculationStrategy` 接口（契约：输入 `List<TestedRecord>` → 输出 `double weight` + 摘要），v1 首发实现为**时间衰减加权平均**（衰减因子 0.9/月，缺考不计入，同一考试多次考同一 KP 取平均）。接口设计预留扩展点，后续可接入 EWMA、贝叶斯推断等算法
   - `description` 存储 JSON 摘要（考试次数、最近考试日期、各次得分率列表）
   - 融合触发时全量重算，保证与最新成绩数据一致

3. **融合日志与回滚**：
   - 新增 `fusion_log` 表（MySQL），记录每次融合操作的完整明细：
     - 操作时间、触发方式（手动/增量自动）、融合 KP 组数、MASTERS 边更新数
     - **融合明细 JSON**：每组合并的源 KP ID 列表 → 目标 KP ID、重定向的边类型与数量
     - **MASTERS 变更快照 JSON**：每个受影响的 `(studentNo, kpName)` 的 oldWeight → newWeight
   - **基于日志回滚**：根据 `fusion_log` 记录逆向恢复：① 删除融合产生的规范 KP → ② 重建被删除的源 KP 节点 → ③ 回重定向边 → ④ 恢复 MASTERS 旧权重。回滚粒度 = 单次融合操作
   - 支持查询融合历史和当前融合状态

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增功能需求（KP 模糊匹配/MASTERS 聚合/融合回滚的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① `KpMatchingStrategy` 接口 + v1 模糊匹配算法（归一化 + 编辑距离/分词 Jaccard）；② `WeightCalculationStrategy` 接口 + v1 时间衰减公式；③ 融合事务边界与回滚机制（日志驱动逆向恢复）；④ 融合触发策略（手动 + 增量自动）；⑤ `fusion_log` 表结构与回滚快照格式
- [ ] 影响现有 AC — 无已有 AC 冲突，本 change 是增量功能
- [x] 影响数据模型 / 迁移 — Neo4j 新增 `MASTERS` 边类型（`EdgeType` 枚举 + `MastersEdge` 类）；MySQL 新增 `fusion_log` 表（含融合明细 JSON + MASTERS 变更快照 JSON）；KnowledgePoint 节点 schema 新增 `fusionSource` 字段标记融合来源
- [x] 影响外部 API 兼容性 — 新增融合触发端点（`POST /api/v1/graph/fusion/execute`）+ 状态查询端点（`GET /api/v1/graph/fusion/status`）+ 回滚端点（`POST /api/v1/graph/fusion/rollback/{fusionLogId}`），仅增量不破坏；融合操作会修改已有 KP 节点（边重定向），但不影响既有查询语义
- [ ] 仅修复 bug，无范围变化
- [ ] 依赖新模块 — 复用既有基础设施（Neo4j `GraphNodeRepository`、MySQL JPA、`EdgeType` 枚举扩展），不引入新外部依赖

## 核心设计约束（进入 DESIGN 前必须遵守）

- **KP 匹配策略模式**：定义 `KpMatchingStrategy` 接口，契约 `double match(KpCandidate a, KpCandidate b)` 返回 0~1 相似度。v1 首发实现为**模糊匹配**（名称归一化 + 编辑距离/分词 Jaccard + subject 同科目约束），阈值可配置。接口设计必须能承载后续的向量相似度匹配、LLM 语义匹配等实现，新增匹配策略不改融合引擎核心逻辑
- **权重计算策略模式**：定义 `WeightCalculationStrategy` 接口，契约 `WeightResult calculate(List<TestedRecord> records)`。v1 首发实现为**时间衰减加权平均**（衰减因子 0.9/月，可配置）。接口设计必须能承载后续的 EWMA、贝叶斯推断、机器学习排序等算法
- **融合日志驱动回滚**：`fusion_log` 记录每次融合的完整快照（融合前 KP 列表 + 边拓扑 + 融合后 KP ID 映射 + MASTERS 变更前后值），回滚时按日志逆向恢复。回滚粒度 = 单次融合操作，不支持跨多次融合的部分回滚
- **MASTERS 边是衍生边**：MASTERS 由 TESTED 聚合计算得出，不独立写入新事实。每次融合触发时全量重算覆盖
- **共享节点保留**：融合只合并 KnowledgePoint 节点，Student / Exam / Entity / Document 节点不参与合并
- **融合键分组**：匹配策略输出相似度后，以 `subject` 为第一分组键（跨学科不合并），相似度 ≥ 阈值的 KP 归入同一融合组
- **增量融合策略**：新文档抽取或新 CSV 上传后，仅融合涉及到的 KP（而非全量重融），MASTERS 边仅重算受影响的学生
- **遵循既有四层架构 + 构造器注入 + GraphNode/GraphEdge 抽象体系**。MASTERS 边注册进 `EdgeType` 枚举，新增 `MastersEdge` 类。策略接口放 `application/graph/fusion/strategy/`，实现类放同包
- **遵循全局删除约束 C1-C5**：删除操作涉及多存储系统时遵守中间状态 + 幂等 + 共享节点保留

## 范围排除（这次不做）

- ❌ **跨源图查询 API**：`GET /api/v1/graph/fusion/kp/*`、`GET /api/v1/graph/fusion/student/*`、`GET /api/v1/graph/fusion/diagnose/*` 等查询端点属于后续 change（`graph-query` 或独立 change），本次只做融合引擎 + 权重计算
- ❌ **归因诊断 API**：沿 PREREQUISITE_OF 链追溯根因的诊断查询不在本次范围
- ❌ **薄弱点排行/掌握度画像 API**：学生维度的聚合查询留给后续 change
- ❌ **向量相似度匹配实现**：v1 不接入 embedding 或向量数据库做 KP 语义匹配，但 `KpMatchingStrategy` 接口必须在本次定义好，支持后续插拔切换
- ❌ **LLM 语义匹配实现**：v1 不调用 LLM 判断 KP 是否等价，但策略接口预留此扩展点
- ❌ **EventNode / HAS_EVENT / RELATES_TO**：仍使用 csv-grade-import 确定的简化三元模型（Student → Exam → KnowledgePoint），不引入事件节点
- ❌ **MASTERS 跨学科聚合**：掌握度按 subject 独立计算，不在数学和物理之间做跨学科迁移
- ❌ **实时融合触发**：融合为同步手动触发 + 新数据上传后的自动增量触发，不做消息队列异步融合
- ❌ **融合冲突人工审核 UI**：当两个同名 KP 的 `description` 或 `gradeLevel` 冲突时，v1 采用规则解决（优先保留文档抽取 KP 的非空字段），不做人工审核界面
- ❌ **QuestionNode**：细粒度试题节点不在本次范围
- ❌ **前端可视化**：纯后端 API，不涉及图可视化前端
- ❌ **Student → KnowledgePoint 直连历史趋势**：MASTERS 边存聚合值 + 摘要 JSON，不做跨时间的趋势线查询 API（属于分析层 change）

## 验收线（粗粒度，不是 AC）

1. **KP 模糊匹配融合**：系统中存在文档抽取 KP"二次函数顶点坐标"和 CSV 导入 KP"顶点坐标公式"（名称相似但不完全相同），subject 均为"数学"，模糊匹配识别为同组 → 融合为一个规范 KP 节点
2. **MASTERS 边生成**：融合后，学生"张三"到 KP"对称轴"存在 MASTERS 边，weight 反映其历次考试的时间衰减加权聚合值
3. **增量融合**：上传新 CSV 后自动触发增量融合 — 仅新涉及的 KP 被融合 + 受影响学生的 MASTERS 边重算，未变化部分不受影响
4. **旧 KP 清理**：融合后旧 KP 节点被删除，入边/出边完整重定向到规范 KP 节点，无悬空边
5. **手动全量融合**：`POST /api/v1/graph/fusion/execute` → 全量融合所有 KP + 全量重算 MASTERS → 返回 `fusion_log` 记录
6. **融合日志记录**：`fusion_log` 包含完整融合明细 JSON（源 KP→目标 KP 映射 + 边重定向清单 + MASTERS 变更快照）
7. **基于日志回滚**：`POST /api/v1/graph/fusion/rollback/{fusionLogId}` → Neo4j 恢复到融合前状态（规范 KP 删除 + 源 KP 重建 + 边恢复 + MASTERS 权重回退）
8. **策略可替换**：修改 `application-dev.yml` 配置可切换 `KpMatchingStrategy` 或 `WeightCalculationStrategy` 的实现类，无需修改融合引擎核心代码

## 风险与未知

- **模糊匹配误合/漏合**：编辑距离 + Jaccard 阈值过高会漏掉应合并的 KP（如"二次函数顶点坐标" vs "顶点坐标公式"），过低会误合不相关的 KP（如"一次函数定义" vs "二次函数定义"仅一字之差但不同类）。阈值 0.85 是初始值，需真实数据校准。通过 `KpMatchingStrategy` 接口可在后续切换更精准的算法
- **融合冲突解决**（✅ 已决策 — Q3 → A）：文档抽取 KP 的非空字段覆盖 CSV 侧空字段，CSV 侧非空字段保留在 `fusionSource` 标记中供审计
- **MASTERS 公式可替换性**：v1 时间衰减加权平均假设"越近越重要"，对低频考试（如一年一考）的衰减行为需验证。`WeightCalculationStrategy` 接口预留了替换路径
- **大图重算性能**：全量融合时需遍历所有 Student + KnowledgePoint 对，若学生数 × KP 数很大（如 1000 学生 × 500 KP = 50 万对），Cypher 查询性能需评估
- **回滚日志完整性**：回滚依赖 `fusion_log` 的融合明细 JSON 完整性，若融合后 Neo4j 被外部修改（非融合路径），回滚可能不精确。回滚前需校验当前图状态与日志快照的一致性
- **并发融合**：两个管理员同时触发融合可能导致 Neo4j 写冲突。v1 通过 MySQL `fusion_log` 表的乐观锁或分布式锁（Redis）控制

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。