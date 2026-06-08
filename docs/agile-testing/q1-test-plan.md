# GraphNexus 敏捷测试四象限 — Q1：技术面·支撑团队 测试方案

> 基于图谱技术的 AI 上下文处理与精准问答系统
>
> 版本：v1.0 | 创建日期：2026-06-08
>
> 参考文档：[use-case-view.md](use-case-view.md) · [logical-view.md](logical-view.md) · [dev-view-architecture.md](dev-view-architecture.md) · [logical-view-class-diagrams.md](logical-view-class-diagrams.md) · [logical-view-state-diagrams.md](logical-view-state-diagrams.md)

---

## 一、Q1 象限定位

```
        面向业务（Business Facing）
                │
    Q2 ┌────────┼────────┐ Q3
       │  手动   │  手动   │
       │  探索性  │  用户验收 │
  支撑  │  场景   │  可用性  │ 评判
  团队  ├────────┼────────┤ 产品
       │ ★Q1★   │  自动化  │
       │  单元   │  性能   │
       │  接口   │  安全   │
    Q1 └────────┼────────┘ Q4
                │
        面向技术（Technology Facing）
```

| 维度 | Q1 说明 |
|------|--------|
| **核心问题** | "代码写对了吗？"、"改了 A 会不会破坏 B？" |
| **执行方式** | **全自动化**，CI 流水线秒级反馈 |
| **目标** | 支撑开发团队快速迭代，防止回归 |
| **覆盖层次** | L4 基础设施层 → L3 领域层 → L2 应用层 → L1 表示层 |
| **反馈时效** | 单元测试 < 1s，API 测试 < 5s，全量 < 5min |

---

## 二、测试分层策略

### 2.1 测试金字塔映射

```
                    ┌──────────┐
                    │  E2E (少) │  ← Q2/Q3 象限覆盖
                    │  0~5 个   │
                    ├──────────┤
                    │  API/组件  │  ← Q1 覆盖 (集成测试)
                    │  30~40 个  │     按模块接口
                    ├──────────┤
                    │   单元     │  ← Q1 覆盖 (核心)
                    │  150~200个 │     按类/方法
                    └──────────┘
```

### 2.2 四层架构 → 测试分层映射

```
架构层                      测试层                    测试类型
──────                     ──────                    ────────
L1 · 表示层                L1 组件测试               前端单元 + 组件快照
  (7 个前端包)              (Jest + Testing Library)

L2 · 应用层                L2 API 测试               Controller 集成测试
  (api + application)       (Spring MockMvc)         用例编排验证

L3 · 领域层                L3 领域单元测试            核心算法 + 业务规则
  (6 个领域包)              (JUnit5 + Mockito)        领域模型 + 状态机

L4 · 基础设施层             L4 适配器测试             适配器单元 + 契约测试
  (infrastructure)          (Testcontainers)          外部依赖 Mock
```

---

## 三、测试环境与工具链

### 3.1 技术选型

| 层级 | 语言/框架 | 测试框架 | Mock 框架 | 额外工具 |
|------|----------|---------|----------|---------|
| **L1 前端** | TypeScript / React | Jest + Testing Library | MSW (API Mock) | Storybook 交互测试 |
| **L2 应用层** | Java / Spring Boot | JUnit5 + MockMvc | Mockito | REST Assured |
| **L3 领域层** | Java | JUnit5 | Mockito | AssertJ, Fixture |
| **L4 基础设施** | Java | JUnit5 + Testcontainers | WireMock (LLM API) | Neo4j Testcontainer |

### 3.2 CI 流水线集成

```
Git Push
  │
  ▼
┌──────────────────────────────────────────────────────────┐
│  Stage 0: 编译检查 (30s)                                   │
│  ───────────────────────                                   │
│  · 前端: tsc --noEmit                                     │
│  · 后端: mvn compile                                      │
├──────────────────────────────────────────────────────────┤
│  Stage 1: L1+L2+L3 单元测试 (并行, < 2min)                  │
│  ───────────────────────                                   │
│  · graphnexus-common          (单元, 纯逻辑)               │
│  · graphnexus-graph-core      (单元, Mock Repository)      │
│  · graphnexus-ai-analysis     (单元, Mock LLMClient)       │
│  · graphnexus-master-data     (单元, Mock Repo)            │
│  · graphnexus-knowledge-system (单元, Mock Repo)            │
│  · graphnexus-ingestion       (单元, Mock 各 Step)         │
│  · graphnexus-operations      (单元, Mock Repo)            │
│  · graphnexus-application     (单元, Mock L3 接口)         │
│  · 前端 shared-types/components (单元, Jest)              │
│  · graphnexus-ops             (单元, Mock Infrastructure)  │
├──────────────────────────────────────────────────────────┤
│  Stage 2: L2+L4 集成测试 (< 2min)                          │
│  ───────────────────────                                   │
│  · graphnexus-api (Controller 集成, MockMvc)               │
│  · graphnexus-infrastructure (Testcontainers)              │
├──────────────────────────────────────────────────────────┤
│  Stage 3: 前端组件测试 (< 1min)                             │
│  ───────────────────────                                   │
│  · graphnexus-admin / teacher / student                   │
│  · graphnexus-ops-console / operations-console            │
└──────────────────────────────────────────────────────────┘
```

