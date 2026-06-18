# DESIGN: 宽图谱融合 — 多源知识图谱合并与掌握度聚合

- **Change ID**: `wide-graph-fusion`
- **关联**: `@.specs/wide-graph-fusion/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 技术栈已在 `@.specs/CONTEXT.md`「已锁技术决策」中锁定，本 change 无新增外部依赖。

- **选定**：延续既有 Java 17 + Spring Boot 3.3.x 四层架构
- **后端**：Spring Boot 3.3.x / Spring MVC / Spring Data JPA + Neo4jClient
- **数据库**：MySQL 8.0（新增 `fusion_log` 表）+ Neo4j 5.x（新增 `MASTERS` 边 + KnowledgePoint 节点 `fusionSource` 字段）
- **关键依赖**：无新增 `pom.xml` 依赖。Jackson（JSON 序列化，已有）、Lombok（已有）
- **理由**：融合引擎是既有图节点/边抽象体系上的纯业务逻辑扩展，无需新框架或库。模糊匹配算法手写实现（编辑距离 + N-gram Jaccard），不引入 NLP 库
- **明确排除**：不引入 Lucene/Elasticsearch（全文搜索引擎对百级 KP 规模过度）、不引入 HanLP/jiagu（NLP 分词库对 v1 阈值匹配过度）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 确认的实际清单）：
- infrastructure/neo4j/edge/EdgeType.java              （既有 · 新增 MASTERS 枚举值）
- infrastructure/neo4j/node/NodeType.java              （既有 · 无变更，仅引用）
- infrastructure/neo4j/node/KnowledgePointNode.java    （既有 · 新增 fusionSource 字段 + toProperties() 覆盖）
- infrastructure/neo4j/repository/GraphNodeRepository.java （既有 · 新增 KP 查询/合并/边重定向/MASTERS 批量写入方法）
- infrastructure/mysql/document/ExamRecordRepository.java  （既有 · 新增按 KP 名称查询成绩方法）
- application/document/service/impl/GradeServiceImpl.java   （既有 · 上传后调用 FusionService 增量融合）
- application/graph/service/impl/GraphServiceImpl.java     （既有 · 抽取后调用 FusionService 增量融合）
- common/exception/ErrorCode.java                      （既有 · 新增 A0012~A0014）

新增模块：
- api/graph/controller/FusionController.java           （新 · L1 融合触发/状态/回滚端点）
- api/graph/dto/FusionExecuteVO.java                   （新 VO）
- api/graph/dto/FusionStatusVO.java                     （新 VO）
- api/graph/dto/FusionRollbackVO.java                   （新 VO）
- application/graph/fusion/strategy/KpMatchingStrategy.java     （新接口）
- application/graph/fusion/strategy/FuzzyMatchStrategy.java     （新实现 · v1）
- application/graph/fusion/strategy/ExactMatchStrategy.java     （新实现 · 测试桩）
- application/graph/fusion/strategy/WeightCalculationStrategy.java （新接口）
- application/graph/fusion/strategy/TimeDecayStrategy.java     （新实现 · v1）
- application/graph/fusion/strategy/SimpleAverageStrategy.java  （新实现 · 测试桩）
- application/graph/fusion/model/FusionCandidate.java           （新 BO · KP 候选）
- application/graph/fusion/model/FusionGroup.java               （新 BO · 融合组）
- application/graph/fusion/model/MastersCalculation.java        （新 BO · MASTERS 计算结果）
- application/graph/fusion/model/FusionLogBO.java               （新 BO）
- application/graph/fusion/service/FusionService.java           （新接口）
- application/graph/fusion/service/impl/FusionServiceImpl.java  （新实现）
- application/graph/fusion/config/FusionProperties.java         （新 · yml 配置映射）
- infrastructure/mysql/fusion/FusionLogDO.java                  （新 DO）
- infrastructure/mysql/fusion/FusionLogRepository.java           （新 Repository）
- infrastructure/neo4j/edge/MastersEdge.java                    （新边类）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- infrastructure/llm/                                   （LLM 模块，融合不调 LLM）
- application/graph/extraction/                         （抽取模块，融合不涉及 Prompt/Schema）
- application/document/parser/                          （解析器模块，融合不解析文件）
- infrastructure/storage/                               （MinIO，融合不操作文件）
- pom.xml                                               （禁动清单项，无新增依赖）
- docs/项目规范.md                                      （禁动清单项）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 图边类型注册 | `EdgeType` 枚举（8 种边） | **沿用**，新增 `MASTERS("MASTERS")` 枚举值 |
| 图节点属性映射 | `GraphNode.toProperties()` 多态方法 | **沿用**，KnowledgePointNode 覆盖追加 `fusionSource` |
| 图节点持久化 | `GraphNodeRepository.save(GraphNode)` (MERGE) | **沿用**，融合后的规范 KP 走同一 MERGE 逻辑 |
| 图边持久化 | `GraphNodeRepository.saveEdge(GraphEdge)` (CREATE) | **沿用**，MastersEdge 走同一逻辑 |
| 图边批量写入 | `GraphNodeRepository.saveAllEdges(List)` (UNWIND) | **沿用**，MASTERS 边批量写入复用 |
| 图按 examNo 删边 | `GraphNodeRepository.deleteEdgesByExamNo()` | **沿用**，回滚时需删除 MASTERS 边 |
| Neo4j Cypher 查询 | `Neo4jClient.query().bindAll().fetch()` | **沿用**，融合查询全走 Neo4jClient |
| MySQL JPA | Spring Data JPA + `@Transactional` | **沿用**，fusion_log CRUD |
| 构造器注入 | `@RequiredArgsConstructor` | **沿用** |
| API 响应包装 | `ApiResponse<T>` | **沿用** |
| 异常处理 | `BusinessException` + `ErrorCode` | **沿用**，新增 A0012(融合日志不存在)/A0013(融合进行中)/A0014(回滚校验失败) |
| yml 配置绑定 | `@ConfigurationProperties` | **沿用**，FusionProperties |
| KP 模糊匹配 | **没有** | **新建**（理由：项目首次需要字符串模糊匹配能力） |
| 权重计算 | **没有** | **新建**（理由：项目首次需要聚合计算策略） |
| 融合日志/回滚 | **没有** | **新建**（理由：项目首次需要操作审计与回滚） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 依赖注入：     **沿用** 构造器注入（@RequiredArgsConstructor）
- 分层架构：     **沿用** L1(FusionController+VO) → L2(FusionService+BO) → L3(Repository+DO)
- Neo4j 持久化： **沿用** Neo4jClient + 手动 Cypher（MERGE/CREATE/UNWIND/DETACH DELETE）
- 策略模式：     **引入新模式** KpMatchingStrategy + WeightCalculationStrategy（理由：REQUIREMENT 明确要求可扩展替换，项目首次在此领域用策略模式，但 Spring 依赖注入天然支持）
- 融合日志：     **引入新模式** 操作审计日志 + 快照 JSON（理由：项目首次需要可回滚的操作日志，既有无此抽象）
- 自动触发钩子： **引入新模式** Service 层事件委托（理由：无 MQ，在 GradeServiceImpl/GraphServiceImpl 中同步调用 FusionService）
- 事务管理：     **沿用** @Transactional 仅放 L2，Neo4j 与 MySQL 事务独立
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | **KP 匹配策略**：定义 `KpMatchingStrategy` 接口，v1 实现为 `FuzzyMatchStrategy`（多度量组合：α×字符Jaccard + β×BigramJaccard + γ×(1-归一化编辑距离)，默认 α=0.3/β=0.5/γ=0.2，阈值 0.85） | ① 纯编辑距离 ② 纯字符 Jaccard ③ 调用 LLM 判断 | ① 对长度差异敏感（"顶点坐标公式" vs "二次函数顶点坐标" 编辑距离大但语义相关）；② 对词序不敏感（"一次函数图像" vs "二次函数图像" 字符集高度重叠）；③ v1 不调 LLM。多度量组合兼顾字面重叠与词序信息，BigramJaccard 捕获局部词序（"顶点坐标"作为公共 bigram），字符 Jaccard 预防 bigram 粒度过细的漏合 | 三度量组合需要调参（α/β/γ），阈值 0.85 是初始值，需真实数据校准。`FusionProperties` 中全部可配置 |
| D2 | **权重计算策略**：定义 `WeightCalculationStrategy` 接口，v1 实现为 `TimeDecayStrategy` 时间衰减加权平均 | ① 简单算术平均 ② 仅取最近一次 ③ 指数加权移动平均(EWMA) | 用户选择 Q2→B。时间衰减对教育场景合理：最近考试更反映当前掌握水平。公式：weight = Σ(scoreRate_i × d^months_i) / Σ(d^months_i)，d=0.9，缺考跳过。同考试多次考同一 KP 先取平均再参与衰减 | 衰减因子 0.9/月使半年前的考试权重降到 0.9⁶≈0.53，低频考试场景（如一年一考）需调整。因子通过 yml 可配置 |
| D3 | **融合引擎编排**：`FusionService` 统一编排全量/增量融合流程。全量：查询所有 KP → 匹配分组 → 逐组合并 → 全量重算 MASTERS → 写 fusion_log。增量：仅查询受影响的 KP（新旧 KP 名称交集）→ 匹配分组 → 合并 → 仅重算受影响学生的 MASTERS | ① 全量和增量两套独立 Service ② 每次上传全量重融 | ① 代码重复且维护两份合并逻辑；② 大图重算性能不可接受。选择统一引擎 + 参数控制范围（scope=FULL/INCREMENTAL），增量通过传入 affected KpNames 限定范围 | 增量融合需正确识别"受影响学生"（所有通过 TESTED 边关联到被融合 KP 的 Student），Cypher 查询需多跳遍历 |
| D4 | **KP 合并算法**：对每个融合组：① 选一个 KP 为规范节点（优先选带 documentId 的文档 KP，字段更全）；② 将其余 KP 的入边/出边重定向到规范节点；③ DETACH DELETE 删除其余 KP；④ 规范节点设 `fusionSource = "DOCUMENT,CSV_IMPORT"` | ① 创建全新 KP 节点再删旧节点 ② 保留所有旧节点仅加 SAMES_AS 边 | ① 新节点无历史边，需全部重建，额外事务开销；② 保留旧节点使图膨胀且查询需额外跳转。选择就地升级：保留信息最全的 KP 为主，重定向边后删除冗余节点 | 重定向边期间有短暂窗口（单次 Cypher 事务内，毫秒级），规范 KP 选主逻辑（文档 KP 优先）可能丢失 CSV 侧独有信息（已通过 fusionSource 字段保留审计线索） |
| D5 | **MASTERS 边批量写入**：按 Student 分组，每人一条 UNWIND Cypher 创建其所有 MASTERS 边 | ① 逐条 CREATE ② 单条巨型 UNWIND 全量 | ① N+1 问题（100 学生 × 50 KP = 5000 次 DB round-trip）；② 巨型 UNWIND 参数 Map 过大，Neo4j 单次查询参数有上限。分组 UNWIND 折中：每 Student 一组 UNWIND（含其所有 KP），100 学生 = 100 次 UNWIND 调用 | 100 次 UNWIND 仍有一定开销，但单次 UNWIND 参数在几十条边级别，远低于 Neo4j 参数上限 |
| D6 | **融合日志结构**：`fusion_log` 表含 `fusion_detail_json`（MEDIUMTEXT）和 `masters_snapshot_json`（MEDIUMTEXT）两个 JSON 列。fusion_detail_json 结构：`[{groupId, sourceKpIds[], targetKpId, redirectedEdges: [{type, fromId, toId}]}]`。masters_snapshot_json 结构：`[{studentNo, kpName, oldWeight, newWeight}]` | ① 关系表（fusion_log + fusion_detail + fusion_masters_snapshot 三表）② 仅存统计数字不存明细 | ① 三表 JOIN 查询复杂，回滚时需组装，且融合明细与 MASTERS 快照总是整体读写；② 无法回滚。选择 JSON 列：融合明细 JSON 在回滚时直接反序列化为回滚指令，无需额外 JOIN | JSON 列无法在 MySQL 层按 KP ID 检索（"哪些融合涉及 KP-X？"），但 v1 无此查询需求。MEDIUMTEXT 上限 16MB，千级 KP 融合仍远低于此 |
| D7 | **回滚机制**：逆向恢复顺序：① 查 fusion_log 获取快照 JSON → ② 校验当前 Neo4j 状态与快照的一致性（规范 KP 存在、MASTERS 边 weight 匹配）→ ③ 删除规范 KP（DETACH DELETE）→ ④ 重建源 KP 节点（含原始属性）→ ⑤ 重建原始边 → ⑥ 回退 MASTERS 权重（oldWeight 为 null 则删边，否则 UPDATE）→ ⑦ 标记 `fusion_log.rolled_back = true` | ① 仅记录不自动回滚（人工 Cypher 恢复）② 全量备份/恢复（Neo4j dump） | ① 人工回滚易出错且不幂等；② 全量备份粒度太粗（回滚一次融合会影响其他不相关数据）。选择日志驱动逆向恢复：利用 fusion_detail_json 的精确映射逆向操作，回滚粒度 = 单次融合 | 若融合后 Neo4j 被外部直接修改（非融合路径），一致性校验会失败拒绝回滚。回滚本身是 Neo4j + MySQL 双写，Neo4j 写失败时 MySQL fusion_log 不回滚（最终一致） |
| D8 | **自动增量融合钩子位置**：在 `GradeServiceImpl.uploadGradeCsv()` 的 Neo4j 图构建完成后（第 154 行之后）调用 `fusionService.fuseIncremental(affectedKpNames)`；在 `GraphServiceImpl.extract()` 的抽取写入完成后（第 83 行之后）调用 `fusionService.fuseIncremental(affectedKpNames)` | ① Controller 层 AOP 拦截 ② MQ 异步事件 | ① AOP 对返回类型不同的方法需分别写切面，且难以获取 affectedKpNames 参数；② v1 不做 MQ 异步。选择 Service 层显式委托：调用点明确，affectedKpNames 从方法内部直接获取，增量融合失败不阻塞主流程（catch log + 不抛异常，用户可后续手动全量融合修复） | 增量融合失败时静默（仅 ERROR 日志），用户需查 fusion_log 确认。GradeServiceImpl 和 GraphServiceImpl 各自新增 1 行委托调用，耦合度轻微增加 |
| D9 | **融合并发控制**：`fusion_log` 表加 `status` 字段（RUNNING/COMPLETED/ROLLED_BACK），融合开始前检查是否存在 RUNNING 记录，存在则拒绝新融合请求（返回 A0013 融合进行中）。融合完成/失败后更新 status | ① Redis 分布式锁 ② 数据库行锁 SELECT FOR UPDATE | ① Redis 非项目核心组件（当前未使用），引入仅为一把锁过度；② MySQL 行锁需在事务内持有，与 Neo4j 写操作不在同一事务。选择应用层状态机：简单可靠，fusion_log 单表控制 | 若融合过程 JVM 崩溃，fusion_log 残留 RUNNING 状态需人工清理（或加超时自动转 FAILED 的定时任务，v2） |
| D10 | **KnowledgePoint 区分来源**：为 CSV 导入的 KP 增加标记。修改 `GradeServiceImpl` 中 KnowledgePointNode 构造，传入 `fusionSource = "CSV_IMPORT"`。文档抽取的 KP 已有 `documentId` 非空作为区分。融合后规范 KP 的 `fusionSource` 拼接来源（如 `"DOCUMENT,CSV_IMPORT"`） | ① 新增独立 Label `:CsvKnowledgePoint` ② 不加标记靠 `documentId IS NULL` 推断 | ① Label 不同则 MERGE 无法跨来源匹配（MERGE 依赖相同 Label）；② 推断不可靠（未来可能有其他无 documentId 的 KP 来源）。选择 `fusionSource` 字符串字段，可扩展拼接多个来源 | KnowledgePointNode 新增字段需更新 `toProperties()` 和构造器，GradeServiceImpl 需改 1 行 |

---

## 2. 数据流 / 架构图

### 2.1 全量融合流程

```
POST /api/v1/graph/fusion/execute
        │
        ▼
