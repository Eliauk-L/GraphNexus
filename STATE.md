# STATE — GraphNexus 跨会话状态

> 每次阶段切换或清窗时更新，供下一个会话快速恢复上下文。

---

## 当前活动

- **Change ID**: `user-auth-rbac`
- **当前阶段**: CHANGE ✅ → REQUIREMENT ✅ → DESIGN ✅ → 等待用户确认（下一步 UI-DESIGN）
- **当前角色**: Architect
- **最后更新**: 2026-06-22
- **工件**: `@.specs/user-auth-rbac/CHANGE.md` + `REQUIREMENT.md` + `DESIGN.md` + `@.specs/adr/ADR-037-040-*.md` + `@.specs/CONTEXT.md`（已更新）
- **关键设计决策 (D1–D12)**:
  - D1: JWT → jjwt 0.12.x / HS256 / Access Token 30min + Refresh Token UUID 7d
  - D2: Access Token payload: `{sub, userId, roles, iat, exp}`
  - D3: SecurityFilterChain → 单一链 + JwtAuthenticationFilter 插在 UsernamePasswordAuthenticationFilter 前
  - D4: 密码 → BCryptPasswordEncoder(10)
  - D5: 角色模型 → role 表预置 5 行 / 不允许运行时增删角色
  - D6: ADMIN 继承 TEACHER → UserPrincipal 构建时自动追加 ROLE_TEACHER
  - D7: Refresh Token → UUID + Redis 存储 + 滚动刷新
  - D8: Redis 缓存 → StringRedisTemplate 手动管理 / key `user:auth:<userId>` / TTL 30min / 降级查 MySQL
  - D9: Redis 不可用 → WARN 日志 + 降级直查 MySQL（Refresh 强依赖 Redis 无法降级）
  - D10: 权限注解 → Controller 类级 @PreAuthorize + 方法级覆盖
  - D11: 测试 → JwtTestHelper + TestSecurityConfig 覆盖类
  - D12: 前端 Token 存储 → localStorage
- **ADR**: ADR-037（JWT 双 Token 滚动刷新）+ ADR-038（RBAC 角色权限模型）+ ADR-039（Redis 用户缓存与降级）+ ADR-040（全端点权限收敛方案）

---

## 上一个活动

- **Change ID**: `diagnosis-subgraph-viz`
- **当前阶段**: CHANGE ✅ → REQUIREMENT ✅ → DESIGN → 等待用户确认
- **当前角色**: Architect
- **最后更新**: 2026-06-22
- **用户决策**: Q1 子图布局→A 上下分区 | Q2 度量展示→A 仅 MASTERS 权重映射 | Q3 多次考试→C 详情面板+趋势折线图
- **工件**: `@.specs/diagnosis-subgraph-viz/CHANGE.md` + `REQUIREMENT.md` + `DESIGN.md` + `@.specs/adr/034-diagnosis-subgraph-svg.md` + `@.specs/adr/035-masters-exam-history-data-flow.md` + `@.specs/adr/036-diagnosis-component-isolation.md`
- **关键设计决策 (D1–D8)**:
  - D1 渲染方案: **纯 SVG**（非 G6 v5），轻量力导向布局，≤30 节点场景更合适
  - D2 布局算法: 简单力导向 + 层级约束，Student 固定居中上方
  - D3 颜色映射: **四档离散色阶**（红/橙/黄/绿），非连续 HSL 插值
  - D4 大小映射: 连续线性 `radius = 12 + weight * 28` → [12, 40]px
  - D5 趋势图: 纯 SVG 折线图，不引入图表库
  - D6 数据加载: **异步非阻塞**，LLM 报告先渲染，子图随后加载
  - D7 MASTERS 数据透传: 考试历史 JSON 走 KP 节点 `properties.examHistory`（非边 description），零 API 契约变更
  - D8 画布尺寸: 宽 100% + 高 400px 固定，viewBox="0 0 600 400"
- **ADR**: ADR-034（纯 SVG 渲染方案）+ ADR-035（examHistory 节点属性传递）+ ADR-036（组件隔离策略）
- **下一步**: 用户确认 DESIGN.md 后进入 `@flow-kit/prompts/2a-ui-design.md`（前端项目必须走 UI-DESIGN）
- **关键设计决策 (D1–D9)**:
  - D1 学科全景图 API 端点: `GET /api/v1/graph/construction/subject/{subjectName}`
  - D2 学科列表 API: `GET /api/v1/graph/subjects`
  - D3 指标学科过滤: **结果层后置过滤**（全图 GDS 计算 → 按 subject KP 列表筛选），不改 GdsAdapter
  - D4 节点大小映射: **线性映射** totalDegree → [20, 60]px，95 百分位截断防离群值
  - D5 节点颜色映射: **5 档暖色梯度**（PageRank 百分位分段），开关 OFF 时恢复默认 NODE_COLORS
  - D6 视图模式切换: Pinia store `viewMode : 'document' | 'subject'`，互斥
  - D7 PageRank 开关: **localStorage 持久化**，默认 OFF，降级内存状态
  - D8 学科图 Cypher: **单查询** OPTIONAL MATCH + DISTINCT，LIMIT 1000
  - D9 MetricsPanel 位置: **右侧滑出**，与 NodeDetailPanel 互斥
