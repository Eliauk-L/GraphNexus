# GraphNexus Q1 MVP 测试方案 — 技术面·支撑团队

> 版本：MVP v1.0 | 创建日期：2026-06-09
>
> 原则：**最小用例集覆盖全部功能** — 共计 4 个测试用例，覆盖需求文档 3 阶段 6 项任务

---

## 需求覆盖矩阵

| 需求任务 | Q1 覆盖 |
|---------|:--:|
| 阶段1.1 PDF文档图谱化（版面分析+NER/RE→知识图谱） | ✅ |
| 阶段1.2 CSV成绩事件化（事件节点+学生/知识点关联） | ✅ |
| 阶段1.3 图谱数据导入Neo4j | ✅ |
| 阶段2.1 实体对齐（精确匹配+模糊匹配） | ✅ |
| 阶段2.2 宽图谱构建（学生/知识点为图钉融合） | ✅ |
| 阶段2.3 PageRank/度中心性计算 | ✅ |
| 阶段3.1 图剪枝+LLM归因分析 | ✅ |
| 阶段3.2 动态权重更新（时间衰减+行为调整） | ✅ |

---

## 测试用例

### TC-Q1-01 · PDF文档图谱化全链路单元测试

| 维度 | 说明 |
|------|------|
| **覆盖任务** | 阶段1.1 文档图谱化 + 阶段1.3 图谱导入Neo4j |
| **测试类型** | 单元测试（Mock 外部依赖） |
| **被测对象** | `DocumentIngestionPipeline` + `GraphImportStep` |

**Given**：Mock 一份 15 页 PDF 文件的解析结果，包含：
- 版面分析产出：3 个正文区域、2 个公式区域、1 个表格区域
- NER 产出：5 个知识点实体（二次函数、配方法、判别式、一元二次方程、抛物线）
- RE 产出：4 条关系（引用×2、推导×1、前置依赖×1）

**When**：执行 `DocumentIngestionPipeline.execute(document)`

**Then**：
1. `LayoutAnalysisStep` → `NERStep` → `REStep` → `GraphImportStep` 四个步骤按序执行
2. `GraphImportStep` 被调用时传入 5 个实体节点 + 4 条关系边
3. Document 状态 = `COMPLETED`
4. 处理摘要包含：`{entityCount: 5, relationCount: 4}`
5. 低置信度实体列表推送至实体对齐候选池

---

### TC-Q1-02 · CSV成绩事件化+权重更新单元测试

| 维度 | 说明 |
|------|------|
| **覆盖任务** | 阶段1.2 CSV事件化 + 阶段1.3 Neo4j导入 + 阶段3.2 动态权重更新 |
| **测试类型** | 单元测试（Mock Repository） |
| **被测对象** | `EventIngestionService` + `WeightEngine` |

**Given**：
- Mock M1 中学生 STU-001 存在（状态=ACTIVE）
- Mock M2 中知识点 KP-MATH-042（二次函数）存在，当前权重=0.5
- CSV 数据：`STU-001, 数学, 二次函数, 45, 100`
- 权重规则：错误作答（得分<60%）= -0.08

**When**：执行 `EventIngestionService.importCSV(csvFile)` → 触发 `WeightEngine.adjustWeightByEvent(eventId)`

**Then**：
1. 生成 1 个 `ExamEvent` 节点（studentId=STU-001, kpId=KP-MATH-042, score=45）
2. ExamEvent 边关联到 Student 和 KnowledgePoint
3. 权重引擎触发：匹配行为规则"错误作答"
4. STU-001 → 二次函数 权重变更为 0.42（0.5 - 0.08）
5. `WeightChangeLog` 记录变更（前值 0.5, 新值 0.42, Δ=-0.08, 触发源=EXAM_EVENT）

---

### TC-Q1-03 · 实体对齐+宽图谱融合+PageRank 单元测试

| 维度 | 说明 |
|------|------|
| **覆盖任务** | 阶段2.1 实体对齐 + 阶段2.2 宽图谱构建 + 阶段2.3 PageRank计算 |
| **测试类型** | 单元测试（Mock Neo4j） |
| **被测对象** | `EntityAlignmentService` + `GraphFusionService` |

**Given**：Mock 两个独立图谱：
- 文档图谱：知识点[二次函数](来自PDF)、知识点[配方法](来自PDF)
- 事件图谱：知识点[一元二次函数](来自CSV)、学生[STU-001]→掌握→[一元二次函数]
- 模糊匹配："二次函数" vs "一元二次函数" 编辑距离=2，置信度=0.85

**When**：执行 `GraphFusionService.buildWideGraph(scope)` → 包含实体对齐流程

**Then**：
1. `EntityAlignmentService` 生成候选匹配对（entityA="二次函数", entityB="一元二次函数", confidence=0.85）
2. 精确匹配：学生 STU-001 在两类图谱中学号一致 → 自动合并
3. 融合后宽图谱以学生和知识点为"图钉"节点，文档图谱+事件图谱边全部保留
4. `computePageRank(topK=10)` 返回 PageRank 最高的知识点列表
5. 核心知识点（如二次函数）因关联学生+前置依赖+文档来源，PageRank 显著高于孤立知识点

---

### TC-Q1-04 · 剪枝引擎+LLM归因分析 单元测试

| 维度 | 说明 |
|------|------|
| **覆盖任务** | 阶段3.1 图剪枝+LLM归因分析 + 阶段3.2 动态权重更新 |
| **测试类型** | 单元测试（Mock LLMClient） |
| **被测对象** | `PruningEngine` + `AnalysisGeneratorService` |

**Given**：Mock 宽图谱子集——
- 学生 STU-001 → (Mastery weight=0.42) → 二次函数
- 二次函数 → (Prerequisite) → 配方法 → (Prerequisite) → 一元二次方程
- STU-001 → (Mastery weight=0.90) → 一元二次方程
- 剪枝策略：taskType=ATTRIBUTION_ANALYSIS, maxHops=3, maxNeighbors=5, weightThreshold=0.3
- Mock LLMClient 返回：`{"rootCauses": [{"cause": "配方法掌握不足", "evidence": ["前置依赖链:配方法权重缺失", "二次函数权重0.42"], "confidence": "高"}]}`

**When**：执行 `PruningEngine.prune(STU-001, ATTRIBUTION_ANALYSIS)` → 子图传入 `AnalysisGeneratorService.generateAttribution(subgraph)`

**Then**：
1. 剪枝子图包含：STU-001、二次函数、配方法、一元二次方程（沿前置依赖链上溯3跳）
2. 权重 0.42（<0.3? 否，保留）→ 子图中保留该 MASTERY 边
3. 子图序列化传入 LLM，Prompt 模板变量正确填充
4. LLM 返回解析为结构化 `AttributionReport`：根因≥1项，每项含 evidence + confidence
5. 端到端耗时（Mock LLM 下）< 1s

---

## Q1 MVP 用例汇总

| 编号 | 用例名称 | 覆盖任务 | 类型 |
|------|---------|---------|------|
| TC-Q1-01 | PDF文档图谱化全链路 | 1.1 + 1.3 | 单元 |
| TC-Q1-02 | CSV事件化+权重更新 | 1.2 + 1.3 + 3.2 | 单元 |
| TC-Q1-03 | 实体对齐+融合+PageRank | 2.1 + 2.2 + 2.3 | 单元 |
| TC-Q1-04 | 剪枝引擎+LLM归因 | 3.1 + 3.2 | 单元 |

> 4 个用例覆盖全部 8 项任务（含隐性任务 1.3），每个用例模拟完整链路的最小可验证单元。
