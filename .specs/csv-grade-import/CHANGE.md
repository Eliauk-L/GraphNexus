# CHANGE: CSV 成绩文件上传与解析入库

- **Change ID**: `csv-grade-import`
- **创建日期**: 2026-06-15
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

`knowledge-graph-extraction` 已落地文档图谱（PDF → Entity → KnowledgePoint），打通了从教辅资料到知识体系的链路。但当前系统缺失另一条关键数据源：**学生考试成绩**。

考试成绩是掌握度分析、薄弱点诊断、个性化推荐的**核心证据源**。没有成绩数据，知识图谱只有"教了什么"而不知道"学生学会了什么"，宽图谱融合和任务驱动剪枝无法发挥实际价值。

本 change 是连接「结构化成绩数据」与「图分析」的桥梁：将 CSV 成绩文件解析为 Neo4j 中的 Student → Exam → KnowledgePoint 三元图谱路径，为后续宽图谱融合和智能诊断提供数据基础。

参考文档 `docs/知识图谱构建示例-初中数学教辅.md` 第四步，本次聚焦其**简化版**落地。

## What（做什么）

基于既有四层架构，实现 **CSV 成绩文件上传 → 解析 → MySQL + MinIO + Neo4j 全链路**：

1. **CSV 文件上传**：复用现有 `POST /api/v1/document/upload` 接口（`multipart/form-data`，参数 `file` + `subject`），系统按 `.csv` 后缀自动识别为成绩文件并走成绩处理链路，`.pdf` 走既有文档处理链路不变
2. **CSV 解析**：解析双行表头格式（第 1 行列名 `题1~题N`，第 2 行知识点名称，同一题多知识点用 `;` 分隔），成绩格式 `raw_score/max_score`，提取学生信息（学号/姓名/班级）和各题得分，一题关联多知识点时同一得分复制到每条 TESTED 边
3. **MySQL 持久化**：新建独立表 `exam_record`，每行 CSV 数据对应一条记录，各题成绩与知识点以 JSON 格式存储在 `score_details` 列中
4. **MinIO 文件存储**：原始 CSV 文件上传至 MinIO `grades/` 文件夹（教辅 PDF 存入 `textbooks/` 文件夹），路径存入 MySQL 记录
5. **Neo4j 图存储**（简化模型，不做 EventNode）：
   - 创建/合并 `Student` 节点（以 `studentNo` 为唯一标识，含 `name`、`className`、`grade`）
   - 创建 `Exam` 节点（`name` + `examDate` + `subject`，同一 CSV 文件共享一个 Exam 节点）
   - 为每道题创建 `KnowledgePoint` 节点（**新建**，不与已有 KP 融合，融合属于宽图谱 change）
   - 创建 `ATTENDED` 边：`Student → Exam`（纯结构，无属性）
   - 创建 `TESTED` 边：`Exam → KnowledgePoint`（纯结构，无属性。去重后每个知识点一条）
   - 图谱路径：`(:Student) -[:ATTENDED]-> (:Exam) -[:TESTED]-> (:KnowledgePoint)`
   - **所有分数仅存 MySQL `exam_record`**，Neo4j 不存数值
6. **同步处理**：上传请求内完成解析 + MySQL 写入 + MinIO 上传 + Neo4j 写入，同步返回结果
7. **成绩删除**：`DELETE /api/v1/document/grade/exam/{examNo}` 级联删除 MySQL 记录 + MinIO CSV 文件 + Neo4j Exam 节点及 ATTENDED/TESTED 边，保留 Student 和 KnowledgePoint 节点

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增功能需求（CSV 上传/解析/入库的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① CSV 解析策略与容错；② MySQL `exam_record` 表结构 + JSON 列设计；③ Neo4j 简化事件模型（Student + Exam + KnowledgePoint + ATTENDED + TESTED）；④ 学号策略（CSV 提供 vs 自动生成）
- [ ] 影响现有 AC — 无已有 AC，不冲突
- [x] 影响数据模型 / 迁移 — MySQL 新增 `exam_record` 表；Neo4j 新增 `Student` 节点、`Exam` 节点、`ATTENDED` 边、`TESTED` 边（KnowledgePoint 节点复用已有类型）；MinIO 新增 CSV 文件存储路径
- [x] 影响外部 API 兼容性 — 复用现有 `/api/v1/document/upload` 接口，按文件后缀分流处理（`.pdf` 原链路 / `.csv` 成绩链路），新增成绩查询端点 `GET /api/v1/document/grade/exam/{examName}`，新增级联删除端点 `DELETE /api/v1/document/grade/exam/{examNo}`，仅增量不破坏
- [ ] 仅修复 bug，无范围变化
- [ ] 依赖新模块 — 复用既有基础设施（MinIO `FileStorageService`、Neo4j `GraphNodeRepository`、MySQL JPA），不引入新依赖

