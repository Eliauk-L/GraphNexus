# CONTEXT — GraphNexus 项目共享上下文

> 跨 change 共享。任何术语、决策、偏好一旦写入，后续会话可直接 @引用，避免复述。
>
> 每次 REQUIREMENT 阶段追加或更新。

---

## 术语表

| 术语 | 定义 |
|------|------|
| **宽图谱** | 融合多种数据源（文档图谱 + 事件图谱 + 结构化数据）的 Neo4j 图数据库，以学生/知识点为主数据节点 |
| **图剪枝** | 从宽图谱中按任务类型裁切出最小够用子图，减少 LLM 上下文噪音 |
| **任务驱动剪枝** | 根据用户查询意图（如"分析学生A数学薄弱点"）动态选择子图遍历策略 |
| **图钉节点** | 宽图谱中的主数据锚点节点，如 `Student`、`KnowledgePoint`，是图谱融合的骨架 |
| **事件节点** | 将 CSV 行记录（如一次考试）转换为图节点，通过外键关联到图钉节点 |
| **文档图谱** | 从 PDF 教辅中抽取概念、公式、定义及其关系形成的知识子图 |
| **LLMGateway** | 自研 LLM 调用网关，负责模型路由/配额/降级/调用审计 |
| **MinerU** | PDF 版面分析工具（v4 精准解析 API，`vlm` 模型），主用方案；PDFBox 为兜底 |
| **四层架构** | L1(api) → L2(application) → L3(infrastructure)，common 被所有层依赖 |
| **错误码** | 5位字符串：来源(A=用户端/B=系统/C=第三方)+4位数字，如 `A0001`、`B0120` |
| **MinerUDocumentParser** | `DocumentParser` 接口的 MinerU v4 精准解析 API 实现，通过 `file-urls/batch` 签名上传→自动解析→轮询→下载 zip 提取 `full.md` 完成 PDF 解析，`vlm` 模型版本，公式以 LaTeX 保留。支持 ≤200MB/≤200 页。所有异常 fallback 到 `PdfBoxDocumentParser` |
| **文档状态机** | 文档生命周期状态：`UPLOADED → PROCESSING → COMPLETED`（成功路径）或 `UPLOADED → PROCESSING → FAILED`（异常路径），状态不可跳转或逆向。v1 使用 5 个状态，`DELETING`/`DELETED` 预留 |
| **Document** | MySQL 中的文档元数据实体（DO），存储 `document_no`（MD5 内容指纹）、`name`、`subject`（学科）、`file_size`、`minio_path`、`text_content`、`page_count`、`metadata_json`、`status`、`fail_reason`、`uploaded_by` 等字段，遵循逻辑删除 |
| **EntityNode** | Neo4j 节点，表示 LLM 从文档原文中抽取的片段实体，含 `entityType`（DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION）、`originalText`、`pageNumber`，生命周期绑定源文档 |
| **KnowledgePointNode** | Neo4j 节点，表示跨文档的标准化知识点（如"二次函数顶点坐标"），去重后多个文档的实体可对齐到同一 KP，是图钉节点的核心类型之一 |
| **KnowledgeCategoryNode** | Neo4j 节点，表示知识点的层次分类（如 初中数学→代数→函数→二次函数），通过 CHILD_OF 边形成树 |
| **ALIGNED_TO 边** | EntityNode → KnowledgePointNode 的对齐关系，连接文档原文片段与标准知识点，是多文档融合的关键桥梁 |
| **PREREQUISITE_OF 边** | KnowledgePoint → KnowledgePoint 的前置依赖关系（如"对称轴"是"顶点坐标"的前置知识），含 `strength`(0-1) 和 `description` 属性 |
| **GraphNode / GraphEdge 抽象** | 图节点和边的抽象基类/接口，定义通用契约（label/type/properties），通过类型注册机制支持扩展，新增子类无需修改核心持久化链路 |
| **抽取 JSON Schema** | LLM 抽取结果的结构定义，约束 entities/knowledgePoints/categories/relationships 各字段类型和枚举值，用于入库前校验 |
| **exam_record** | MySQL 独立表，存储成绩文件的原始记录。每行 = 一个学生的一次考试，`score_details` 为 JSON 列存各题成绩与知识点明细（`{questionLabel, kpNames, rawScore, maxScore}`），通过 `exam_no` 判重。v2（`grade-management-refactor`）已删除 `csv_file_path` 和 `csv_md5` 列（不再存储 MinIO 路径和文件 MD5） |
| **StudentNode** | Neo4j 节点（Label `:Student`），表示学生。唯一标识 `studentNo`（学号），属性含 `name`、`className`、`grade`。通过 `ATTENDED` 边连接 ExamNode，通过 `MASTERS` 边（未来）连接 KnowledgePointNode |
| **ExamNode** | Neo4j 节点（Label `:Exam`），表示一次考试。唯一标识 `examNo`（来源于 CSV `考试编号` 列，格式如 `E20200041`），属性含 `name`、`examDate`、`subject`。同一 CSV 文件生成一个 Exam 节点 |
| **ATTENDED 边** | Neo4j 关系边 `(:Student)-[:ATTENDED]->(:Exam)`，表示学生参加了某次考试。纯结构边，无属性 |
| **TESTED 边** | Neo4j 关系边 `(:Exam)-[:TESTED]->(:KnowledgePoint)`，表示考试中考查了某知识点。纯结构边，无属性。**所有分数在 MySQL `exam_record` 中** |
| **MASTERS 边** | Neo4j 关系边 `(:Student)-[:MASTERS]->(:KnowledgePoint)`，聚合边。`weight` 为该学生对知识点的掌握度（0~1），由所有 TESTED 路径的得分率经时间衰减加权平均计算得出；`description` 存储 JSON 摘要（考试次数、最近考试日期、各次得分率）。**衍生边**，不写入新事实，每次融合全量重算覆盖 |
| **KP 融合** | 将多源（文档抽取 + CSV 导入）中同名或名称相似的 KnowledgePoint 节点合并为一个规范节点，合并后旧节点删除，所有入边/出边重定向到规范节点 |
| **融合组** | 经 `KpMatchingStrategy` 匹配后，相似度 ≥ 阈值的多个 KnowledgePoint 构成一个融合组，全组合并为一个规范节点 |
| **KpMatchingStrategy** | KP 匹配策略接口（契约：输入两个 KP 候选 → 输出 0~1 相似度），v1 首发实现为 `FuzzyMatchStrategy`（名称归一化 + 编辑距离/分词 Jaccard + subject 约束）。接口预留向量相似度、LLM 语义匹配等扩展点 |
| **WeightCalculationStrategy** | MASTERS 权重计算策略接口（契约：输入 `List<TestedRecord>` → 输出 `weight` + 摘要），v1 首发实现为 `TimeDecayStrategy`（月衰减因子 0.9，缺考不计入）。接口预留 EWMA、贝叶斯推断等扩展点 |
| **fusion_log** | MySQL 表，记录每次融合操作的完整审计日志：触发方式、融合 KP 组数、MASTERS 边数、融合明细 JSON（源 KP → 目标 KP 映射 + 边重定向清单）、MASTERS 变更快照 JSON（oldWeight → newWeight）。支持按 `fusionLogId` 回滚 |
| **智能问答** | 用户用自然语言提问，系统从宽图谱中剪枝出最小够用子图，输入 LLM 生成 Markdown 格式的分析结论与建议 |
| **意图识别** | 根据用户自然语言问题判定查询意图类型（v1：规则匹配 + 关键词，仅支持 STUDENT_DIAGNOSIS），路由到对应剪枝策略 |
| **STUDENT_DIAGNOSIS** | 学生薄弱点诊断意图：从 Student 出发，沿 MASTERS(weight < 0.6) → KnowledgePoint → PREREQUISITE_OF(≤2 跳) 剪枝，LLM 生成含薄弱点列表 + 根因分析 + 学习建议的 Markdown 报告 |
| **SubgraphPruningStrategy** | 图剪枝策略接口（契约：输入 `PruningRequest`（意图 + 实体 + 参数）→ 输出 `PrunedSubgraph`（节点 + 边 + 元数据）），v1 首发 `StudentDiagnosisStrategy`。新增意图只需实现接口 + 注册，不改核心链路 |
| **PrunedSubgraph** | 剪枝后的子图 BO，含节点列表（`List<GraphNode>`）+ 边列表（`List<GraphEdge>`）+ `PruningMeta`（策略名/跳数/阈值等元信息），用于序列化为 LLM Prompt 或通过 API 返回前端渲染 |
| **Token 预算控制** | 子图序列化时按优先级排序节点/边，超出 `query.token-budget.max-input-tokens`（默认 8000）时截断并在 prompt 中标注省略。优先级：Student > 弱掌握 KP（weight 升序）> PREREQUISITE_OF 目标 > 其他 |
| **query_task** | MySQL 表，记录每次问答任务的完整生命周期：`task_id`(UUID)、`question`、`student_name`、`subject`、`status`(PENDING/PROCESSING/COMPLETED/FAILED)、`answer`(Markdown)、`subgraph_json`(剪枝子图)、`token_usage_json`、`error_message`。日志类表，不设逻辑删除 |
| **Prompt 模板文件** | classpath 下的 `.md` 文件（如 `prompts/student-diagnosis-system.md`），按意图类型组装 system prompt（角色设定 + 分析框架 + Markdown 输出格式约束），通过配置切换支持不同风格模板 |
| **同步/异步双模式** | 简单问题（≤30s）走 `POST /api/v1/query/ask` 同步返回；复杂问题走 `POST /api/v1/query/ask-async` + `GET /api/v1/query/result/{taskId}` 轮询。超时自动降级 |
| **Markdown 输出约束** | LLM 必须以 Markdown 格式直接输出分析报告正文，禁止前导语（如"根据提供的子图数据……"），至少含 1 个标题 + 1 个列表。格式不合规时自动重试 ≤2 次 |
| **LLM 结论证据引用** | LLM 分析中引用的知识点名称、前置依赖关系、掌握度数值必须可在子图数据中溯源，禁止编造不存在的节点或关系。v1 通过 system prompt 约束实现，不做事后校验 |
| **子图序列化格式** | 子图 → LLM Prompt 的中间表示：自定义结构化文本（节点列表（id/type/properties）+ 边列表（src→dst/type/weight）+ 统计摘要），非 Graphviz/Mermaid 格式。LLM 通过 prompt 示例理解此格式 |
| **GDS（Graph Data Science）** | Neo4j 官方图算法插件库，提供 PageRank、度中心性、社区发现等 50+ 图算法。通过 Cypher 调用 `gds.*.stream` 过程，结果以记录流返回。本项目使用 GDS 5.x + Neo4j 5.26-community |
| **图投影（Graph Projection）** | GDS 将 Neo4j 原图映射为内存计算图的过程。支持按节点标签和边类型过滤（nodeProjection / relationshipProjection），可指定边方向（NATURAL/REVERSE/UNDIRECTED）。v1 使用 transient 命名图（计算后自动释放），不持久化投影 |
| **PageRank** | 图节点重要性排序算法。一个节点被越多高权重节点连接，其 PageRank 值越高。用于识别宽图谱中的核心枢纽知识点。本项目通过 `gds.pageRank.stream` 调用 |
| **度中心性（Degree Centrality）** | 节点的入度、出度和总度数值。高入度节点表示被大量边指向（如被大量文档实体 ALIGNED_TO 的 KP），高出度节点表示广泛连接到其他节点。本项目通过 `gds.degree.stream` 调用 |
| **指标自动重算** | 图谱数据变更后通过 Spring 事件驱动自动刷新指标缓存的机制。监听融合完成、文档抽取完成、CSV 导入完成事件，触发对应投影参数的指标重算。结果存入 Caffeine 本地缓存（TTL 5 分钟） |
| **MetricResult** | 指标查询 API 返回的通用结构 BO，含 `nodeId`、`nodeType`、`metricName`、`metricValue` 四个字段。`metricValue` 为 Double，PageRank 和度中心性共用此结构 |
| **前端 SPA** | Vue 3 + TypeScript 单页应用，通过 Vite 构建，消费后端 REST API，运行在浏览器桌面端（≥1280px） |
| **管理后台** | GraphNexus 前端的管理员界面，覆盖 6 个功能模块（文件管理、图谱可视化、成绩管理、智能问答、融合管理、图指标），不含学生端/家长端 |
| **Vite proxy** | 开发环境通过 Vite `server.proxy` 将 `/api` 请求转发到后端 Spring Boot（`localhost:8080`），避免 CORS 问题 |
| **力导向布局** | 图可视化中节点-边图的物理模拟布局算法，节点间存在斥力、边存在引力，迭代稳定后形成可读的拓扑结构。用于图谱可视化页面的默认布局 |
| **Markdown 渲染** | 前端将 LLM 返回的 Markdown 文本（含标题、列表、表格）渲染为 HTML 展示，使用 marked/markdown-it 等库 |
| **同名 Student 冲突** | `studentName` 模糊匹配到多个 Student 节点时，返回 HTTP 409 并列出所有匹配的 `(studentNo, name, className)` 供用户选择，不自动挑选 |
| **MASTERS 降级处理** | 若融合从未执行（MASTERS 边不存在），系统降级为直接查询 TESTED 路径并计算原始得分率（不做时间衰减），在 prompt 中标注"融合数据不可用，以下为原始考试得分率" |
| **CSV 双行表头** | 成绩 CSV 的特殊格式：第 1 行为列名（`题1~题N`），第 2 行为每题对应的知识点名称。同一题含多个知识点时用 `;` 分隔（如 `二次函数图像与性质;二次函数顶点式`）。数据行成绩格式为 `raw_score/max_score`，缺考标记为 `-/-` |
| **成绩事件图谱（简化模型）** | 区别于文档参考中的完整事件模型（Student → Event → Exam + Event → KnowledgePoint），本次采用简化三元路径 `Student → Exam → KnowledgePoint`，不创建 EventNode，ATTENDED + TESTED 边直接携带成绩属性 |
| **GradeFileType** | 成绩文件格式枚举（`FileParseType` 实现），v2 拆分为 `CSV` 和 `EXCEL` 两个值，分别表示 CSV 和 Excel（.xlsx/.xls）格式。业务类型统一由 `FileParser.BIZ_GRADE` 表达，格式细节由 `FileParserRegistry` 按扩展名路由 |
| **ExcelGradeParser** | `FileParser` 接口的 Excel 实现，支持 `.xlsx`/`.xls` 双行表头成绩文件解析。使用 Apache POI 读取工作簿，解析结果与 `CsvGradeParser` 一致（`CsvParsePayload`），业务类型 = `BIZ_GRADE`，格式类型 = `GradeFileType.EXCEL` |
| **成绩统一条件查询** | `GET /api/v1/file/grades` 端点支持 6 个可选查询参数（`studentNo`、`name`、`className`、`examNo`、`examName`、`subject`）任意组合 + 分页。取代原有的 `GET /api/v1/file/grades/exam/{examNo}` 单一考试查询端点。Repository 层通过 Spring Data JPA 方法名派生或 `@Query` + Specification 实现动态条件查询 |
| **exam_no 去重拒绝** | 成绩上传去重策略：以 `exam_no`（考试编号）为判重键，上传时若数据库中已存在相同 `exam_no` 且 `is_deleted=0` 的记录，返回 HTTP 409 + 错误码 A0016，提示用户先手动删除再重新上传。不自动覆盖，不使用文件 MD5 判重 |
| **ConstructionController** | 原 `GraphController`，图谱构建模块的 L1 API 控制器，端点 `/api/v1/graph/construction`。负责文档知识图谱抽取（`extract`）和文档子图查询（`getSubgraph`）。与 FusionController（融合）、MetricsController（指标）并列，三者均在 `api/graph/controller/` 包下 |
| **ConstructionService** | 原 `GraphService`，图谱构建模块的 L2 服务接口。编排"图谱构建 → 图谱融合"两阶段流水线（跨文档实体对齐由融合隐式完成）。位于 `application/graph/construction/service/`，与 `ExtractionService` 同包 |
| **ConstructionGraphRepository** | 图谱构建模块的 Neo4j 数据访问组件（L3）。负责图谱构建和成绩事件图谱的 Neo4j 操作：节点/边 CRUD（save/saveEdge）、文档子图查询（findByDocumentId/findEdgesByDocumentId）、文档子图删除（deleteByDocumentId）、考试图谱清理（deleteEdgesByExamNo/deleteExamNode）。从原 `GraphNodeRepository` 拆分而来 |
| **FusionGraphRepository** | 融合模块的 Neo4j 数据访问组件（L3）。负责融合和 MASTERS 的 Neo4j 操作：全量 KP 查询（findAllKnowledgePoints）、按名称查询 KP（findKnowledgePointsByNamesAndSubject）、边重定向（redirectEdges）、KP 删除（deleteKnowledgePoints）、MASTERS 批量 upsert（batchUpsertMastersEdges）、学生查询（findStudentsBySubject/findStudentsByKpNames）、回滚辅助方法。从原 `GraphNodeRepository` 拆分而来 |
| **QueryGraphRepository** | 智能问答和指标模块的 Neo4j 只读查询组件（L3）。负责：按姓名/学号查 Student（findStudentByName/findStudentByNo）、查 MASTERS（findMastersByStudentAndSubject/findMastersByStudentAndKpIds）、查 TESTED 路径（findTestedKpsByStudentAndSubject）、查前置依赖链（findPrerequisitesUpstream）、查学科列表（findDistinctSubjects）。从原 `GraphNodeRepository` 拆分而来 |
| **SubjectNode** | 新增 Neo4j 节点类型（Label `:Subject`），表示学科（如"数学""物理""英语"）。唯一标识 `name`。取代原 KnowledgePoint / Exam / FileNode 上的 `subject` 字符串属性。各节点通过 BELONGS_TO_SUBJECT 边指向对应的 SubjectNode。预留 `(:Subject)-[:CHILD_OF]->(:Subject)` 层级扩展 |
| **BELONGS_TO_SUBJECT** | 新增 Neo4j 关系边类型。方向：`(KnowledgePoint|Exam|FileNode)-[:BELONGS_TO_SUBJECT]->(:Subject)`。表示节点归属于某学科。融合分组从 `groupBy(subject string)` 改为按 BELONGS_TO_SUBJECT 边指向的 Subject 节点引用分组 |
| **两阶段流水线** | 图谱构建的显式工作流：「阶段一·图谱构建」（LLM 抽取 + 单文档内节点/边创建，含 Entity→KP 的 ALIGNED_TO 边）→「阶段二·图谱融合」（跨源 KP 合并 + MASTERS 重算）。跨文档实体对齐由融合隐式完成——融合合并重复 KP 时 `redirectEdges` 自动把所有 ALIGNED_TO 边重定向到规范 KP，无需独立对齐阶段。文档状态流转：`EXTRACTING→EXTRACTED→FUSING→COMPLETED` |
| **跨文档实体对齐（隐式）** | 不作为独立阶段，由图谱融合隐式完成：当两个同名 KP 被融合时，`FusionGraphRepository.redirectEdges` 动态发现并重定向所有入边（含 ALIGNED_TO: Entity→KP）到规范 KP。因此不同文档中指向同一概念的实体最终都会关联到统一的规范 KP。曾考虑独立 FuzzyMatch(entity.name, kp.name) 对齐阶段，但因 Entity 名称与 KP 名称语义维度不同（片段名 vs 标准概念）易误匹配，且与融合 redirectEdges 重复产生冗余边，故移除 |
| **跨源 KP 融合** | 全量/增量融合中显式合并 `fusionSource=DOCUMENT`（文档抽取）和 `fusionSource=CSV_IMPORT`（考试成绩）两种来源的同名知识点。策略：先按 `name + subject`（或 Subject 节点引用）精确匹配前置 pass，再走 FuzzyMatch。考试 KP 创建改为 `MERGE ON (name, subject)` 避免产生冗余节点 |
| **融合原子性** | 全量/增量融合的全部 merge + MASTERS 重算操作具备事务性：全部成功则提交，任一失败则回滚。具体方案（Neo4j 事务包装 vs 先记后做补偿回滚）由 DESIGN 阶段选型。融合失败时图谱状态不变，`fusion_log.status=FAILED` |
| **Subject 名称规范化** | LLM 抽取提示词中新增的指令：要求 LLM 输出标准化学科名（如"数学"而非"高中数学"或"初中数学"），优先使用文档元数据中提供的学科名（来自 `buildUserMessage` 的 `subject` 参数）。确保不同文档抽取出的 subject 名称一致，配合 Subject 节点引用分组 |
| **抽取提示词外置** | 抽取模块的 System Prompt 从 `ExtractionPromptBuilder` 的 Java 文本块迁到 `classpath:/prompts/*.md`，复用 ADR-011 的 `ResourceLoader` + `{{var}}` 范式，与智能问答模块 prompt 管理方式统一。User Message 拼接行为不变 |
| **抽取类型注册机制** | 抽取层（prompt 类型段 + validator 合法集合 + `convertToDomain` 路由）的类型来源统一为枚举/注册表，新增类型无需改 prompt 文案、validator 硬编码或 switch 分支。是持久化层 ADR-002 / AC-5 可扩展契约在抽取层的补齐 |
| **抽取类型混合策略** | 抽取类型定义采用混合方式：Java 枚举管类型契约（枚举值 / 校验 / 转换路由），md 管提示词文案描述；prompt 的类型段由枚举元数据自动生成，消除"枚举 vs prompt 文案 vs switch"三处漂移 |
| **few-shot 学科切换** | System Prompt 的 few-shot 示例按文档 `subject` 选择对应学科示例，未配置学科回退默认示例。缓解 ADR-003 的 few-shot 领域过拟合隐患。v1 仅交付数学 + 默认两套 |
| **异常日志分级** | GlobalExceptionHandler 按异常类型分级落日志：兜底未捕获 Exception → ERROR 级完整堆栈；BusinessException / MethodArgumentNotValidException / AccessDeniedException → WARN 级关键摘要（不含完整堆栈），避免预期内异常污染 error 日志 |
| **完整堆栈日志** | 含异常类名 + 异常 message + cause 链 + 出错行号的 ERROR 级日志条目，通过 MDC traceId 与请求链路关联，落入既有 graphnexus-error.log，供后端凭响应 traceId 检索定位 |
| **GraphConstructedEvent** | Spring 同步事件，图谱构建阶段全部完成后发布，载荷含 `source`(DOCUMENT/CSV)、`subject`、`kpNames`、`mode`(FULL/INCREMENTAL)、`documentId`/`examNo`。由 `GraphConstructedEventListener`(analysis 模块) 同步 `@EventListener` 消费触发融合，是"构建完成→融合"的唯一显式触发源，取代 `ConstructionServiceImpl` 直接调用 `fuseIncremental` 与 grade 路径 `GradeUploadedEventListener`(`@Order(2)`) 直接 `fuseFull` + `@Order` 隐式排序。载荷自包含，发布方不依赖消费方 |