---

## 四、L3 领域层 · 单元测试（核心重点）

> L3 是系统业务核心，承载算法和领域规则，是 Q1 测试投入最大的层级。

### 4.1 M4 · graphnexus-graph-core（图谱核心引擎）

#### 4.1.1 剪枝引擎 (PruningEngine) 测试

**被测类**：`PruningEngine`（策略模式）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **PRUNE-UT-001** | 归因分析剪枝·正常路径 | targetNode=学生A, taskType=ATTRIBUTION_ANALYSIS, maxHops=3, maxNeighbors=5, weightThreshold=0.3 | 返回子图包含 ≤ 3 跳深度的节点，每跳 ≤ 5 个邻居，所有边的权重 ≥ 0.3 |
| **PRUNE-UT-002** | 剪枝·目标节点无关联数据 | targetNode 为孤立节点（无边连接） | 返回仅含目标节点的空子图，不抛异常 |
| **PRUNE-UT-003** | 剪枝·跳数限制生效 | maxHops=1, targetNode 有 3 跳深的关联 | 只返回 1 跳内的节点和边 |
| **PRUNE-UT-004** | 剪枝·邻居截断 (Top-K) | maxNeighborsPerHop=3, 实际某跳有 10 个邻居 | 每跳只保留权重最高的 3 个邻居 |
| **PRUNE-UT-005** | 剪枝·权重阈值过滤 | weightThreshold=0.5, 存在权重 0.2/0.6/0.8 的边 | 权重 < 0.5 的边被裁剪 |
| **PRUNE-UT-006** | 剪枝·关系类型白名单 | whitelist=[MASTERY, PREREQUISITE], 存在 KNOWLEDGE_REL 边 | KNOWLEDGE_REL 边被裁剪 |
| **PRUNE-UT-007** | 剪枝·深度优先 vs 广度优先 | 策略配置 maxHops=2, maxNeighbors=2 | BFS 展开，深度优先追溯前置依赖链 |
| **PRUNE-UT-008** | 剪枝·空策略回退 | strategyId=null, 无匹配策略 | 使用系统默认策略 (maxHops=3, maxNeighbors=5, threshold=0.3) |
| **PRUNE-UT-009** | A/B 对比测试 | strategyA(2跳,Top5) vs strategyB(3跳,Top3) | compareStrategy() 返回 {nodeDiff, edgeDiff, tokenEstimateDiff} |
| **PRUNE-UT-010** | 剪枝·有环图谱 | 宽图谱中存在 A→B→C→A 循环 | 不无限递归，visited 集合保证每个节点只访问一次 |

**Mock 策略**：

```java
// Mock IGraphRepository，注入剪枝引擎
@Mock IGraphRepository graphRepository;
@Mock IStrategyRepository strategyRepository;
@InjectMocks PruningEngine pruningEngine;

// 构造模拟宽图谱数据
// Student→(Mastery weight=0.8)→KP_A
// KP_A→(Prerequisite)→KP_B→(Prerequisite)→KP_C
// KP_A→(KnowledgeRel)→KP_D (weight=0.2, 应被过滤)
when(graphRepository.findSubgraph(any(), anyInt()))
    .thenReturn(mockWideGraph());
```

#### 4.1.2 权重引擎 (WeightEngine) 测试