FusionController (L1)
  └─ FusionService.fuseFull()  (L2)
       │
       ├─ 1. 并发检查（fusion_log WHERE status=RUNNING → 拒绝）
       │
       ├─ 2. 查询所有 KP 节点
       │     GraphNodeRepository.findAllKnowledgePoints()
       │     ─── Cypher: MATCH (kp:KnowledgePoint) RETURN kp
       │
       ├─ 3. 匹配分组
       │     KpMatchingStrategy.match(kpA, kpB) → 0~1
       │     ─── 按 subject 分桶 → 桶内两两匹配 → 相似度≥阈值归组
       │
       ├─ 4. 融合执行（每组一个 Neo4j 事务）
       │     for each FusionGroup:
       │       ├─ 选主 KP（documentId 非空优先）
       │       ├─ 边重定向（Cypher: MATCH ... CREATE/DELETE edges）
       │       └─ DETACH DELETE 冗余 KP
       │
       ├─ 5. MASTERS 全量重算
       │     for each Student:
       │       ├─ 查 MySQL exam_record 中所有得分记录
       │       ├─ WeightCalculationStrategy.calculate(records) → weight
       │       └─ UNWIND 批量 CREATE/MERGE MASTERS 边
       │
       ├─ 6. 写 fusion_log（MySQL 事务）
       │     INSERT fusion_log (fusion_detail_json, masters_snapshot_json, status=COMPLETED)
       │
       └─ 7. 返回 FusionExecuteVO
              { fusionLogId, mergedKpGroupCount, mastersEdgeCount }