## 已锁技术决策

| 决策 | 取值 | 锁定时间 | 来源 |
|------|------|---------|------|
| JDK 版本 | Java 17 LTS | 2026-06-08 | `docs/tech-stack-java.md` |
| 构建工具 | Maven 3.9.x（单模块） | 2026-06-08 | `docs/tech-stack-java.md` |
| 核心框架 | Spring Boot 3.3.x | 2026-06-08 | `docs/tech-stack-java.md` |
| 图数据库 | Neo4j 5.x + Spring Data Neo4j 7.x | 2026-06-08 | `docs/tech-stack-java.md` |
| 关系数据库 | MySQL 8.0 + Spring Data JPA + Hibernate 6.4+ | 2026-06-08 | `docs/tech-stack-java.md` |
| 缓存 | Redis 7.x (Lettuce) + Caffeine 本地缓存 | 2026-06-08 | `docs/tech-stack-java.md` |
| 消息队列 | RabbitMQ 3.x + Spring AMQP | 2026-06-08 | `docs/tech-stack-java.md` |
| LLM 集成 | Spring AI 1.0.x + 自研 LLMGateway + WebFlux WebClient | 2026-06-08 | `docs/tech-stack-java.md` |
| 文件存储 | MinIO 8.x | 2026-06-08 | `docs/tech-stack-java.md` |
| 安全认证 | Spring Security 6.x + JWT (jjwt 0.12) | 2026-06-08 | `docs/tech-stack-java.md` |
| 依赖注入方式 | 构造器注入（`private final` + `@RequiredArgsConstructor`） | 2026-06-11 | `docs/项目规范.md` §1.4.3 |
| POJO 命名后缀 | DO / DTO / BO / VO / Query | 2026-06-11 | `docs/项目规范.md` §1.4.1 |
| 数据库命名 | 小写蛇形单数，逻辑删除，`utf8mb4` | 2026-06-11 | `docs/项目规范.md` §1.4.1 |
| REST API 命名 | 小写下划线分隔，单数资源名 | 2026-06-11 | `docs/项目规范.md` §1.4.1 |
| 测试框架 | JUnit 5 + Mockito + Testcontainers + ArchUnit | 2026-06-08 | `docs/tech-stack-java.md` |
| PDF 解析策略 | `DocumentParser` 接口 + `MinerUDocumentParser`（v4 精准解析 API，`vlm` 模型，主用）+ `PdfBoxDocumentParser`（PDFBox，兜底）；接口契约：输入 `byte[]` → 输出 `ParseResult`（textContent/pageCount/metadata）；L2 Service 只依赖接口不依赖实现类 | 2026-06-11 | `document-process-pdf-minimal` REQUIREMENT |
| MinerU 兜底策略 | MinerU v4 所有异常（网络/超时/`state=failed`/Token过期/轮询超时）→ 自动 fallback 到 PDFBox；PDFBox 也失败才标记 `FAILED`；`mineru.enabled=false` 可全局关闭 MinerU 直接走 PDFBox；`metadata.parser` 字段记录实际使用的解析器 | 2026-06-16 | `mineru-pdf-parser` REQUIREMENT |
| 图节点/边抽象策略 | 所有 Neo4j 节点/边类型通过 `GraphNode`/`GraphEdge` 抽象基类统一契约，新增类型通过注册机制声明，核心持久化链路面向抽象编程不依赖具体子类 | 2026-06-13 | `knowledge-graph-extraction` CHANGE |
| 知识图谱抽取策略 | LLM 从文档文本中一次性抽取 Entity + KnowledgePoint + KnowledgeCategory + 全部关系边；同步执行，先校验 JSON Schema 再写入 Neo4j；重复抽取全量覆盖（先删旧子图再写新子图） | 2026-06-13 | `knowledge-graph-extraction` CHANGE |
| LLMGateway 最小契约 | v1 最小接口：`String chat(String systemPrompt, String userMessage)`；调用失败抛 `BusinessException(C0001)`；后续版本扩展路由/配额/降级 | 2026-06-13 | `knowledge-graph-extraction` REQUIREMENT |
| Entity ↔ KnowledgePoint 双层模型 | EntityNode 绑定文档原文片段（溯源），KnowledgePointNode 为跨文档标准概念（去重），两者通过 ALIGNED_TO 边连接；v1 不做跨文档 KP 去重合并，每次抽取创建新 KP | 2026-06-13 | `knowledge-graph-extraction` REQUIREMENT |
| Neo4j 持久化方式 | v1 使用 `Neo4jClient` + 手动 Cypher（MERGE/CREATE/UNWIND）进行节点和边的读写，不依赖 `Neo4jTemplate.save()`。原因：SDN 7.x 对 `@Node` 抽象父类继承映射存在兼容问题。查询结果通过 `Neo4jClient.query().fetch()` 手动提取属性 | 2026-06-14 | `knowledge-graph-extraction` DEV |
| GraphEdge 通用属性 | `GraphEdge` 基类含 `weight`(Double, 默认 1.0) + `description`(String) 两个通用属性。`weight` 用于后续事件边掌握度/置信度，`description` 说明关系含义。`PrerequisiteEdge.strength` 同步映射到 `weight` | 2026-06-14 | `knowledge-graph-extraction` DEV |
| ReferencesEdge 拆分 | DERIVES/CONTAINS/REFERENCES 三种边各自独立成类（`DerivesEdge`/`ContainsEdge`/`ReferencesEdge`），与 `EdgeType` 枚举一一对应。共 8 种逻辑边类型 → 8 个独立 Java 类 | 2026-06-14 | `knowledge-graph-extraction` DEV |
| DocumentRepository JPQL（已废弃） | ~~`findByIdAndIsDeletedFalse` 等逻辑删除查询使用 `@Query("... WHERE d.isDeleted = 0")` 显式 JPQL，避免 Hibernate 6.5 对 `Boolean` + MySQL TINYINT 的 `isFalse()` 谓词构建 bug~~ → 由 `jpa-query-refactor` 升级 Hibernate + 替换为方法名派生 | 2026-06-14 | `knowledge-graph-extraction` DEV |
| Repository 方法命名规范 | Spring Data JPA Repository 方法名：① 不含 DO/Entity 后缀（如 `findByStudentNo` 而非 `findExamRecordDOByStudentNo`）；② 方法名完整反映查询条件（如含 `IsDeletedFalse` 和 `StatusNot` 时须显式出现在方法名中）；③ 复杂查询（LIKE + NULL 可选参数、Object[] 投影、GROUP BY）保留 `@Query` | 2026-06-19 | `jpa-query-refactor` REQUIREMENT |
| Hibernate Boolean/TINYINT 谓词 bug 修复 | 升级 Hibernate 到修复 Boolean/TINYINT 谓词生成 bug 的版本，使 `findByIsDeletedFalse()` 方法名派生查询正常工作。来源：Hibernate 6.5 对 MySQL TINYINT + Boolean 属性的 `isFalse()` 谓词构建异常，升级后无需再用 `@Query("... WHERE d.isDeleted = 0")` 规避 | 2026-06-19 | `jpa-query-refactor` REQUIREMENT |
| CSV 成绩解析模型 | v1 采用双行表头格式（第 1 行列名 + 第 2 行知识点名称），成绩 `raw/max`，缺考 `-/-`。第 1 行元数据列含 `考试编号`（examNo 来源）。动态列数，解析为 `ScoreDetail` 列表存入 MySQL JSON 列。编码默认 UTF-8，兼容 GBK | 2026-06-15 | `csv-grade-import` REQUIREMENT |
| 成绩事件图谱模型（简化版） | Neo4j 采用三元路径 `(:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(:KnowledgePoint)`，边均为纯结构无属性，不创建 EventNode。所有分数（各题得分/总分/排名）仅存 MySQL `exam_record`。成绩解析的 KnowledgePoint 独立存在，不与文档图谱 KP 融合。v2 已移除 MinIO 文件存储，原始文件解析后不保留 | 2026-06-15 / 更新 2026-06-19 | `csv-grade-import` DESIGN → `grade-management-refactor` |
| CSV 上传幂等策略（已废弃） | ~~以 CSV 文件 MD5 为判重键，重复上传覆盖 MySQL 记录 + 更新 Neo4j 边属性（非新增节点/边）~~ → 由 `grade-management-refactor` 替换为 exam_no 判重拒绝策略 | 2026-06-15 | `csv-grade-import` REQUIREMENT |
| 成绩上传去重策略 | 以 `exam_no`（考试编号）为判重键，上传时若 exam_no 已存在则返回 HTTP 409 拒绝，提示用户先手动删除再重新上传。不再使用文件 MD5 判重，不再自动覆盖 | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 全局删除约束 | 所有多存储系统实体的删除操作必须遵守：① 级联删除所有关联数据；② 幂等（删除已删除资源无报错）；③ 中间状态（先标记 DELETING/is_deleted，再清理外部系统，最后物理删除）；④ 共享节点保留不级联 | 2026-06-15 | `csv-grade-import` DESIGN（全局约束） |
| KP 融合策略模式 | 定义 `KpMatchingStrategy` 接口（`double match(KpCandidate a, KpCandidate b)`），v1 首发 `FuzzyMatchStrategy`（名称归一化 + 编辑距离/Jaccard + subject 约束，阈值 0.85 可配置）。新增匹配算法只需实现接口 + yml 配置切换，不修改融合引擎核心逻辑 | 2026-06-15 | `wide-graph-fusion` REQUIREMENT |
| MASTERS 权重策略模式 | 定义 `WeightCalculationStrategy` 接口（输入 `List<TestedRecord>` → 输出 `weight` + 摘要 JSON），v1 首发 `TimeDecayStrategy`（月衰减因子 0.9，缺考不计入，同考试多次取平均）。新增聚合算法只需实现接口 + yml 配置切换 | 2026-06-15 | `wide-graph-fusion` REQUIREMENT |
| 融合回滚机制 | `fusion_log` 记录每次融合的完整快照（融合明细 JSON + MASTERS 变更 JSON），回滚按日志逆向恢复 Neo4j 图状态。回滚粒度 = 单次融合操作，幂等。不支持跨多次融合的部分回滚 | 2026-06-15 | `wide-graph-fusion` REQUIREMENT |
| KP 字段冲突解决 | 融合时若多个源 KP 的 `description`/`gradeLevel` 字段不同：文档抽取 KP 的非空字段覆盖 CSV 侧空字段，CSV 侧非空字段保留在 `fusionSource` 标记中供审计。冲突字段以文档侧为准 | 2026-06-15 | `wide-graph-fusion` CHANGE（Q3→A） |
| 融合触发策略 | 双触发模式：① 手动全量融合 `POST /api/v1/graph/fusion/execute`；② 自动增量融合 — 新文档抽取或新 CSV 上传完成后自动触发，仅融合涉及的 KP + 重算受影响学生的 MASTERS 边 | 2026-06-15 | `wide-graph-fusion` CHANGE（Q1→C） |
| 意图识别策略 | v1 规则匹配（关键词 + 实体提取），仅支持 `STUDENT_DIAGNOSIS` 一种意图。意图识别失败（关键词无法匹配）时返回 HTTP 400 提示"无法识别查询意图，请更明确地描述"。后续 change 可扩展为 LLM 分类或 embedding 匹配 | 2026-06-17 | `intelligent-qa` CHANGE（Q1→A） |
| LLM 输出格式 | LLM 分析结论以中文 Markdown 格式返回，禁止前导语。模板文件化管理（classpath `prompts/`），通过配置切换。格式不合规自动重试 ≤2 次 | 2026-06-17 | `intelligent-qa` CHANGE（Q4→A） |
| 智能问答异步持久化 | QA 任务记录持久化到 MySQL `query_task` 表（日志类表，不设逻辑删除），含完整生命周期状态 + 子图 JSON + token 用量，支持历史查询 | 2026-06-17 | `intelligent-qa` CHANGE（Q2→A） |
| 子图数据独立端点 | LLM 分析结论和子图结构数据通过独立 API 端点返回（`GET /api/v1/analysis/subgraph/{taskId}`），不混在同一响应体中。前端可选择性渲染图可视化 | 2026-06-17 | `intelligent-qa` CHANGE（Q3→B） |
| 图指标计算引擎 | 使用 Neo4j GDS 5.x 原生算法（PageRank + 度中心性），通过 Cypher `gds.*.stream` 调用，transient 命名图模式（计算后释放）。结果仅 API 返回不持久化，Caffeine 缓存，图谱变更后事件驱动自动重算 | 2026-06-17 | `graph-metrics` REQUIREMENT |
| Spring 事件机制 | 首次引入 `ApplicationEventPublisher` + `@EventListener` 实现模块间解耦通知。图谱变更后发布 `GraphChangedEvent`，由 `MetricsCacheInvalidator` 消费并清空 Caffeine 缓存。同步执行（`clearCache` O(1)），不阻塞主流程 | 2026-06-17 | `graph-metrics` DESIGN D4 |
| GDS 图投影边方向 | PREREQUISITE_OF/BELONGS_TO/CHILD_OF/TESTED/ATTENDED/EXTRACTS/REFERENCES/DERIVES/CONTAINS → NATURAL 方向；ALIGNED_TO/MASTERS → UNDIRECTED（双向语义对等，利于重要性传递） | 2026-06-17 | `graph-metrics` DESIGN §2.3 |
| 指标缓存策略 | Caffeine 本地缓存，按投影参数 Hash 做 key，TTL 5 分钟可配，最大 50 条目。图变更时全量清空（`invalidateAll`）。并发安全：`Cache.get(key, loader)` 确保同 key 仅触发一次 GDS 计算 | 2026-06-17 | `graph-metrics` DESIGN D3 |
| 前端框架 | Vue 3 + TypeScript + Vite | 2026-06-17 | `frontend-ui` CHANGE（用户选定） |
| UI 调性 | 极简（Minimal）— 参考 Linear/Vercel/Stripe | 2026-06-17 | `frontend-ui` CHANGE 步骤 0.6 |
| 前端组件库 | 待 DESIGN 阶段决策（候选：Element Plus / Naive UI / Ant Design Vue） | — | `frontend-ui` CHANGE |
| 图可视化库 | 待 DESIGN 阶段决策（候选：ECharts / Cytoscape.js / D3.js） | — | `frontend-ui` CHANGE |
| V1 目标分辨率 | 桌面端 ≥1280px 宽度，不做移动端 | 2026-06-17 | `frontend-ui` REQUIREMENT |
| V1 鉴权 | 跳过（后端 Spring Security 临时放开或内网部署） | 2026-06-17 | `frontend-ui` CHANGE |
| 文件处理 Pipeline 抽象 | 定义 `FileProcessingPipeline` 接口，封装"上传→解析→入库→图谱"全链路。新增文件类型只需实现接口 + 注册，Controller/Service 核心代码零改动。策略模式 + Spring 依赖注入实现 | 2026-06-18 | `extensible-file` REQUIREMENT |
| FileController / GradeController 拆分 | 当前 `DocumentController` 拆分为 `FileController`（文档类：PDF/TXT 上传、列表、CRUD、解析触发）+ `GradeController`（成绩类：CSV 上传、列表、按考试编号查询/删除）。两组端点职责清晰，共享底层 Pipeline 抽象 | 2026-06-18 | `extensible-file` REQUIREMENT |
| 文档状态机 v2 | 扩展 `DocumentStatus` 为 8 状态：`UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED`（成功路径），任意 `*ING` 状态失败回退到上一步 `*ED` 状态 + `failReason` 记录失败步骤/原因；`FAILED` 状态保留用于不可恢复错误；`DELETING` 独立于处理状态。失败后可手动 `/process` 从当前状态继续 | 2026-06-18 | `extensible-file` REQUIREMENT |
| TXT 文件解析策略 | TXT 文件直接读取文本内容（UTF-8 主试 + GBK 回退），不做版面分析。原始文本直接传给 LLM 做知识抽取，走与 PDF 相同的文档处理链路（MinIO 存储 + `document` 表 + LLM 抽取 + 融合） | 2026-06-18 | `extensible-file` REQUIREMENT |
| 同步上传链路 | 文档上传后串联执行 解析→抽取→融合，一次 HTTP 请求返回最终结果（`COMPLETED` 或中间失败状态）。不引入异步队列。手动 `/process` 端点保留用于异常恢复和断点续跑 | 2026-06-18 | `extensible-file` REQUIREMENT |
| `file_type` 字段 | `document` 表新增 `file_type` VARCHAR(20) NOT NULL 字段，值来源于 `FileParseType` 枚举（如 `PDF`、`TXT`）。用途：① 上传时路由到对应 Pipeline；② 列表筛选条件。存量数据回填为 `PDF` | 2026-06-18 | `extensible-file` REQUIREMENT |
| 文档列表条件查询 | `GET /api/v1/document`（FileController）支持可选查询参数 `file_type`（精确匹配）+ `name`（LIKE 模糊搜索），配合 `pageNum`/`pageSize` 分页。不传筛选参数时行为不变。MySQL LIKE 实现，不引入全文检索 | 2026-06-18 | `extensible-file` REQUIREMENT |
| 成绩文件不存 MinIO | 成绩文件上传后即时解析，仅保留 MySQL 结构化数据 + Neo4j 图数据，原始文件不存入 MinIO。上传链路简化为 解析 → MySQL → Neo4j；删除链路简化为 MySQL → Neo4j（无 MinIO 清理） | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 成绩去重策略 | 以 `exam_no` 为判重键，重复上传返回 HTTP 409 拒绝（错误码 A0016），提示用户先手动删除。不使用 MD5 判重，不自动覆盖 | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 成绩删除粒度 | 仅支持按 `exam_no` 整场考试删除，不支持单条学生记录删除。与上传粒度（一个文件 = 一个 exam_no = 一次考试）对称 | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| GradeFileType 枚举拆分 | `GradeFileType` 由单一 `CSV_GRADE` 拆为 `CSV` 和 `EXCEL` 两个值，分别表示 CSV 和 Excel 文件格式。业务类型统一由 `FileParser.BIZ_GRADE` 表达 | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 成绩条件查询统一端点 | `GET /api/v1/file/grades` 通过可选参数支持多维度组合查询，合并原 `GET /api/v1/file/grades/exam/{examNo}` 功能。查询参数：`studentNo`、`name`（模糊）、`className`、`examNo`、`examName`（模糊）、`subject` | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 成绩模块事件驱动解耦 | 成绩处理模块（`application/file/grade/`）不直接调用图谱代码。上传链路：解析 → MySQL → 发布 `GradeUploadedEvent` → 结束；删除链路：MySQL 删除 → 发布 `GradeDeletedEvent` → 结束。图谱构建/清理由独立的 `GradeGraphEventListener` 监听事件完成。成绩模块仅依赖 `ApplicationEventPublisher` + 事件类，不注入 `GraphNodeRepository` 或图谱 Service | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| GradeDeletedEvent | Spring 事件，成绩删除后发布，携带 `examNo`、关联学生数、知识点列表。由 `GradeGraphEventListener` 消费，负责清理 Neo4j 中对应 Exam 节点及 ATTENDED/TESTED 边 | 2026-06-19 | `grade-management-refactor` REQUIREMENT |
| 图谱构建模块命名规范 | `GraphController` → `ConstructionController`，`GraphService` → `ConstructionService`。URL `/api/v1/graph` → `/api/v1/graph/construction`（与 `/api/v1/graph/fusion`、`/api/v1/graph/metrics` 形成命名空间层次）。Service 包从 `core/service/` 移至 `construction/service/` | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| GraphNodeRepository 三模块拆分 | 原 `GraphNodeRepository`（666 行）按子域拆为三个独立 Repository 并删除原类：`ConstructionGraphRepository`（构建+成绩事件）、`FusionGraphRepository`（融合+MASTERS）、`QueryGraphRepository`（智能问答+指标只读）。各模块 Service/EventListener 注入对应 Repository | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| Subject 节点化 | Neo4j 中学科信息从 KnowledgePoint/Exam/FileNode 的 `subject` 字符串属性改为独立 `SubjectNode`（Label `:Subject`）+ `BELONGS_TO_SUBJECT` 边。融合分组从 `groupBy(subject string)` 改为按 Subject 节点引用分组。存量数据迁移脚本幂等执行。MySQL 中 `document.subject` / `exam_record.subject` 保留不删。预留 `(:Subject)-[:CHILD_OF]->(:Subject)` 学科层级扩展（v2） | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| 两阶段流水线（构建→融合） | 图谱构建实现显式两阶段工作流：① 图谱构建（LLM抽取+单文档内节点/边+Entity→KP ALIGNED_TO）→ ② 图谱融合（跨源KP合并+MASTERS重算）。跨文档实体对齐由融合隐式完成（redirectEdges 重定向 ALIGNED_TO 边）。`document.status` 流转：`EXTRACTING→EXTRACTED→FUSING→COMPLETED`。原设计含独立"实体对齐"阶段，后因与融合 redirectEdges 重复且 Entity.name↔KP.name 匹配语义不成立而移除 | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| 跨源 KP 融合策略 | 全量/增量融合均显式处理 `DOCUMENT` ↔ `CSV_IMPORT` 跨源 KP 合并。先按 `name + subject`（或 Subject 节点引用）精确匹配前置 pass，再走 FuzzyMatch（阈值 0.85）。考试 KP 创建改为 `MERGE ON (name, subject)` 而非 `MERGE ON id`（UUID），避免与文档 KP 产生冗余节点 | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| 融合原子性方案 | 融合操作必须具备原子性：全部 merge + MASTERS 重算成功提交，任一失败回滚。具体方案（Neo4j 事务包装 vs 先记后做补偿回滚）由 DESIGN 阶段选型确定。失败时 `fusion_log.status=FAILED`，Neo4j 图谱状态不变 | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| 文档状态机 v2（维持） | `graph-construction-refactor` 初版曾扩展 v3（新增 ALIGNING/ALIGNED 两状态用于独立实体对齐阶段），后因跨文档对齐改由融合隐式完成而回退到 v2（8 状态）。成功路径：`UPLOADED→PARSING→PARSED→EXTRACTING→EXTRACTED→FUSING→COMPLETED`。FUSING 失败回退到 EXTRACTED | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| LLM 提示词 subject 规范化 | `ExtractionPromptBuilder` 的 System Prompt 增加 subject 名称规范化指令：要求 LLM 使用标准化学科名（如"数学"而非"高中数学"），优先使用文档元数据中提供的学科名。`ExtractionService.convertToDomain()` 不再设置 KnowledgePointNode 的 `subject` 属性，改为创建/查找 SubjectNode + BELONGS_TO_SUBJECT 边 | 2026-06-20 | `graph-construction-refactor` REQUIREMENT |
| 抽取提示词外置 + 类型混合策略 | 抽取 System Prompt 外置到 `classpath:/prompts/*.md`（对齐 ADR-011）；抽取类型采用混合策略——Java 枚举管类型契约（值/校验/转换路由），md 管文案，prompt 类型段由枚举自动生成；few-shot 按 subject 切换 + 默认回退。补齐 ADR-002 在抽取层的可扩展契约 | 2026-06-21 | `extraction-prompt-pluggable` REQUIREMENT |
| 系统异常日志策略 | GlobalExceptionHandler 兜底未捕获 Exception 必须 `log.error` 完整堆栈（含 cause 链 + 行号），traceId 关联；BusinessException / 校验 / 权限异常 WARN 级摘要不打完整堆栈。完整堆栈只入后端日志不回前端 | 2026-06-21 | `exception-traceability` REQUIREMENT |
| 前端 ErrorResponse 不增强 | 系统异常响应体保持泛化：userTip 不变、errorMessage 仍为 `系统内部异常: <异常类SimpleName>`，不向客户端暴露异常 message / 堆栈 / 根因（安全）；细节走日志 | 2026-06-21 | `exception-traceability` REQUIREMENT |
| 异常日志零新增基础设施 | 复用既有 TraceIdFilter(MDC) + logback-spring.xml + graphnexus-error.log，不新增 appender、不引入日志收集系统、不改 pom.xml | 2026-06-21 | `exception-traceability` REQUIREMENT |
| fusion 归属 analysis + 事件驱动触发 | fusion 全栈（api-dto + application + infrastructure）从 graph 模块迁入 analysis 模块；构建完成改用同步 `GraphConstructedEvent` 触发融合（文档路径 `mode=INCREMENTAL`，成绩路径 `mode=FULL`），取代 `ConstructionServiceImpl` 直接调用 + grade `@Order(2)` 监听器。依赖方向 `construction(graph) → 事件 → fusion(analysis)`，禁止 analysis 反向注入 graph Service、禁止 construction 再注入 `FusionService`。同步语义保留（`@EventListener` 同线程，单请求 `COMPLETED`），不引异步/MQ/schema 变更，fusion 行为等价 | 2026-06-21 | `fusion-to-analysis-event-driven` CHANGE（Q1/Q2） |
| REST 融合端点迁移 | `/api/v1/graph/fusion/{execute,status,rollback}` → `/api/v1/analysis/fusion/{execute,status,rollback}`（破坏性），前端融合管理 UI + API 文档同步；旧路径 `/graph/fusion/*` 是否保留别名由 DESIGN 锁定（REQUIREMENT 默认不保留→404） | 2026-06-21 | `fusion-to-analysis-event-driven` CHANGE（Q3） |