**被测类**：`WeightEngine`（规则多态）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **WGHT-UT-001** | 行为事件·正确作答+Δ | 学生A→KP_X(weight=0.5), ExamEvent(score=100%), BehaviorRule(CORRECT=+0.05) | 权重更新为 0.55, WeightChangeLog 记录 Δ=+0.05 |
| **WGHT-UT-002** | 行为事件·错误作答-Δ | 学生A→KP_X(weight=0.5), ExamEvent(score=0%), BehaviorRule(WRONG=-0.08) | 权重更新为 0.42, WeightChangeLog 记录 Δ=-0.08 |
| **WGHT-UT-003** | 行为事件·部分正确 | 学生A→KP_X(weight=0.5), ExamEvent(score=50%), BehaviorRule(PARTIAL=-0.02) | 权重更新为 0.48 |
| **WGHT-UT-004** | 权重边界·上限截断 | 当前 weight=0.97, 触发 +0.05 | 权重=1.0（不超出 [0, 1] 范围） |
| **WGHT-UT-005** | 权重边界·下限截断 | 当前 weight=0.03, 触发 -0.08 | 权重=0.0（不超出 [0, 1] 范围） |
| **WGHT-UT-006** | 时间衰减·指数曲线 | TimeDecayRule(curve=EXPONENTIAL, halfLife=30天), lastUpdated 60天前, 初始 weight=0.8 | w' = 0.8 × e^(-ln(2)×60/30) = 0.8 × 0.25 = 0.2 |
| **WGHT-UT-007** | 时间衰减·线性曲线 | TimeDecayRule(curve=LINEAR, k=0.01/天), lastUpdated 10天前, 初始 weight=0.8 | w' = 0.8 - 0.01×10 = 0.7 |
| **WGHT-UT-008** | 时间衰减·阶梯曲线 | TimeDecayRule(curve=STEP, steps=[30天:-0.1, 60天:-0.2]), lastUpdated 45天前 | w' = 0.8 - 0.1 = 0.7（仅触发第 1 阶） |
| **WGHT-UT-009** | 前置依赖链传播 | BehaviorRule(affectScope=PREREQ_CHAIN), KP_B 依赖 KP_A | 更新 KP_B 权重时，KP_A 权重按衰减系数同步调整 |
| **WGHT-UT-010** | 手动修正覆盖 | 管理员 manualOverride(studentA, KP_X, 0.9, "专家评估") | 权重更新为 0.9, operatorSource=MANUAL_CORRECTION |
| **WGHT-UT-011** | 规则模拟·不影响真实数据 | simulateRule(ruleId, scope) | 返回预估的权重变更列表，原始数据不变 |
| **WGHT-UT-012** | 无匹配规则回退 | 事件类型无匹配 BehaviorRule | 使用默认规则 (CORRECT+0.05 / WRONG-0.08) |

#### 4.1.3 宽图谱融合 (GraphFusionService) 测试

**被测接口**：`IGraphFusionService`

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **FUS-UT-001** | 精确匹配融合（学号） | PDF图谱中有 Student(sid=001), CSV事件有 Student(sid=001) | 两个 Student 节点合并为一个 |
| **FUS-UT-002** | 模糊匹配融合（知识点） | PDF抽取"二次函数", CSV中"一元二次函数", 编辑距离=2 | 生成 EntityAlignmentPair(置信度=0.85)，推送至 UC-04 候选池 |
| **FUS-UT-003** | PageRank 计算 | 融合后图谱，topK=10 | 返回 PageRank 最高的 10 个知识点节点 |
| **FUS-UT-004** | 度中心性计算 | 融合后宽图谱 | 学生节点的度 = 关联事件数 + 关联知识点数 |
| **FUS-UT-005** | 融合范围控制 | scope=[Subject:数学] | 仅融合数学学科相关的子图 |

#### 4.1.4 图谱领域模型 测试

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **DM-UT-001** | MasteryRelation 不可变性 | 创建 MasteryRelation(weight=0.5) | weight 更新生成新对象，原对象不变 |
| **DM-UT-002** | Subgraph.merge() | subgraphA(节点A,B,C) + subgraphB(节点C,D) | 合并后节点 = {A,B,C,D}，边去重 |
| **DM-UT-003** | WideGraph.computePageRank() | 宽图谱包含环状依赖 | 算法收敛，不无限循环 |
| **DM-UT-004** | Event 多态·getScoreRatio() | ExamEvent(score=85, maxScore=100) | 得分率 = 0.85 |
| **DM-UT-005** | Subject.getWeaknessOverview() | 数学学科, 关联 3 个 KP, 学生掌握权重 [0.2, 0.5, 0.9] | 返回薄弱分布 {高危(0-0.3):1, 关注(0.3-0.6):1, 健康(0.6-1):1} |

---

### 4.2 M5 · graphnexus-ai-analysis（AI 分析引擎）

#### 4.2.1 上下文构建器 (ContextBuilderService) 测试

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **CTX-UT-001** | 子图序列化·正常 | Subgraph(15节点, 20边) | 序列化为 LLM 可读的结构化 JSON |
| **CTX-UT-002** | Token 预算控制 | subgraph(500节点), tokenLimit=8192 | estimateTokenCount() 返回 > 8192，触发自动截断 |
| **CTX-UT-003** | 额外信息融合·权重趋势 | 学生A→KP_X 近 3 次权重变化 [0.6, 0.5, 0.35] | 附加入"权重下降趋势"标注 |
| **CTX-UT-004** | 额外信息融合·教辅引用 | KP_X 来源文档《数学必修一》P42 | 附加入"原文引用：P42，题型：例题3" |
| **CTX-UT-005** | 空子图处理 | Subgraph(1节点, 0边) | 返回最简上下文结构，不抛异常 |