```

### 2.2 增量融合流程（上传后自动触发）

```
GradeServiceImpl.uploadGradeCsv() / GraphServiceImpl.extract()
        │  （主流程完成后）
        ├─ ...
        └─ fusionService.fuseIncremental(affectedKpNames)  // 新增 1 行
             │
             ├─ 1. 仅查询 affectedKpNames 匹配的 KP 节点
             │     MATCH (kp:KnowledgePoint) WHERE kp.name IN $names AND kp.subject = $subject
             │
             ├─ 2. 匹配分组（仅 affected 范围内的 KP）
             │
             ├─ 3. 融合执行（仅 affected 融合组）
             │
             ├─ 4. MASTERS 增量重算（仅 affected 学生）
             │     MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp)
             │     WHERE kp.name IN $names
             │
             ├─ 5. 写 fusion_log（trigger_type=AUTO_INCREMENTAL, status=COMPLETED）
             │
             └─ catch Exception → log.error("增量融合失败，可手动全量融合修复", e)
                  // 不抛异常，不阻塞主流程
```

### 2.3 回滚流程

```
POST /api/v1/graph/fusion/rollback/{fusionLogId}
        │
        ▼
FusionController (L1)
  └─ FusionService.rollback(fusionLogId)  (L2)
       │
       ├─ 1. 查 fusion_log（不存在 → A0012）
       │
       ├─ 2. 一致性校验
       │     校验规范 KP 存在 + MASTERS weight 与 newWeight 匹配
       │     （不匹配 → A0014 "图状态已变更，无法回滚"）
       │
       ├─ 3. 逆向恢复
       │     ├─ DETACH DELETE 规范 KP
       │     ├─ CREATE 源 KP（含原始属性，从 fusion_detail_json 还原）
       │     ├─ CREATE 原始边（从 redirectedEdges 逆向重建）
       │     └─ UPDATE/DELETE MASTERS 边（回退到 oldWeight）
       │
       ├─ 4. 标记 fusion_log.rolled_back = true
       │
       └─ 5. 返回 FusionRollbackVO { fusionLogId, restoredKpCount, restoredEdgeCount }