## 默认行为

- 所有 Service 层方法使用构造器注入，禁止 `@Autowired` 字段注入
- 所有数据库表必须含 `id`、`create_time`、`update_time` 三字段
- 所有业务表使用逻辑删除（`is_deleted` 字段），日志表除外
- 禁止使用数据库外键约束，关联在应用层维护
- L1 禁止直接调用 L3；L3 不包含业务逻辑
- 异常在 L3 捕获并向上抛 BusinessException，在 L1 由 GlobalExceptionHandler 统一处理
- 注释使用中文，代码标识符使用英文，注释的作者设置为 Jay
- **分层异常传递**：L3 捕获技术异常 → 向上抛 `BusinessException`（不记日志）；L2 捕获并记日志（含参数和上下文）；L1 禁止向上抛，由 `GlobalExceptionHandler` 统一处理
- **事务管理**：`@Transactional` 仅放 L2 Service 方法；查询用 `@Transactional(readOnly = true)`；Neo4j 与 MySQL 事务独立，通过消息队列最终一致
- **数据对象转换链**：前端 JSON → L1 DTO/VO → L2 BO/Query → L3 DO/Entity，跨层禁止直接传 DO
- **文档删除联动**：幂等操作。① `findById`（含 DELETING 状态，允许重试）→ ② `isDeleted=1` 直接返回 → ③ 进入/保持 DELETING → ④ MinIO 删除（容忍文件不存在）→ ⑤ Neo4j 图谱清除 → ⑥ `markDeleted()`。正常查询排除 DELETING 状态
- **Git 提交格式**：`<type>(<change-id>): <task-id> <subject>`（如 `feat(init-platform): T01 创建包结构`）
- Git提交时禁止设置共同创作者 Commit without co-author
- Git禁止提交.specs/下的内容
- **字段冗余策略**：非频繁修改 + 非唯一索引 + 非 varchar 超长字段允许适当冗余，避免每次查询 JOIN 统计
- **本地开发环境**：所有基础设施组件（Neo4j 5.x / MySQL 8.0 / MinIO / Redis 7.x / RabbitMQ 3.x）通过 podman 容器化部署，`application-dev.yml` 中配置的连接参数可直接使用。集成测试使用 `@SpringBootTest` + `@ActiveProfiles("dev")` 直连 podman 中的真实组件，不需要 Testcontainers 或 @MockBean 替代
- 当有新的sql文件产生时，需要将其同步到resources/db/init.sql中
- **后端异常定位路径**：前端响应返回 traceId → 后端用 traceId 在 `logs/graphnexus-error.log`（或控制台）grep → 读完整堆栈定位出错类与行，无需复现