#### 4.2.2 分析生成器 (AnalysisGeneratorService) 测试

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **GEN-UT-001** | 归因报告生成·正常 | LLM 返回合法 JSON 归因报告 | parse 成功，返回结构化的 AttributionReport |
| **GEN-UT-002** | 归因报告·LLM 返回非法 JSON | LLM 返回含 markdown 的文本 | 尝试修复解析（提取 JSON 块），失败则返回 "格式异常" |
| **GEN-UT-003** | 归因报告·LLM 超时 | callLLM() 抛出 TimeoutException | 重试一次；仍超时则返回错误，记录日志 |
| **GEN-UT-004** | 教学建议生成·正常 | 归因报告已生成，前置依赖链已加载 | 返回 TeachingSuggestion {补救路径, 教辅资源, 时间估算} |
| **GEN-UT-005** | 复习路径生成·正常 | 学生A 的知识点列表 + 掌握权重 | 优先排序薄弱知识点，预估复习时间 |
| **GEN-UT-006** | Prompt 模板填充 | 模板 "{{student_name}}的薄弱点是{{kp_name}}" | 变量替换后 Prompt 完整且无残留占位符 |

#### 4.2.3 LLM 网关 (LLMGatewayService) 测试

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **GW-UT-001** | 模型路由·归因任务 | taskType=ATTRIBUTION_ANALYSIS | 路由到配置的归因分析模型 |
| **GW-UT-002** | 模型降级·主模型不可用 | 主模型返回 503 | switchModel() 切换到备用模型 |
| **GW-UT-003** | 配额耗尽·日配额 | 今日调用已达上限 | 返回配额耗尽错误，触发通知 |
| **GW-UT-004** | 配额检查·周配额 | getQuotaStatus(modelId) | 返回 {used, limit, remaining} |
| **GW-UT-005** | 调用日志记录 | callLLM(request) 执行完成 | LLMCallLog 记录 {时间, 模型, Token数, 延迟, 结果} |

---

### 4.3 M3 · graphnexus-ingestion（数据入库引擎）

#### 4.3.1 文档解析流水线 (DocumentIngestionPipeline)

**被测类**：`DocumentIngestionPipeline`（流水线模式）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **ING-UT-001** | PDF 流水线·正常全链路 | Mock 各 Step 均返回成功 | execute() 返回 SUCCESS，Document 状态=COMPLETED |
| **ING-UT-002** | PDF 流水线·版面分析失败 | LayoutAnalysisStep 抛出异常 | 流水线中断，Document 状态=FAILED，记录失败步骤 |
| **ING-UT-003** | PDF 流水线·NER 实体为空 | NERStep 返回空实体列表 | 流水线继续，状态=COMPLETED_WITH_WARNING |
| **ING-UT-004** | PDF 流水线·RE 关系为空 | REStep 返回空关系列表 | 流水线继续，状态=COMPLETED_WITH_WARNING |
| **ING-UT-005** | PDF 流水线·图谱导入部分失败 | GraphImportStep 返回 3/5 节点成功 | 成功部分已写入，记录失败节点列表 |
| **ING-UT-006** | PDF 流水线·格式校验失败 | 上传文件非 PDF | 拒绝上传，返回格式错误 |
| **ING-UT-007** | PDF 流水线·文件大小超限 | 文件大小 > MAX_SIZE | 拒绝上传，返回文件过大错误 |
| **ING-UT-008** | PDF 流水线·扫描版无 OCR | 版面分析检测到无文本层 | 标记 FAILED，提示"请提供文本版 PDF" |

#### 4.3.2 事件导入 (EventIngestionService) 测试

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **EVT-UT-001** | CSV 成绩导入·正常 | CSV 含 10 行有效成绩数据 | 生成 10 个 ExamEvent 节点，返回成功 10/失败 0 |
| **EVT-UT-002** | CSV 成绩导入·学号不存在·跳过 | CSV 中学号不存在，策略=SKIP | 标记为异常行跳过，不影响其他行处理 |
| **EVT-UT-003** | CSV 成绩导入·学号不存在·自动创建 | CSV 中学号不存在，策略=AUTO_CREATE | 自动创建 Student 节点，事件正常生成 |
| **EVT-UT-004** | CSV 成绩导入·知识点不匹配 | 知识点名称在 M2 中不存在 | 标记为"待确认"，生成 EntityAlignmentPair |
| **EVT-UT-005** | CSV 成绩导入·字段缺失 | 缺少必填字段 "学号" | 拒绝导入，列出缺失字段 |
| **EVT-UT-006** | CSV 成绩导入·得分非法值 | 得分 = 120（超出满分 100） | 标记为异常行跳过 |
| **EVT-UT-007** | 多类型事件导入·作业事件 | AssignmentEvent(assignType=DAILY_PRACTICE, result=CORRECT) | 正确创建作业事件节点 |
| **EVT-UT-008** | 多类型事件导入·课堂测验 | QuizEvent(quizName="课前小测", templateId=T001) | 正确创建课堂测验事件节点 |
| **EVT-UT-009** | 得分分配·多知识点平均 | ScoreAllocationRule(AVERAGE), 1题关联3个KP, 得分3/10 | 每个 KP 分配得分 = 1 |
| **EVT-UT-010** | 得分分配·按权重比例 | ScoreAllocationRule(WEIGHTED) | 按预定义权重比例分配得分 |