```

### 2.4 模块依赖图

```
┌─────────────────────────────────────────────────┐
│ L1 api/graph/controller/FusionController        │
│    POST /execute   GET /status  POST /rollback  │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│ L2 application/graph/fusion/                    │
│  ┌─────────────────────────────────────────┐    │
│  │ FusionService (接口)                     │    │
│  │  + fuseFull()                            │    │
│  │  + fuseIncremental(kpNames)              │    │
│  │  + rollback(fusionLogId)                 │    │
│  │  + getStatus()                           │    │
│  └─────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────┐    │
│  │ strategy/                                │    │
│  │  KpMatchingStrategy ◄── FuzzyMatch       │    │
│  │  WeightCalcStrategy  ◄── TimeDecay       │    │
│  └─────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────┐    │
│  │ FusionProperties (yml → 配置绑定)         │    │
│  └─────────────────────────────────────────┘    │
└──────┬──────────────────────┬───────────────────┘
       │                      │
┌──────▼──────────┐  ┌───────▼────────────────────┐
│ L3 Neo4j        │  │ L3 MySQL                    │
│ GraphNodeRepo   │  │ fusion_log                  │
│  + mergeKp()    │  │  + FusionLogDO              │
│  + redirectEdges│  │  + FusionLogRepository      │
│  + batchMasters │  │ exam_record (只读)           │
│  + findKps()    │  │  + ExamRecordRepository     │
└─────────────────┘  └────────────────────────────┘
```

---

## 3. 关键状态机

### 3.1 fusion_log 状态

```
                    ┌──────────┐
        融合开始 →   │ RUNNING  │
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              ▼                     ▼
        ┌──────────┐         ┌──────────┐
        │COMPLETED │         │ (JVM崩)  │ → 需人工清理或超时转 FAILED(v2)
        └────┬─────┘         └──────────┘
             │
      回滚触发│
             ▼
        ┌───────────┐
        │ROLLED_BACK│
        └───────────┘