## 全局删除约束

> 所有涉及多存储系统（MySQL / Neo4j / MinIO）的实体删除操作，必须遵守以下约束。违反即设计退回。

### C1 · 级联删除

- 删除主实体时，必须同步清理其所有关联数据，不得残留孤儿记录
- 级联范围包括：MySQL 关联记录、MinIO 文件、Neo4j 关联边及专属节点
- **共享节点**（如 Student、KnowledgePoint）若可能被其他实体引用，**禁止级联删除**，仅删边不删节点

### C2 · 幂等性

- 删除已删除或已不存在的资源必须成功返回（无异常），最终状态一致
- MinIO 文件删除必须容忍 `FileNotFoundException`（文件可能已被手动清理或首次上传失败）
- Neo4j 边/节点删除使用 `DETACH DELETE` 或先判存在，不存在时跳过不报错

### C3 · 中间状态

- 跨系统删除前必须先设置中间状态标记（`DELETING` 或 `is_deleted=true`），防止并发操作冲突
- 中间状态下的实体对正常查询不可见（查询排除 DELETING/is_deleted 记录）
- 进入中间状态后，外部系统删除失败可基于中间状态重试，不再依赖原始请求上下文
- 所有外部系统清理完成后，执行物理删除（MySQL）或标记终态（`DELETED`）
- 若某外部系统删除失败：记录 ERROR 日志（含实体标识 + 失败原因），不阻塞后续步骤，最终由定时任务或手动补偿