- **ADR**: ADR-032（学科全景图 API 设计）+ ADR-033（指标学科过滤策略：结果层后置过滤）

---

## 上一个活动

- **Change ID**: `transaction-management-refactor`
- **当前阶段**: DEV ✅ → 下一步 TEST/REVIEW
- **DEV 执行记录**: 11 任务 · 4 波次 · 212 单测全通过
- **工件**: `@.specs/transaction-management-refactor/{CHANGE,REQUIREMENT,DESIGN,TASK}.md` + `@.specs/adr/{028,029}-*.md`
- **最后更新**: 2026-06-22
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）— 纯后端重构，无前端变更，跳过 `UI-DESIGN.md`
- **关键决策（DESIGN 级 · D1–D10）**：
  - D1 parse() 拆 3 段：短事务(→PARSING)→无事务(MinIO+解析)→短事务(→PARSED)
  - D2 extract() 拆 4 段：短事务(→EXTRACTING)→无事务(LLM)+Neo4j独立Tx→短事务(→EXTRACTED)→事务外事件
  - D3 事件移到 @Transactional 外（调用方负责顺序：先 tx commit 再 publish）
  - D4 @EventListener 不标注 @Transactional，事务委托 Service public 方法
  - D5 GradeUploadService 拆分：解析(无tx)→短tx(去重+saveAll)→事务外事件
  - D6 GradeServiceImpl.deleteByExamNo 拆分：短tx(查询+deleteAll)→事务外事件
  - D7 FusionServiceImpl.updateLogFailed status="FAILED" 笔误修复
  - D8 QueryServiceImpl 3 个 private @Transactional v1 不改（标注技术债）
  - D9 跨存储补偿：Neo4j 失败→新短事务回退 MySQL 状态+failReason
  - D10 Neo4j 写操作用 TransactionTemplate(neo4jTransactionManager) 包裹（对齐 FusionServiceImpl）
  - **设计原则**：接受最终一致而非强一致；补偿回退上一稳定态而非全部回滚
- **ADR**: `ADR-028`（事务边界策略 6 条规则）+ `ADR-029`（跨存储失败补偿策略）
- **上游工件**: `@.specs/transaction-management-refactor/CHANGE.md` + `REQUIREMENT.md` + `DESIGN.md` + `@.specs/adr/028-*.md` + `@.specs/adr/029-*.md`

> ⏸️ `exception-traceability`：CHANGE ✅ → REQUIREMENT ✅ → DESIGN ✅ → TASK ✅ → DEV ✅。后端全局异常处理器补全分级结构化日志 + traceId 关联，复用既有 logback/MDC，响应体不变。TASK 拆 2 个串行任务：**T01 ✅ 兜底 ERROR 完整堆栈 + AC-1 测试（提交 `e1abcc9`）**；**T02 ✅ 业务/校验/权限 WARN 分级 + 响应体回归 + AC-4 零基础设施静态校验（提交 `493eab2`）**。change 全部任务完成，下一步 `@flow-kit/prompts/5-test.md`。工件：`.specs/exception-traceability/TASK.md` + `T01-SUMMARY.md` + `T02-SUMMARY.md` + `{CHANGE,REQUIREMENT,DESIGN}.md` + `CONTEXT.md`。
> ⏸️ `graph-construction-refactor` 暂停于 DEV ✅ / 下一步 TEST·REVIEW，待本 change 收尾后恢复。其 DEV 执行记录见下方。