---

### 4.4 M2 · graphnexus-knowledge-system（知识体系）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **KNW-UT-001** | 知识点 CRUD | 创建/编辑/删除知识点 | CRUD 操作正常，删除时检查关联关系 |
| **KNW-UT-002** | 分类树·两级深度 | 学科(数学) → 模块(函数) → 知识点(二次函数) | 正确创建并维护层级关系 |
| **KNW-UT-003** | 前置依赖·合法 | KP_B 依赖 KP_A (A→B 无循环) | 创建成功 |
| **KNW-UT-004** | 前置依赖·循环检测 | KP_B 已依赖 KP_A，现添加 KP_A 依赖 KP_B | 拒绝创建，返回循环路径 [A→B→A] |
| **KNW-UT-005** | 前置依赖·自依赖 | KP_A 依赖 KP_A | 拒绝创建 |
| **KNW-UT-006** | 实体对齐·高置信度（>0.9） | 两个实体相似度 0.95 | 建议自动合并 |
| **KNW-UT-007** | 实体对齐·中置信度（0.6-0.9） | 两个实体相似度 0.75 | 推送候选池，需人工确认 |
| **KNW-UT-008** | 实体对齐·低置信度（<0.6） | 两个实体相似度 0.45 | 仅供参考标记 |
| **KNW-UT-009** | 实体对齐·确认合并 | 管理员确认合并 A→B | 副实体 A 关系迁移至主实体 B，A 删除 |
| **KNW-UT-010** | 实体对齐·驳回 | 管理员驳回合并候选 | 加入白名单，系统不再建议合并 |
| **KNW-UT-011** | 实体对齐·批量操作 | 按置信度 > 0.9 批量确认 10 对 | 返回 {合并对数, 影响关系数} |

---

### 4.5 M1 · graphnexus-master-data（主数据管理）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **MD-UT-001** | 学生·创建 | createStudent(name, sid, classId) | 学号唯一性校验通过，创建成功 |
| **MD-UT-002** | 学生·学号重复 | createStudent(sid=已存在的学号) | 抛出 DuplicateStudentException |
| **MD-UT-003** | 学生·状态转换 ACTIVE→SUSPENDED | updateStatus(SUSPENDED) | 状态更新，停止参与权重计算 |
| **MD-UT-004** | 学生·状态转换 SUSPENDED→ACTIVE | 休学期满，updateStatus(ACTIVE) | 状态恢复，重新参与权重计算 |
| **MD-UT-005** | 学生·归档不可逆 | GRADUATED/TRANSFERRED 状态尝试任意转换 | 拒绝转换，抛出 IllegalStateTransitionException |
| **MD-UT-006** | 教师·任教关系 | 教师关联班级列表 | 查询教师任教班级返回正确列表 |
| **MD-UT-007** | 权限校验·教师查看本班学生 | 教师请求查看任教班级学生数据 | 权限通过 |
| **MD-UT-008** | 权限校验·教师查看非本班学生 | 教师请求查看非任教班级学生数据 | 返回 403 Forbidden |
| **MD-UT-009** | 班级·创建与查询 | 创建班级并关联学生 | getClass() 返回班级基本信息及学生列表 |

---

### 4.6 状态机测试（跨模块）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **SM-UT-001** | Document 状态·UPLOADED→PROCESSING | 文档已上传，消息队列消费 | 状态转换成功 |
| **SM-UT-002** | Document 状态·PROCESSING→COMPLETED | 所有 Step 成功完成 | 状态转换成功 |
| **SM-UT-003** | Document 状态·PROCESSING→FAILED | 某 Step 抛出异常 | 状态转换，记录失败原因 |
| **SM-UT-004** | Document 状态·非法转换 | COMPLETED→PROCESSING | 拒绝，COMPLETED 是终态 |
| **SM-UT-005** | EntityAlignmentPair·PENDING→MERGED | 管理员确认合并 | 状态转换，执行合并操作 |
| **SM-UT-006** | EntityAlignmentPair·PENDING→REJECTED | 管理员驳回 | 状态转换，加入白名单 |
| **SM-UT-007** | ScheduledTask·SCHEDULED→RUNNING | Cron 触发或手动触发 | 状态转换，任务开始执行 |
| **SM-UT-008** | ScheduledTask·RUNNING→COMPLETED | 任务执行成功 | 状态转换，记录 TaskExecutionRecord |
| **SM-UT-009** | GraphBackup·IN_PROGRESS→VERIFIED | 备份完成，校验和匹配 | 状态转换，备份可用 |
| **SM-UT-010** | Notification·UNREAD→READ→ARCHIVED | 用户查看通知后归档 | 状态正常流转 |

---

### 4.7 M8 · graphnexus-operations（运营分析）

