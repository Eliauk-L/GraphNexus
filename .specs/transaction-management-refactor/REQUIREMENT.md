# REQUIREMENT: 后端事务管理策略梳理与重构

- **Change ID**: `transaction-management-refactor`
- **关联**: `@.specs/transaction-management-refactor/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为前端用户，我想在文件管理页面实时看到文档处理进度（PARSING→PARSED→EXTRACTING→EXTRACTED→FUSING→COMPLETED），以便了解当前处理阶段而非面对长时间无响应后突然跳变。
- **US-2**：作为后端开发者，我想事务边界有清晰一致的规范（状态更新=短事务、长耗时操作=无事务、事件=事务外），以便不再需要 `afterCommit` 手动回调、`saveAndFlush()` 假可见性 workaround、以及猜疑"这里到底该不该加 @Transactional"。
- **US-3**：作为系统，我想 MySQL 状态变更与 Neo4j 图写入的失败场景有明确的补偿语义（Neo4j 失败 → MySQL 状态回退 + failReason），以便即使跨存储无强一致事务，系统也能进入可恢复的已知状态而非静默不一致。
- **US-4**：作为运维人员，我想事务配置统一可审计（所有 @Transactional 在 public 方法、@EventListener 不标注事务、事件不在事务内发布），以便排查事务相关 Bug 时有确定的预期而非逐个方法猜测。

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 文档状态中间态对前端轮询可见

- **Given** 文档 `documentId=X` 当前状态为 `UPLOADED`
- **When** 调用 `POST /api/v1/file/textbooks/X/parse` 触发解析
- **Then** 状态变更为 `PARSING` 后**1 秒内**，通过 `GET /api/v1/file/textbooks?pageNum=1&pageSize=100` 查询该文档，`status` 字段为 `PARSING`（非 `UPLOADED`）
- **验证方式**: 集成测试 —— 调 `POST /parse/X` → 立即在新事务中 `GET /list` 查询同文档 → 断言 `status == "PARSING"`；轮询模拟测试 —— 在 `saveAndFlush(PARSING)` 之后、解析器执行之前，从另一个数据库连接查询，断言可见 PARSING

### AC-2 · LLM/MinerU/MinIO 操作不在事务内

- **Given** 文档处理管线中的慢操作包括：LLM 抽取调用（`ExtractionService.extract()`）、MinerU API 解析调用（通过 `TextbookParser.parse()`）、MinIO 文件读写
- **When** 这些操作执行时
- **Then** 当前线程不持有活跃的 JPA 事务（`TransactionSynchronizationManager.isActualTransactionActive() == false`）
- **验证方式**: ① 代码审查：grep 确认 `@Transactional` 方法体内不直接或间接调用 LLM/MinerU/MinIO；② 单元测试：mock LLM/MinerU，在调用点断言 `!TransactionSynchronizationManager.isActualTransactionActive()`

### AC-3 · JPA @Transactional 仅包裹纯 MySQL 操作

- **Given** 所有标注 `@Transactional` 的 public 方法
- **When** 审查这些方法的方法体
- **Then** 方法体内不存在以下调用：① `constructionGraphRepository.*` / `fusionGraphRepository.*` / `queryGraphRepository.*`（Neo4j Repository）；② `eventPublisher.publishEvent(...)`（事件发布）；③ 外部 API 调用（LLM/MinerU）
- **验证方式**: 代码审查 + ArchUnit 测试规则 —— `noClasses().that().areAnnotatedWith(Transactional.class).should().accessClassesThat().resideInAnyPackage("..neo4j..")` 作为指导性检查（部分 Neo4j 读操作可能仍可接受，需逐个评审）

### AC-4 · 事件发布不在事务内

- **Given** 所有 `ApplicationEventPublisher.publishEvent()` 调用点
- **When** 审查这些调用点的调用栈
- **Then** 调用发生时当前线程不持有活跃的 JPA 事务（即事件在事务提交后发布，或发布方方法本身不标注 `@Transactional`）
- **验证方式**: 代码审查 —— 搜索 `publishEvent(` 调用所在方法，确认方法签名上无 `@Transactional`；或搜索 `TransactionSynchronizationManager.registerSynchronization` 确认已消除

### AC-5 · 消除 afterCommit 手动回调

- **Given** 重构完成
- **When** 搜索 `TransactionSynchronizationManager.registerSynchronization` 和 `TransactionSynchronization`
- **Then** 在生产代码中无匹配（测试工具代码除外）
- **验证方式**: `grep -rn "TransactionSynchronizationManager.registerSynchronization\|new TransactionSynchronization" src/main/java/` 返回空

### AC-6 · @Transactional 注解规范统一

- **Given** 重构完成
- **When** 审查所有 `@Transactional` 注解的位置
- **Then** ① 所有 `@Transactional` 位于 L2 Service 的 **public** 方法上；② 不存在私有方法标注 `@Transactional`；③ 不存在 `@EventListener` 方法标注 `@Transactional`（`@TransactionalEventListener` 除外——若 DESIGN 决定使用）
- **验证方式**: `grep -B5 "@Transactional"` 检查每个注解前的方法签名是否为 `public`；`grep -rn "@Transactional" src/main/java/ | grep -v "public\|interface\|//"` 检查是否有非 public 标注

### AC-7 · FusionServiceImpl.updateLogFailed 修复

- **Given** 融合操作执行失败（Neo4j 事务回滚或业务异常）
- **When** `FusionServiceImpl.updateLogFailed()` 被调用
- **Then** `fusion_log` 表中对应记录的 `status` 字段为 `"FAILED"`（非 `"COMPLETED"`）
- **验证方式**: 单元测试 —— mock Neo4j 异常，调 `fuseFull()`，断言 `fusionLogRepository.save()` 参数中 `status == "FAILED"`；手动验证 —— 关闭 Neo4j 后调融合 API，查 `fusion_log` 表 status

### AC-8 · Neo4j 写入失败时 MySQL 状态回退补偿

- **Given** 文档处理管线中 MySQL 状态已提交为 `EXTRACTING`（短事务），随后 Neo4j 写入（图谱构建）抛出异常
- **When** 异常被捕获处理
- **Then** ① 开启新的短事务将文档状态从 `EXTRACTING` 回退到 `PARSED`（或前一个稳定态）；② `failReason` 字段记录具体失败原因（含异常 message，≤300 字符）；③ 前端轮询可观察到 `PARSED` 状态 + `failReason` 非空
- **验证方式**: 集成测试 —— mock `constructionGraphRepository.save()` 抛异常 → 断言 `document.status == PARSED && document.failReason != null`

### AC-9 · 既有功能等价回归（全量测试通过）

- **Given** 重构完成
- **When** 执行全量测试
- **Then** 所有既有单元测试 + 集成测试通过（`mvn test` 输出 `BUILD SUCCESS`，0 failures, 0 errors）；文档上传/解析/抽取/融合/删除、成绩上传/查询/删除、智能问答同步/异步功能行为不变
- **验证方式**: `mvn test` 全量绿色；手动 UAT —— 走通「上传 PDF → 解析 → 抽取 → 融合 → 智能问答 → 删除文档」完整链路；走通「上传成绩 → 条件查询 → 融合 → 删除成绩」完整链路

### AC-10 · 前后端状态轮询集成验证

- **Given** 前后端均运行，上传一个 PDF 文件
- **When** 前端自动触发解析 + 状态轮询（2s 间隔）
- **Then** 前端管线进度指示器依次展示：UPLOADED → PARSING（≤1s 可见）→ PARSED（解析完成后 ≤1s 可见）→ EXTRACTING（触发抽取后 ≤1s 可见）→ EXTRACTED → FUSING → COMPLETED；所有中间态均在前端 UI 中有对应视觉呈现
- **验证方式**: 手动 UAT —— 前端上传 PDF，观察 `StatusPipeline` 组件各阶段逐一点亮，不出现跳变

---

## 范围切分

### v1（本次必做）

- 文档处理管线（`TextbookServiceImpl.parse()` / `deleteTextBook()`）事务拆分：状态更新短事务 + 慢操作无事务
- 图谱构建管线（`ConstructionServiceImpl.extract()`）事务拆分：MySQL 状态更新短事务 + Neo4j 写入独立 + 事件移出事务
- 成绩处理模块（`GradeUploadService` / `GradeServiceImpl`）事务边界调整：事件移出事务
- 融合模块 `FusionServiceImpl.updateLogFailed()` 笔误修复
- `afterCommit` 手动回调消除（`TextbookServiceImpl` 两处）
- `@Transactional` 注解规范统一（public-only，EventListener 不标注）
- Neo4j 写入失败时的 MySQL 状态回退补偿（v1 覆盖文档路径；成绩路径重叠度低暂不覆盖）
- 全量既有测试回归通过

### v2（下一轮考虑，不本次）

- 异步融合引入消息队列做最终一致性（RabbitMQ 既有，当前仅预留）—— v1 通过同步补偿满足需求
- 成绩路径的 Neo4j 失败补偿（成绩图谱构建失败时 MySQL `exam_record` 已提交，v1 保持现状：失败仅记 log，需手动处理）
- `QueryServiceImpl` 私有 `@Transactional` 方法的 public 化重构（3 个辅助方法可能受 AOP 自调用影响，v1 仅标注为已知技术债，不做行为变更）
- 融合操作的事务隔离级别显式配置（如 Neo4j 事务超时/重试策略）
- 数据库连接池事务监控埋点

### out（永远不做）

- 引入分布式事务框架（Seata/Atomikos/XA）—— 项目规模与复杂度不匹配，跨存储最终一致性通过状态机补偿即可
- MySQL 隔离级别从 REPEATABLE_READ 降为 READ_COMMITTED —— 引入新的并发语义风险（不可重复读/幻读），且短事务方案已解决可见性问题
- Neo4j HTTP/Bolt 协议层面的两阶段提交 —— Neo4j 不支持 XA，技术上不可行
- 前端轮询改为 WebSocket 推送 —— 状态可见性已通过短事务解决，WebSocket 引入额外基础设施复杂度

---

## 非功能性需求

- **性能**: 文档处理管线拆分后单个文档的总处理时间（上传→COMPLETED）不超过重构前的 1.1 倍（允许 ≤10% 的额外开销来自多次短事务 commit）；短事务 commit 延迟 ≤50ms（HikariCP + MySQL 本地 podman）
- **可访问性**: 无（纯后端重构）
- **安全**: 不向客户端暴露内部事务状态或异常堆栈（维持既有 `GlobalExceptionHandler` 泛化策略）；`failReason` 字段截断 ≤300 字符
- **兼容性**: 无 API 契约变更；前端 `fileStore.ts` 轮询逻辑不变（无需修改）；所有端点路径、请求/响应体不变
- **可观测性**: 每次事务提交/回滚在 DEBUG 级别记录（含实体类型+ID+操作）；Neo4j 写入失败补偿时 WARN 级别记录补偿动作；维持既有的 traceId 关联

## 依赖与假设

- **依赖**: 无新增外部服务或库依赖；复用既有 Spring `@Transactional` + `Neo4jTransactionManager` + `TransactionTemplate`
- **假设**: 
  - MySQL 8.0 + InnoDB 默认 `REPEATABLE_READ`（已验证 podman 环境确为此隔离级别）
  - 短事务（状态更新）的 commit 在 podman 本地 MySQL 上 ≤50ms，不会成为性能瓶颈
  - `TextbookServiceImpl.parse()` 拆分为多个短事务方法后，MinerU API 调用失败（异常）的补偿逻辑（回写 `UPLOADED` + failReason）在新事务中执行，数据库连接仍然可用
  - `ConstructionServiceImpl.extract()` 拆分后，Neo4j 写入失败不影响已提交的 MySQL `EXTRACTING` 状态回退（补偿事务可独立开启）
  - 前端轮询间隔为 2s，短事务 commit 后 ≤1s 内可见（满足状态进度展示需求）
  - `TextbookParsedEventListener` 的 `saveFailReason()` 自调用问题与 `QueryServiceImpl` 私有 `@Transactional` 同为已知 AOP 自调用技术债，v1 不修改行为，仅代码注释标注

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。