## DEV 执行记录 (fusion-to-analysis-event-driven)

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | GraphConstructedEvent 新建 | (待提交) | ✅ |
| T02 | frontend fusion.ts 新建 | (待提交) | ✅ |
| T03 | fusion 全栈搬迁 + 删除 GradeUploadedEventListener | (待提交) | ✅ |
| T04 | GraphConstructedEventListener 新建 | (待提交) | ✅ |
| T05 | ConstructionServiceImpl 重构 | (待提交) | ✅ |
| T06 | GradeGraphEventListener 重构 | (待提交) | ✅ |
| T07 | 前端 fusionStore import + graph.ts 清理 | (待提交) | ✅ |
| T08 | 测试搬迁 + import 更新 + 全量 mvn test | (待提交) | ✅ |

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | SQL DDL — DROP csv columns + index | `905542e` | ✅ |
| T02 | pom.xml — Apache POI 5.2.5 | `905542e` | ✅ |
| T03 | ErrorCode A0022 exam_no duplicate | `905542e` | ✅ |
| T04 | GradeFileType CSV + EXCEL split | `ea04746` | ✅ |
| T05 | CsvParsePayload → GradeParsePayload | `ea04746` | ✅ |
| T06 | ExamRecordDO csv field removal | `ea04746` | ✅ |
| T07 | Grade BO/VO csv field cleanup | `ea04746` | ✅ |
| T08 | CsvGradeParser adapt new enums | `0a08d13` | ✅ |
| T09 | ExcelGradeParser new | `0a08d13` | ✅ |
| T10 | GradeUploadedEvent + GradeDeletedEvent | `0a08d13` | ✅ |
| T11 | ExamRecordRepository refactor | `0a08d13` | ✅ |
| T12 | GradeUploadService event-driven | `0a08d13` | ✅ |
| T13 | GradeServiceImpl spec query + event | `0a08d13` | ✅ |
| T14 | GradeGraphEventListener new | `0a08d13` | ✅ |
| T15 | GradeUploadedEventListener @Order(2) | `0a08d13` | ✅ |
| T16 | GradeController conditional query | `0a08d13` | ✅ |
| T17 | CsvGradeParserTest adapt | `0a08d13` | ✅ |
| T18 | ExcelGradeParserTest new | `9b9ba7b` | ✅ |
- **最后更新**: 2026-06-19

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | TextbookRepository 5 条 @Query → 方法名派生 | `98fd00d` | ✅ |
| T02 | ExamRecordRepository 5 替换 + 1 改名 | `98fd00d` | ✅ |
| T03 | TextbookRepository 调用方更新（3 Service + 3 Test） | `396b61a` | ✅ |
| T04 | ExamRecordRepository 调用方更新（3 Service + 1 Strategy） | `396b61a` | ✅ |
| T05 | 全量测试验证 + AC 核验 | `396b61a` | ✅ |

## 上一个活动

- **Change ID**: `intelligent-qa`
- **当前阶段**: TASK → 等待 DEV
- **最后更新**: 2026-06-17

## 并行 Change

| Change ID | 阶段 | 状态 |
|-----------|------|------|
| `init-platform` | DEV → TEST | ✅ 9/9 任务完成 |
| `document-process-pdf-minimal` | DEV | ✅ 10/10 任务完成 |
| `knowledge-graph-extraction` | DEV | ✅ 11/11 任务完成，集成测试全通过 |
| `csv-grade-import` | DEV ✅ → TEST | 🔄 进行中（17/17 单测 + 6/6 集成通过） |
| `intelligent-qa` | TASK | 🔄 等待确认，17 任务 6 波次，下一步 DEV |
| `fix-extraction-json-parsing` | DEV | ✅ T01 完成 |
| `package-restructure` | DEV ✅ | 🔄 15+5 任务完成，T01-T20 全部 done，下一步 TEST/REVIEW |
| `refine-package-structure` | DONE | ✅ 7/7 任务完成（本次将 supersede） |
| `frontend-ui` | DEV | ✅ 18/18 任务完成，5 波次全部通过，vue-tsc 0 错误 |

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | ExtractionJsonParser + Jackson 宽松解析 | — | ✅ |
| T01 | ErrorCode 新增 A0008/A0009/A0010 | `51626a0` | ✅ |
| T02 | GraphNode/GraphEdge 抽象 + NodeType/EdgeType 枚举 | `63840a8` | ✅ |
| T03 | LlmGateway + SpringAiLlmGateway + yml 配置 | `fd15560` | ✅ |
| T04 | DocumentNode/EntityNode/KnowledgePointNode/KnowledgeCategoryNode | `e599eb1` | ✅ |
| T05 | 6 类图边（Extracts/References/Derives/Contains/AlignedTo/BelongsTo/ChildOf/Prerequisite） | `6d03eab` | ✅ |
| T06 | GraphNodeRepository 通用图仓库 | `c7aba8e` | ✅ |
| T07 | ExtractionService Prompt+JSON Schema+校验 | `c90c4f4` | ✅ |
| T08 | GraphService 接口+实现 | `38c7e61` | ✅ |
| T09 | GraphController + VO/DTO | `976f1ac` | ✅ |
| T10 | 单元测试（16 tests, 0 failures） | `989a40d` | ✅ |
| T11 | 集成测试（4/4 通过，直连 podman + DeepSeek API） | `a0b1ef5` | ✅ |

## 中断任务

暂无。

## 待完成事项

- 进入 `@flow-kit/prompts/5-test.md` 测试矩阵 + UAT
- 进入 `@flow-kit/prompts/6-review.md` 双轮审查

---

> 完整流程见 `.specs/knowledge-graph-extraction/` 下各工件。