### C4 · 删除顺序

```
① MySQL 标记中间状态（事务保障，失败即终止）
② MinIO 文件删除（幂等，失败记 WARN 不阻塞）
③ Neo4j 关联边删除（幂等）
④ Neo4j 专属节点删除（幂等）
⑤ MySQL 物理删除 / 标记终态 DELETED
```

### C5 · 适用实体

| 实体 | 主存储 | 级联目标 | 中间状态字段 | 保留节点 |
|------|--------|---------|-------------|---------|
| Document（教辅 PDF） | MySQL `document` | MinIO PDF 文件 + Neo4j EntityNode/边 | `status = 'DELETING'` | KnowledgePoint |
| Exam（考试成绩） | MySQL `exam_record` | Neo4j Exam 节点/ATTENDED/TESTED 边（v2 已移除 MinIO 文件级联，成绩文件不再存入 MinIO） | `is_deleted = 1` | Student、KnowledgePoint |


## 既有抽象索引

| 路径（预留） | 能力 | 触发场景 |
|---|---|---|
| `common/exception/BusinessException.java` | 统一业务异常 | 所有 Service 层业务错误 |
| `common/exception/GlobalExceptionHandler.java` | 全局异常 → ErrorResponse | 所有 HTTP 请求异常路径 |
| `common/logging/TraceIdFilter.java` | 入口生成/提取 traceId → MDC | 每个 HTTP 请求 |
| `common/ApiResult.java` | 统一 API 响应体 | 所有 Controller 返回 |
| `common/PageResult.java` | 统一分页响应体 | 所有分页查询接口 |
| `application/document/service/DocumentParser.java` | 可扩展 PDF 解析接口 | PDF 解析调用（PDFBox / 未来 MinerU） |
| `infrastructure/storage/` | MinIO 文件存储适配器 | PDF 文件上传/下载/删除 |
| `application/graph/service/` | 知识图谱抽取与查询服务 | LLM 抽取触发 + 子图查询 |
| `infrastructure/neo4j/node/` | 图节点抽象基类 + 具体节点类 | Neo4j 节点定义与持久化 |
| `infrastructure/neo4j/edge/` | 图边抽象基类 + 具体边类 | Neo4j 关系边定义与持久化 |
| `infrastructure/neo4j/repository/` | 通用图节点/边 Repository | Neo4j 查询与写入 |
| `application/llmgateway/service/` | LLM 网关服务（v1 最小调用） | LLM API 调用（chat 方法） |
| `infrastructure/llm/client/SpringAiLlmGateway.java` | LlmGateway 的 Spring AI 实现 | LLM 调用 |
| `infrastructure/llm/config/LlmConfig.java` | ChatModel bean 手动创建 | 绕过 Spring AI 自动配置 |
| `api/file/controller/GradeController.java` | 成绩上传/查询/删除 API（L1） | CSV/Excel 成绩文件上传、条件查询、级联删除 |
| `application/file/grade/parser/` | 成绩文件解析器（L2） | `CsvGradeParser`（.csv）+ `ExcelGradeParser`（.xlsx/.xls），均实现 `FileParser` 接口，业务类型统一为 `BIZ_GRADE` |
| `application/file/grade/service/` | 成绩上传编排 + 查询 + 删除（L2） | 成绩上传（解析 → MySQL → Neo4j，无 MinIO）+ 条件查询 + exam_no 级联删除 |
| `application/graph/fusion/strategy/` | KP 匹配策略 + MASTERS 权重计算策略接口与实现 | 融合匹配算法替换 + 权重算法替换 |
| `application/graph/fusion/service/` | 融合引擎服务（KP 合并 + MASTERS 聚合 + 回滚） | 手动/增量融合触发 + 回滚 |
| `infrastructure/mysql/fusion/` | `fusion_log` 表 DO/Repository（L3） | 融合日志持久化与查询 |
| `infrastructure/mysql/document/` | `document` 表 + `exam_record` 表 DO/Repository（L3） | 文档元数据 + 成绩原始记录持久化 |
| `application/query/service/` | 智能问答引擎（意图识别 + 剪枝策略调度 + LLM 调用编排） | 学生薄弱点诊断 + 子图生成 + LLM 分析 |
| `application/analysis/strategy/` | `SubgraphPruningStrategy` 接口 + 各剪枝策略实现 | 任务驱动图剪枝，v1 首发 `StudentDiagnosisStrategy` |
| `application/query/prompt/` | Prompt 模板加载 + 组装服务 | 按意图加载 system/user prompt 模板 + 子图数据注入 |
| `api/query/controller/` | 智能问答 API（L1） | `POST /ask` + `POST /ask-async` + `GET /result/{taskId}` |
| `api/analysis/controller/` | 图分析 API（L1） | `GET /subgraph/{taskId}`，后续扩展 PageRank/度中心性等 |
| `infrastructure/mysql/query/` | `query_task` 表 DO/Repository（L3） | 问答任务生命周期持久化 |
| `application/graph/metrics/service/` | 图指标计算服务（GDS 调用 + 缓存 + 事件监听） | PageRank/度中心性计算 + Caffeine 缓存管理 + 图谱变更自动重算 |
| `infrastructure/neo4j/gds/` | GDS 过程调用适配器（L3） | 封装 `gds.pageRank.stream` / `gds.degree.stream` Cypher 调用，构建命名图投影 + 结果映射 |
| `api/graph/controller/MetricsController.java` | 图指标查询 API（L1） | `GET /api/v1/graph/metrics/pagerank` + `GET /api/v1/graph/metrics/degree` |
| `application/graph/metrics/model/` | 指标计算结果 BO/VO | `MetricResult`（nodeId/nodeType/metricName/metricValue）通用结构 |
| `application/graph/metrics/event/GraphChangedEvent.java` | 图谱变更通知事件 | 所有图谱写操作完成后发布，消费者（MetricsCacheInvalidator）监听并清空缓存 |

## 禁动清单

> 以下路径不允许任何 change 的 `write_files` 触碰（除非显式申请解禁）。

- `pom.xml` — 依赖变更需走独立 change 评估
- `docs/项目规范.md` — 规范变更需全员评审
- `docs/tech-stack-java.md` — 技术栈变更需架构评审