## 核心设计约束（进入 DESIGN 前必须遵守）

> 本 change 是事件图谱的**简化第一版**，聚焦 CSV 数据的结构化入库，为后续宽图谱融合留接口。

- CSV 解析出的 KnowledgePoint **不与已有 KP 节点融合**（融合逻辑属于独立 change `wide-graph-fusion`）
- Neo4j 侧**不做 EventNode**（采用 Student → Exam → KnowledgePoint 三元路径，ATTENDED + TESTED 边携带成绩属性，EventNode 留待后续 change 按需引入）
- Student 节点以 `studentNo`（学号）为唯一标识，支持 MERGE（同一学生多次考试复用同一 Student 节点）
- Exam 节点以 CSV 中的 `考试编号` 列为唯一标识（格式如 `E20200041`），同一 CSV 文件共享一个 Exam 节点
- 同步处理模式，与既有 `document-process-pdf-minimal` 一致
- 遵循既有四层架构 + 构造器注入 + GraphNode/GraphEdge 抽象体系

## 范围排除（这次不做）

- ❌ **EventNode / HAS_EVENT / RELATES_TO / BELONGS_TO_EXAM**：本次采用 Student → Exam → KnowledgePoint 三元模型，不做事件节点
- ❌ **MASTERS 边（Student → KnowledgePoint 直连）**：本次通过 Exam 中介表达成绩，不做 Student 到 KnowledgePoint 的直连掌握度边（该边属于宽图谱融合 change 的聚合输出）
- ❌ **与已有 KnowledgePoint 融合**：CSV 解析出的 KP 节点独立存在，不与文档图谱中的 KP 做 ALIGNED_TO 或去重合并（宽图谱融合属于独立 change）
- ❌ **掌握度跨考试累积计算**：同一学生多次考试涉及同一知识点时，每次考试的 TESTED.weight 独立存储，不做跨考试加权平均（聚合计算留待宽图谱融合 change）
- ❌ **异步处理**：用户明确选择同步模式
- ❌ **CSV 模板验证/纠错 UI**：纯后端 API，不做前端校验界面
- ❌ **学号自动生成**：CSV 文件必须包含学号列，系统不做自动生成（若 CSV 无学号则拒绝）
- ❌ **QuestionNode**：每道题的细粒度节点模型超出本次范围

## 验收线（粗粒度，不是 AC）

1. **CSV → MySQL 端到端**：上传示例 CSV 文件（`docs/学生成绩表示例.csv`）→ MySQL `exam_record` 表中可查询到每条学生记录，`score_details` JSON 包含完整题目-知识点-得分映射
2. **CSV → Neo4j 端到端**：解析后 Neo4j 中可查询到：
   - `Student` 节点（每名学生一个，含学号/姓名/班级）
   - `Exam` 节点（每次考试一个，含名称/日期/学科）
   - `KnowledgePoint` 节点（每题对应的知识点一个，同 CSV 内同名 KP 合并为一个节点）
   - `ATTENDED` 边：`Student → Exam`（含总分/班级排名）
   - `TESTED` 边：`Exam → KnowledgePoint`（含得分/满分/得分率 weight）
   - 完整路径 `(:Student) -[:ATTENDED]-> (:Exam) -[:TESTED]-> (:KnowledgePoint)` 可遍历
3. **MinIO 文件留存**：原始 CSV 文件保存在 MinIO 中，MySQL 记录包含有效的 `csv_file_path`
4. **重复上传幂等**：同一 CSV 文件（MD5 判重）重复上传时，MySQL 记录覆盖更新，Neo4j 中 ATTENDED 和 TESTED 边属性更新为最新值
5. **解析容错**：CSV 中存在个别行成绩格式异常时（如 `-/-` 表示缺考），该题 score 记为 NULL 而非整体失败

## 风险与未知

- **学号来源**：当前示例 CSV 无学号列，需确认实际 CSV 模板是否包含学号，或需在 REQUIREMENT 阶段确定学号策略（CSV 提供 vs 拼接生成 `班级+姓名` hash）
- **CSV 列数动态变化**：不同考试题目数不同（示例为 18 题），解析器需支持动态列数
- **大 CSV 文件**：若一个 CSV 包含全年级数百学生 × 数十题，Neo4j 同步写入的延迟需评估
- **TESTED 边 weight 语义**：weight = rawScore / maxScore 仅表达单次考试得分率，同一学生多次考试涉及同一知识点时（通过不同 Exam 节点），历史趋势需跨 Exam 聚合查询，MASTERS 聚合边留待宽图谱融合 change
- **考试编号策略**：`examNo` 来源于 CSV 中的 `考试编号` 列（如 `E20200041`），系统不做自动生成

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。