```

### 3.2 KP 融合前后

```
融合前:
  KP-A (documentId="1")  ←ALIGNED_TO── E1
                         ←BELONGS_TO── CAT-函数
  
  KP-B (documentId=null) ←TESTED── EXAM-01

融合后:
  KP-A' (fusionSource="DOCUMENT,CSV_IMPORT")
        ←ALIGNED_TO── E1
        ←BELONGS_TO── CAT-函数
        ←TESTED── EXAM-01

  KP-B: DETACH DELETED
```

---

## 4. ADR 索引

凡 D1~D10 中可逆性低的，单独写 ADR：

- `@.specs/adr/006-kp-matching-strategy.md` — KpMatchingStrategy 接口 + 模糊匹配多度量组合算法（D1）
- `@.specs/adr/007-weight-calculation-strategy.md` — WeightCalculationStrategy 接口 + 时间衰减加权公式（D2）
- `@.specs/adr/008-fusion-rollback.md` — 融合日志驱动回滚机制（D6 + D7 + D9）
- `@.specs/adr/009-auto-incremental-fusion-hook.md` — 自动增量融合的触发位置与容错策略（D8）

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **模糊匹配误合**：多度量组合阈值 0.85 仍可能误合不同 KP（如"一次函数定义" vs "二次函数定义" Levenshtein ≈ 0.83，靠近阈值） | 不同知识点被错误合并，图数据失真 | 中 | ① 阈值通过 yml 可配置，运维可调参；② `KpMatchingStrategy` 接口预留替换路径，v2 加入 LLM 语义匹配；③ 回滚机制可在发现误合后恢复 |
| R2 | **模糊匹配漏合**：长度差异大的同义 KP（"二次函数顶点坐标" vs "顶点坐标公式"，BigramJaccard ≈ 0.33）可能不触发合并 | 同名 KP 仍为孤岛，掌握度计算碎片化 | 高 | ① α/β/γ 权重调整可提升 Bigram 权重；② v1 接受部分漏合，通过手动全量融合 + 查阅 fusion_log 发现；③ v2 LLM 语义匹配根本解决 |
| R3 | **大图重算性能**：全量融合遍历所有 Student × KP 对，1000 学生 × 500 KP = 50 万对，MySQL 查询 + Neo4j 写入耗时超 5 秒 AC 约束 | 手动融合 HTTP 超时，用户体验差 | 中 | ① v1 数据规模小（预计 < 200 学生 × 100 KP）；② 分组 UNWIND 批量写入减少 DB round-trip；③ v2 切 MQ 异步 + 分页批量写 |
| R4 | **融合中 JVM 崩溃**：Neo4j 已部分修改（边已重定向但旧 KP 未删除），fusion_log 残留 RUNNING 状态 | 图数据不一致，后续融合被拒绝（并发控制） | 低 | ① 融合入口检查 RUNNING 状态并拒绝；② 运维手动清理 RUNNING 状态后重新全量融合（全量融合天然修复不一致）；③ v2 加超时自动转 FAILED + 定时补偿 |
| R5 | **回滚时图状态已变更**：融合后 Neo4j 被手动 Cypher 修改，回滚一致性校验失败 | 回滚被拒绝（A0014），需人工介入 | 低 | 拒绝回滚是安全策略（宁可拒绝不可破坏数据）。A0014 错误信息含具体不一致字段，运维可手动修复后再回滚 |
| R6 | **增量融合静默失败**：自动增量融合 catch 异常不抛（D8），用户不知道融合未生效 | MASTERS 边过期，查询结果不准 | 中 | ① ERROR 日志含完整堆栈 + affectedKpNames；② `GET /api/v1/graph/fusion/status` 可查最近融合状态；③ 用户可随时手动全量融合修复 |

---

## 6. 不在范围

- ❌ 向量相似度匹配的 `KpMatchingStrategy` 实现（v2）
- ❌ LLM 语义匹配的 `KpMatchingStrategy` 实现（v2）
- ❌ EWMA/贝叶斯 `WeightCalculationStrategy` 实现（v2）
- ❌ 融合历史趋势查询 API（v2）
- ❌ MQ 异步融合（v2）
- ❌ 超时自动转 FAILED 的定时任务（v2）
- ❌ 跨学科 KP 融合
- ❌ 融合冲突人工审核 UI
- ❌ EventNode 引入
- ❌ 跨源查询 API（`GET /fusion/kp/*`、`GET /fusion/student/*`、`GET /fusion/diagnose/*`）

---

## 9. 架构沉淀建议（本 change 完成后供 `A-evolve` 同步用）

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/graph/fusion/strategy/KpMatchingStrategy.java` | KP 匹配策略接口（`double match(KpCandidate, KpCandidate)`） | 任何需要判断两个知识点是否等价的场景 | 后续接入向量匹配/LLM 匹配只需实现此接口 |
| `application/graph/fusion/strategy/WeightCalculationStrategy.java` | 权重计算策略接口（`WeightResult calculate(List<TestedRecord>)`） | 任何需要从多条记录聚合单一权重的场景 | 后续接入 EWMA/贝叶斯只需实现此接口 |
| `application/graph/fusion/config/FusionProperties.java` | 融合配置绑定（`@ConfigurationProperties("fusion")`） | 所有融合相关可配置参数集中管理 | 按 Spring Boot 惯例放 config 包 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 策略模式用于算法可替换性 | `KpMatchingStrategy` + `WeightCalculationStrategy` 接口 + yml 切换实现 | 所有需要算法可插拔的模块 | 低 — 仅需修改 yml 配置即可替换实现类 |
| 操作日志驱动回滚 | MySQL `fusion_log` 存快照 JSON → 逆向恢复 Neo4j | 所有需要可回滚的写操作 | 中 — 回滚逻辑与快照 JSON 结构强耦合，改 JSON schema 需兼容旧日志 |
| 同步 Service 委托作为事件钩子 | 在既有 Service 方法末尾显式调用 FusionService | 所有需要"在 X 操作后自动触发 Y"的场景 | 低 — 仅一行委托调用，移除即可解耦 |

### 9.3 新增 / 修改的跨模块契约

```
- 新增 POST /api/v1/graph/fusion/execute — 手动全量融合，请求体空，响应 FusionExecuteVO
- 新增 GET /api/v1/graph/fusion/status — 查询最近融合状态，响应 FusionStatusVO
- 新增 POST /api/v1/graph/fusion/rollback/{fusionLogId} — 回滚指定融合，响应 FusionRollbackVO
- 新增 EdgeType.MASTERS — Neo4j MASTERS 边类型
- 新增 ErrorCode A0012(融合日志不存在) / A0013(融合进行中) / A0014(回滚校验失败)
- 新增 MySQL 表 fusion_log（id, trigger_type, status, merged_kp_group_count, masters_edge_count, fusion_detail_json, masters_snapshot_json, rolled_back, executed_at, create_time）
- KnowledgePointNode 新增字段 fusionSource（VARCHAR，标记融合来源）
```

### 9.4 新增 / 升级的依赖

本 change 无新增 pom.xml 依赖。

### 9.5 禁动清单变化

```
- 新增禁动：application/graph/fusion/strategy/ 接口签名（KpMatchingStrategy.match / WeightCalculationStrategy.calculate）变更需走独立 CHANGE，因为会影响所有实现类
- 新增禁动：fusion_log 表 fusion_detail_json / masters_snapshot_json 的 JSON schema 变更需兼容旧日志（回滚依赖旧 schema）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。