| 测试编号 | 测试场景 | 给定条件 | 预期行为 |
|---------|---------|---------|---------|
| **OPS-UT-001** | 文档产能统计 | 本月上传 10 份，解析成功 9 份 | 返回 {上传10, 成功9, 成功率90%} |
| **OPS-UT-002** | 知识点覆盖分析 | 数学学科有 50 个 KP，新课标要求 60 个 | 返回 {覆盖率 83.3%, 缺口 10 个} |
| **OPS-UT-003** | 文档利用效率·热门排行 | 按文档访问次数降序排列 | 返回 Top-N 热门文档 |
| **OPS-UT-004** | 文档利用效率·僵尸文档 | 入库 > 90 天未被访问 | 返回僵尸文档列表 |
| **OPS-UT-005** | 数据处理时效 | 最近 30 条 PDF 处理耗时 | 返回 P50/P95/P99 耗时及趋势 |

---

## 五、L4 基础设施层 · 适配器测试

### 5.1 graphnexus-infrastructure（适配器单元与集成测试）

| 测试编号 | 测试场景 | 测试方式 | 预期行为 |
|---------|---------|---------|---------|
| **INF-UT-001** | GraphRepository.findNode() | Testcontainers Neo4j + 预置数据 | 正确查询节点及属性 |
| **INF-UT-002** | GraphRepository.findSubgraph() | Testcontainers + 3层图谱数据 | 返回指定跳数内的子图 |
| **INF-UT-003** | GraphRepository.saveNode() | Testcontainers | 节点写入成功，查询返回一致 |
| **INF-UT-004** | GraphRepository Cypher 注入防护 | 参数化查询 | 注入特殊字符不产生非预期行为 |
| **INF-UT-005** | LLMClient.call() 正常 | WireMock 模拟 LLM API 返回 | 正确解析响应 |
| **INF-UT-006** | LLMClient.call() 重试 | WireMock 返回 429 → 200 | 重试机制生效，最终返回成功 |
| **INF-UT-007** | LLMClient.call() 超时 | WireMock 延迟 > 超时阈值 | 抛出 TimeoutException |
| **INF-UT-008** | FileStorage.upload/download | Testcontainers MinIO | 上传文件后可正确下载，MD5 一致 |
| **INF-UT-009** | MessageQueue.publish/subscribe | Testcontainers RabbitMQ | 发布消息后消费者可接收 |
| **INF-UT-010** | Cache.get/set/expire | Testcontainers Redis | 缓存读写正常，TTL 到期后 key 失效 |

---

## 六、L2 应用层 · API 集成测试

### 6.1 graphnexus-api（REST Controller 集成测试）

| 测试编号 | 测试场景 | HTTP 方法 | 预期状态码 | 说明 |
|---------|---------|----------|-----------|------|
| **API-UT-001** | 管理员上传 PDF | POST /api/admin/documents | 202 Accepted | 异步处理，返回 taskId |
| **API-UT-002** | 上传非 PDF 格式 | POST /api/admin/documents | 400 Bad Request | 错误消息指明格式要求 |
| **API-UT-003** | 上传文件过大 | POST /api/admin/documents | 413 Payload Too Large | — |
| **API-UT-004** | 管理员导入成绩 CSV | POST /api/admin/scores/import | 202 Accepted | 返回导入摘要 |
| **API-UT-005** | CSV 字段校验失败 | POST /api/admin/scores/import | 400 Bad Request | 列出缺失字段 |
| **API-UT-006** | 教师归因查询 | POST /api/teacher/attribution | 200 OK | 返回 AttributionReport |
| **API-UT-007** | 教师查询非任教学生 | POST /api/teacher/attribution | 403 Forbidden | 权限拒绝 |
| **API-UT-008** | 学生查看自己学习概览 | GET /api/student/me/overview | 200 OK | 返回掌握雷达图数据 |
| **API-UT-009** | 学生尝试查看他人数据 | GET /api/student/{otherId}/overview | 403 Forbidden | 隐私保护 |
| **API-UT-010** | 运维人员查询日志 | GET /api/ops/logs?level=ERROR | 200 OK | 返回匹配的日志列表 |
| **API-UT-011** | 未认证请求 | (任一路由) 无 Token | 401 Unauthorized | — |
| **API-UT-012** | 角色越权 | 学生调用 /api/admin/* | 403 Forbidden | — |
| **API-UT-013** | 实体对齐审核·确认合并 | POST /api/admin/alignment/{id}/merge | 200 OK | 返回合并影响面 |
| **API-UT-014** | 实体对齐审核·驳回 | POST /api/admin/alignment/{id}/reject | 200 OK | — |
| **API-UT-015** | 剪枝策略保存 | POST /api/admin/pruning-strategies | 201 Created | 返回策略 ID |
| **API-UT-016** | 权重规则配置 | POST /api/admin/weight-rules | 201 Created | 返回规则 ID |
| **API-UT-017** | 运营统计看板 | GET /api/operations/dashboard | 200 OK | 返回四大维度数据 |

### 6.2 graphnexus-application（用例编排测试）

| 测试编号 | 测试场景 | Mock 依赖 | 预期行为 |
|---------|---------|----------|---------|
| **APP-UT-001** | UC-10 归因查询完整编排 | Mock 所有 L3 接口 | 按正确顺序调用 IAuthorizationService → IPruningService → IContextBuilderService → ILLMGatewayService |
| **APP-UT-002** | UC-10 权限校验失败不继续 | Mock IAuthorizationService 返回 false | 不调用后续服务，直接返回 403 |
| **APP-UT-003** | UC-10 剪枝为空子图不调用 LLM | Mock IPruningService 返回空子图 | 返回"数据不足"，不调用 LLM |
| **APP-UT-004** | UC-10 LLM 降级 | Mock ILLMGateway 主模型不可用 | 自动切换备用模型，不影响最终结果 |
| **APP-UT-005** | UC-01 PDF 上传与后续流程编排 | Mock IFileStorage + IMessageQueue | 上传文件 → 发送队列消息 → 返回 202 |
| **APP-UT-006** | UC-02 成绩导入编排 | Mock IEventIngestionService | CSV 数据 → Event 节点 → 触发权重更新 |

---

## 七、L1 表示层 · 前端组件测试

### 7.1 共享组件库（graphnexus-shared-components）

| 测试编号 | 测试场景 | 测试方式 | 预期行为 |
|---------|---------|---------|---------|
| **UI-UT-001** | 知识图谱可视化组件 | Jest + Testing Library | 正确渲染力导向图，节点/边数量匹配 props |
| **UI-UT-002** | 图谱组件交互·节点点击 | 模拟点击节点 | 触发 onNodeClick 回调，传入正确 nodeId |
| **UI-UT-003** | 图谱组件·空数据 | 传入空 nodes/edges | 显示空状态提示，不崩溃 |
| **UI-UT-004** | 雷达图组件 | 传入 5 个学科权重 | 渲染 5 边形雷达图 |
| **UI-UT-005** | 趋势折线图组件 | 传入 6 个月份数据 | 正确渲染折线图 |
| **UI-UT-006** | 置信度标签组件 | confidence=HIGH | 渲染绿色标签 |
| **UI-UT-007** | 置信度标签组件 | confidence=LOW | 渲染红色标签 |
| **UI-UT-008** | 文件上传组件·正常 | 选择 PDF 文件 | 触发 onUpload 回调 |
| **UI-UT-009** | 文件上传组件·格式限制 | 选择 .exe 文件 | 显示错误提示，不触发上传 |

### 7.2 各前端应用冒烟测试

| 前端应用 | 关键页面 | 测试重点 |
|---------|---------|---------|
| **graphnexus-admin** | 数据入库页 | PDF 上传、CSV 导入、处理状态轮询 |
| | 实体对齐页 | 候选对列表、批量操作、合并确认 |
| | 策略配置页 | 剪枝参数表单、权重规则表单 |
| **graphnexus-teacher** | 归因查询页 | 学生选择、知识点检索、报告渲染、追问下钻 |
| | 班级概览页 | 薄弱点 TopN、排名分布 |
| **graphnexus-student** | 学习概览页 | 雷达图、薄弱列表、隐私保护 |
| **graphnexus-ops-console** | 日志查询页 | 多维查询表单、Trace 链路展示 |
| **graphnexus-operations-console** | 运营看板页 | 四大维度卡片、图表渲染 |

---

## 八、公共库测试

### 8.1 graphnexus-common

| 测试编号 | 测试场景 | 预期行为 |
|---------|---------|---------|
| **COM-UT-001** | BaseEntity 序列化/反序列化 | JSON ↔ Entity 双向转换一致 |
| **COM-UT-002** | ValueObject 相等性 | 同值对象 equals() 返回 true |
| **COM-UT-003** | BusinessException 继承体系 | 各子类正确携带错误码和消息 |
| **COM-UT-004** | 工具类·字符串工具 | 边界条件处理正确（空串、null） |
| **COM-UT-005** | 工具类·日期工具 | 时区处理正确 |

---

## 九、测试数据管理策略

### 9.1 Fixture 数据定义

```java
// 测试夹具：标准宽图谱结构
public class WideGraphFixtures {
    // 学生A：数学薄弱型
    public static Student studentA() {
        return Student.builder()
            .studentId("STU-001")
            .name("张三")
            .classId("CLASS-3-2")
            .status(StudentStatus.ACTIVE)
            .build();
    }

    // 知识点B：二次函数（核心薄弱点）
    public static KnowledgePoint kpQuadraticFunction() {
        return KnowledgePoint.builder()
            .kpId("KP-MATH-042")
            .name("二次函数")
            .subjectId("SUBJ-MATH")
            .source(KpSource.AUTO_EXTRACTED)
            .build();
    }

    // 掌握关系：低权重
    public static MasteryRelation weakMastery() {
        return MasteryRelation.builder()
            .studentId("STU-001")
            .kpId("KP-MATH-042")
            .weight(0.25)
            .lastUpdated(LocalDateTime.now().minusDays(60))
            .build();
    }
}
```

### 9.2 Mock 数据原则

| 原则 | 说明 |
|------|------|
| **每个测试独立** | 不依赖其他测试的数据状态，使用 @BeforeEach 初始化 |
| **代表性数据** | 同时包含正常值、边界值、异常值 |
| **显式优于隐式** | 测试方法内明确声明 Mock 行为，不使用全局 @Mock 配置 |
| **就近原则** | Mock 数据定义在测试类内部或测试 Fixture 类中 |

---

## 十、测试质量标准

### 10.1 覆盖率目标

| 层级 | 行覆盖率 | 分支覆盖率 | 说明 |
|------|---------|-----------|------|
| **L3 领域核心 (graph-core, ai-analysis)** | ≥ 90% | ≥ 85% | 核心算法和规则，高覆盖要求 |
| **L3 领域支撑 (其余 4 个模块)** | ≥ 80% | ≥ 75% | 业务逻辑 |
| **L2 应用层 (api, application)** | ≥ 75% | ≥ 70% | 编排层，重点覆盖异常分支 |
| **L4 基础设施层** | ≥ 70% | ≥ 65% | 适配器，集成测试为主 |
| **L1 前端** | ≥ 70% | — | 组件测试 + 工具函数 |

### 10.2 测试用例命名规范

```
{模块缩写}-{测试层级}-{序号}
示例: PRUNE-UT-001, WGHT-UT-001, API-UT-001

测试方法名规范（Java）:
should_{预期行为}_when_{条件}

示例:
should_returnSubgraphWithin3Hops_when_targetNodeHasDeepConnections
should_rejectTransition_when_archivedStudentStatusChange
```

### 10.3 质量门禁

| 门禁项 | 阈值 | 阻断级别 |
|--------|------|---------|
| 单元测试全量通过率 | 100% | **阻断合入** |
| L3 核心模块覆盖率下降 | 不允许下降 | **阻断合入** |
| 新增代码覆盖率 | ≥ 80% | **阻断合入** |
| 测试执行时间（Stage 1） | < 2min | 警告 |
| 测试中存在 `@Disabled` | 需注释说明原因+到期日 | 警告 |
| 测试中存在 `Thread.sleep()` | 不允许（用 Awaitility 替代） | **阻断合入** |

---

## 十一、MVP 阶段聚焦清单

针对 MVP 6 个核心用例（UC-01/02/04/05/06/10），Q1 测试**必须覆盖**以下测试用例：

| 优先级 | 对应的 MVP 用例 | 必须覆盖的测试编号 |
|--------|---------------|-------------------|
| **P0** | UC-01 PDF上传与图谱构建 | ING-UT-001~008 |
| **P0** | UC-02 学生管理与成绩导入 | MD-UT-001~005, EVT-UT-001~006 |
| **P0** | UC-04 实体对齐审核 | KNW-UT-006~011 |
| **P0** | UC-05 知识体系管理 | KNW-UT-001~005 |
| **P0** | UC-06 剪枝策略配置 | PRUNE-UT-001~010 |
| **P0** | UC-10 单学生归因查询 | PRUNE-UT-001, WGHT-UT-001~008, CTX-UT-001~005, GEN-UT-001~003, GW-UT-001~005, APP-UT-001~005 |

---

## 十二、附录：测试用例总量统计

| 模块 | 单元测试数 | API/集成测试数 | 小计 |
|------|-----------|--------------|------|
| graphnexus-graph-core (M4) | 32 | — | 32 |
| graphnexus-ai-analysis (M5) | 17 | — | 17 |
| graphnexus-ingestion (M3) | 18 | — | 18 |
| graphnexus-knowledge-system (M2) | 11 | — | 11 |
| graphnexus-master-data (M1) | 9 | — | 9 |
| graphnexus-operations (M8) | 5 | — | 5 |
| 跨模块状态机 | 10 | — | 10 |
| graphnexus-infrastructure (L4) | — | 10 | 10 |
| graphnexus-api (L2) | — | 17 | 17 |
| graphnexus-application (L2) | — | 6 | 6 |
| graphnexus-common | 5 | — | 5 |
| graphnexus-ops (M7) | 4 | — | 4 |
| 前端组件/页面 | 15 | — | 15 |
| **合计** | **126** | **33** | **159** |

---

> **文档说明**：本文档为 GraphNexus 项目 Q1（技术面·支撑团队）测试方案，覆盖单元测试与 API/组件集成测试。Q2（业务面·支撑团队）、Q3（业务面·评判产品）、Q4（技术面·评判产品）测试方案将在后续文档中分别阐述。