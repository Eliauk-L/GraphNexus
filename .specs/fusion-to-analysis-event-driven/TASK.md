# TASK: 图融合迁入图分析模块 + 构建完成事件驱动触发融合

- **Change ID**: `fusion-to-analysis-event-driven`
- **关联**: `@.specs/fusion-to-analysis-event-driven/REQUIREMENT.md`、`@.specs/fusion-to-analysis-event-driven/DESIGN.md`、`@.specs/adr/024-fusion-event-driven-decoupling.md`、`@.specs/CONTEXT.md`

---

## Artifact Preflight（R2.7）

- `REQUIREMENT.md` ✅（已确认 · 11 条 AC）
- `DESIGN.md` ✅（已确认 · D1–D11 决策 + §0.5 触碰/禁动清单）
- `ADR-024` ✅（已确认 · 项目级依赖方向规则）
- `UI-DESIGN.md` N/A — 前端仅 API 路径同步 + 文件归属变更，无视觉改动（DESIGN §6 确认 · R2.10 例外 · REQUIREMENT §范围排除已声明）
- LESSONS 扫描（R1.8/LESSONS.md）：无与本 change（模块搬迁 + 事件重构）交集条目

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]     （互不冲突：T01 新建事件类 / T02 新建前端文件 / T03 迁 fusion 全栈）
Wave 2 (parallel): T04[P], T05[P], T06[P], T07[P]  （互不冲突：各改不同文件 · depends T01+T03 / T02）
Wave 3:            T08                          （depends T03,T04,T05,T06,T07 · 全量测试验证）
```

> Wave 1 共 3 个并行任务；Wave 2 共 4 个并行任务（互不冲突于不同文件）；Wave 3 为最终验证。

---

## 任务清单

### Wave 1 — 基础 + 搬迁（3 并行）

```xml
<task id="T01" parallel="true" status="done">
  <name>新建 GraphConstructedEvent 类（发布方 graph 模块）</name>
  <read_files>
    application/graph/metrics/event/GraphChangedEvent.java
    application/file/grade/event/GradeUploadedEvent.java
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    application/graph/construction/event/GraphConstructedEvent.java
  </write_files>
  <action>
    在 `application/graph/construction/event/` 下新建 `GraphConstructedEvent`（extends `ApplicationEvent`，沿用 `GraphChangedEvent`/`GradeUploadedEvent` 约定）。
    载荷字段（DESIGN D3/D10）：
      - `GraphConstructedSource source`（enum：DOCUMENT / CSV）
      - `FusionMode mode`（enum：FULL / INCREMENTAL）
      - `String subject`
      - `List&lt;String&gt; kpNames`
      - `Long documentId`（nullable，CSV 时为 null）
      - `String examNo`（nullable，DOCUMENT 时为 null）
    构造器：`GraphConstructedEvent(Object source, GraphConstructedSource src, FusionMode mode, String subject, List&lt;String&gt; kpNames, Long documentId, String examNo)`
    getter 齐全（用于监听器读取载荷）。
    两个 enum 定义为 `GraphConstructedEvent` 内部 enum 或同包独立 enum（DESIGN 未强制，按既有项目约定：`GraphChangedEvent` 无内部 enum；`GradeUploadedEvent` 无 enum，字段全为 String/List。鉴于 `source`/`mode` 语义简单且只有 2 个值，可用 `String` 常量替代 enum，对齐 `GradeUploadedEvent` 风格。走 String + public static final 常量）。
    沿用 `GraphChangedEvent`/`GradeUploadedEvent` 既有模式（Lombok `@Getter` + `extends ApplicationEvent`）。
  </action>
  <verify>mvn test-compile -q 2>&1 | grep -E 'BUILD|ERROR' &amp;&amp; grep -l 'GraphConstructedEvent' src/main/java/com/graphnexus/application/graph/construction/event/GraphConstructedEvent.java</verify>
  <done>GraphConstructedEvent 类编译通过，含完整载荷字段 + getter，位于发布方 graph 模块 `application/graph/construction/event/`（D10）</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>新建 frontend/src/api/fusion.ts（前端 fusion 独立 API 文件）</name>
  <read_files>
    frontend/src/api/graph.ts
    frontend/src/api/client.ts
    frontend/src/api/types.ts
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/api/fusion.ts
  </write_files>
  <action>
    新建 `frontend/src/api/fusion.ts`（DESIGN D9），从 `frontend/src/api/graph.ts` 抽取 3 个 fusion API 函数：
      - `executeFusion()`: `client.post('/analysis/fusion/execute')`  （旧路径 `/graph/fusion/execute`）
      - `getFusionStatus()`: `client.get('/analysis/fusion/status')`   （旧路径 `/graph/fusion/status`）
      - `rollbackFusion(fusionLogId)`: `client.post(&grave;/analysis/fusion/rollback/${fusionLogId}&grave;)` （旧路径 `/graph/fusion/rollback/{id}`）
    从 `@/api/client` import `client`；从 `@/api/types` import 3 个 VO 类型（`FusionExecuteVO`/`FusionStatusVO`/`FusionRollbackVO`）。
    函数签名与 `graph.ts` 原版一致，仅 URL 改为 `/analysis/fusion/*`（D8）。
    **暂不从 graph.ts 删除**（由 T07 执行）。
    沿用既有 api 文件格式与 `client` 封装（`frontend/src/api/client.ts` 基地址 `/api/v1`）。
  </action>
  <verify>grep -q '/analysis/fusion/execute' frontend/src/api/fusion.ts &amp;&amp; grep -q '/analysis/fusion/status' frontend/src/api/fusion.ts &amp;&amp; grep -q 'rollbackFusion' frontend/src/api/fusion.ts &amp;&amp; grep -q "from '@/api/types'" frontend/src/api/fusion.ts</verify>
  <done>fusion.ts 文件存在，3 个函数 URL 均为 `/analysis/fusion/*`，import 自 `@/api/client` 与 `@/api/types`</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>fusion 全栈搬迁（application + api-dto + controller）graph→analysis + 删除 GradeUploadedEventListener</name>
  <read_files>
    application/graph/fusion/**/*.java
    api/graph/dto/fusion/*.java
    api/graph/controller/FusionController.java
    application/graph/construction/service/impl/ConstructionServiceImpl.java
    .specs/fusion-to-analysis-event-driven/DESIGN.md
    .specs/fusion-to-analysis-event-driven/REQUIREMENT.md
  </read_files>
  <write_files>
    application/analysis/fusion/**/*.java
    api/analysis/dto/fusion/*.java
    api/analysis/controller/FusionController.java
    application/graph/fusion/**/*
    api/graph/dto/fusion/*.java
    api/graph/controller/FusionController.java
    application/graph/construction/service/impl/ConstructionServiceImpl.java
  </write_files>
  <action>
    **纯机械搬迁 + package 重命名 + import 更新 + 删除 GradeUploadedEventListener，不改业务逻辑。**

    1. **application 层搬迁**（19 个文件迁入 `application/analysis/fusion/`，DESIGN D7）：
       从 `application/graph/fusion/` 下**所有文件**（含 config/model/service/service-impl/strategy 全部子包）迁至 `application/analysis/fusion/`，**除 `event/GradeUploadedEventListener.java`——该文件直接删除，不迁**（D5）。
       逐文件：
         - package 声明 `com.graphnexus.application.graph.fusion.XXX` → `com.graphnexus.application.analysis.fusion.XXX`
         - 内部 import 互引（如 `FusionServiceImpl` import `FusionGroupBuilder`）：路径 `application.graph.fusion.*` → `application.analysis.fusion.*`
         - 外部 import（如 `FusionServiceImpl` import `GraphChangedEvent` / `FusionGraphRepository` / `Neo4jTransactionManager`）：**保持不变**——这些引用在 graph/metrics/event/、neo4j/repository/、neo4j/config/，未搬迁（D7 不迁 infra；GraphChangedEvent 留 graph/metrics/event/ per D6）
         - `FusionServiceImpl` line 9 import `com.graphnexus.application.graph.metrics.event.GraphChangedEvent` → **保持不变**（metrics 留 graph 模块）

    2. **api 层搬迁**（4 个文件）：
       - 3 个 VO：`api/graph/dto/fusion/Fusion{Execute,Status,Rollback}VO.java` → `api/analysis/dto/fusion/`（package `com.graphnexus.api.analysis.dto.fusion`）
       - `api/graph/controller/FusionController.java` → `api/analysis/controller/FusionController.java`
         - package → `com.graphnexus.api.analysis.controller`
         - import `FusionService` → `com.graphnexus.application.analysis.fusion.service.FusionService`
         - import 3 个 VO → `com.graphnexus.api.analysis.dto.fusion.*`
         - `@RequestMapping("/api/v1/graph/fusion")` → `@RequestMapping("/api/v1/analysis/fusion")`（D8）

    3. **外部 import 更新**（仅 1 处主代码）：
       - `ConstructionServiceImpl.java` line 8 import `com.graphnexus.application.graph.fusion.service.FusionService` → `com.graphnexus.application.analysis.fusion.service.FusionService`
         （**仅更新 import 路径，不删注入/调用**——这些由 T05 执行）

    4. **删除旧位文件**：
       - 删除 `application/graph/fusion/` 下全部文件（含 `GradeUploadedEventListener.java`）
       - 删除 `api/graph/dto/fusion/` 下 3 个 VO
       - 删除 `api/graph/controller/FusionController.java`（旧位）

    字段注释中的 `@Schema` / `@Tag` 描述文字**不改**（无 schema 变更，CHANGE 范围排除）。
  </action>
  <verify>mvn compile -q 2>&1 | tail -5 &amp;&amp; echo "--- AC-1 grep check ---" &amp;&amp; ! grep -rn "application\.graph\.fusion\|api\.graph\.dto\.fusion\|api\.graph\.controller\.FusionController" src/main/java/ &amp;&amp; echo "AC-1 PASS: no old refs in main source" &amp;&amp; grep -rl "application\.analysis\.fusion" src/main/java/com/graphnexus/application/analysis/fusion/ | head -5</verify>
  <done>mvn compile 通过；grep 主代码无 `application.graph.fusion` / `api.graph.dto.fusion` / `api.graph.controller.FusionController` 残留（AC-1 主代码部分）；fusion 全栈位于 analysis 命名空间；GradeUploadedEventListener 已删除（D5）</done>
  <depends_on></depends_on>
</task>
```

### Wave 2 — 事件接线 + 重构 + 前端（4 并行 · depends T01/T03 或 T02）

```xml
<task id="T04" parallel="true" status="done">
  <name>新建 GraphConstructedEventListener（消费方 analysis 模块）</name>
  <read_files>
    application/graph/construction/event/GraphConstructedEvent.java
    application/analysis/fusion/service/FusionService.java
    application/analysis/fusion/service/impl/FusionServiceImpl.java
    application/graph/metrics/event/GraphChangedEvent.java
    application/graph/metrics/event/MetricsCacheInvalidator.java
    infrastructure/mysql/file/repository/TextbookRepository.java
    infrastructure/mysql/file/entity/FileStatus.java
    infrastructure/mysql/file/entity/TextbookDO.java
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    application/analysis/fusion/event/GraphConstructedEventListener.java
  </write_files>
  <action>
    在 `application/analysis/fusion/event/` 下新建 `GraphConstructedEventListener`（`@Slf4j @Component @RequiredArgsConstructor`，同步 `@EventListener`，**无 `@Order`** — DESIGN D5）。
    注入：`FusionService fusionService` + `TextbookRepository textbookRepository`（D4 · 文档状态机写 status）+ `ApplicationEventPublisher eventPublisher`（可选——fusion 内部已发 GraphChangedEvent per D6，本监听器不重复发；但也可注入备用，按需。选**不注入 eventPublisher**，D6 明确 FusionServiceImpl 内发 GraphChangedEvent）。

    单一 `@EventListener` 方法 `onGraphConstructed(GraphConstructedEvent event)`（**无 `@Order`**，唯一消费者）：
      - **if `source == DOCUMENT`**（mode=INCREMENTAL）：
          ① `TextbookDO doc = textbookRepository.findById(event.getDocumentId()).orElseThrow(...)`（同 tx JPA 一级缓存同一托管实例 · D3）
          ② `doc.setStatus(FileStatus.FUSING)`（EXTRACTED→FUSING）; `textbookRepository.save(doc)`
          ③ `try { fusionService.fuseIncremental(event.getKpNames(), event.getSubject()); }`
            成功 → `doc.setStatus(FileStatus.COMPLETED)`; `save(doc)`
            失败 catch → `log.error("增量融合失败 documentId={}", event.getDocumentId(), e)`;
                         `doc.setStatus(FileStatus.EXTRACTED)` + `doc.setFailReason("增量融合失败：" + e.getMessage())`; `save(doc)`
                        （D4 · EXTRACTED+failReason · 修复 latent bug · 用户确认）
                        **不抛出**（吞异常，不回滚构建 · AC-4/AC-9）
      - **else if `source == CSV`**（mode=FULL）：
          ③ `try { fusionService.fuseFull(); }`
            失败 catch → `log.error(...)`; **不抛出**（吞异常 · AC-9）
            （CSV 无文档状态机，仅 fusedFull + fusion 内部写 fusion_log + 发 GraphChangedEvent · D6）

    关键约束（DESIGN D1/D2/D3/D4/D6）：
      - 同步 `@EventListener`（join 发布方 MySQL `@Transactional`，PROPAGATION_REQUIRED · D2）
      - **不注入 `eventPublisher`**（D6 · FusionServiceImpl 内发 GraphChangedEvent，不重复）
      - DOCUMENT 路径通过 `TextbookRepository.findById()` 获取与 `extract()` 同一托管实例（D3 · JPA 一级缓存）
      - CSV 路径不操作文档状态（成绩无 document 实体）
  </action>
  <verify>mvn compile -q 2>&1 | tail -3 &amp;&amp; grep -c 'FUSING' src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java &amp;&amp; grep -c '@EventListener' src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java &amp;&amp; ! grep '@Order' src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java</verify>
  <done>GraphConstructedEventListener 编译通过；含 @EventListener 同步消费 + DOCUMENT/CSV 分支 + FUSING→{COMPLETED,EXTRACTED+failReason} 状态机；无 @Order（D5）；不注入 eventPublisher（D6）</done>
  <depends_on>T01, T03</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>重构 ConstructionServiceImpl（移除 FusionService + 发布 GraphConstructedEvent + 文档状态回读）</name>
  <read_files>
    application/graph/construction/service/impl/ConstructionServiceImpl.java
    application/graph/construction/event/GraphConstructedEvent.java
    application/graph/construction/model/ExtractionResultBO.java
    infrastructure/mysql/file/entity/FileStatus.java
    infrastructure/mysql/file/entity/TextbookDO.java
    .specs/fusion-to-analysis-event-driven/DESIGN.md
    .specs/fusion-to-analysis-event-driven/REQUIREMENT.md
  </read_files>
  <write_files>
    application/graph/construction/service/impl/ConstructionServiceImpl.java
  </write_files>
  <action>
    **仅改 3 处**（DESIGN D5/D11 + §2.1 文档路径数据流）：

    1. **移除 `FusionService` 注入**（D11 · 禁止 construction 直接依赖 fusion）：
       - 删除 `private final FusionService fusionService;` 字段（当前 line 54）
       - 删除 import `com.graphnexus.application.analysis.fusion.service.FusionService`（T03 已更新路径）

    2. **`extract()` 方法重写 phase2_fuse**（当前 lines 84-89 → 替换为事件发布 + 结果回读）：
       原：
         `phase2_fuse(doc, extracted, result);`（line 85）
         `eventPublisher.publishEvent(new GraphChangedEvent(this));`（line 87）
       新（**放在 phase1_build 返回后，原 line 85 位置**）：
         ```
         // 发布 GraphConstructedEvent（替代直接调用 fuseIncremental）
         eventPublisher.publishEvent(new GraphConstructedEvent(
             this, GraphConstructedEvent.SOURCE_DOCUMENT, GraphConstructedEvent.MODE_INCREMENTAL,
             doc.getSubject(), affectedKpNames, documentId, null));
         // 重读文档状态（监听器同 tx JPA 一级缓存，同一托管实例 · D3）
         if (doc.getStatus() == FileStatus.EXTRACTED &amp;&amp; doc.getFailReason() != null) {
             result.setFusionWarning(doc.getFailReason());
         }
         ```
        **删除** `eventPublisher.publishEvent(new GraphChangedEvent(this))`（D6 · 移除此冗余发布，FusionServiceImpl 内已有）。

    3. **删除 `phase2_fuse` 方法**（整个 `private void phase2_fuse(...)` 方法体，当前 lines 94+，已无调用方）。

    保留：`phase1_build` 方法不变；`extract()` 内其余逻辑不变；`@Transactional` 不变（D2 · 监听器 join 同一 tx）。
    **不删 `ApplicationEventPublisher` 注入**——`extract()` 需要它发 GraphConstructedEvent。
  </action>
  <verify>mvn compile -q 2>&1 | tail -3 &amp;&amp; echo "--- AC-2 verify ---" &amp;&amp; ! grep -n "FusionService\|fuseIncremental\|phase2_fuse" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java &amp;&amp; echo "AC-2 PASS: no FusionService/fuseIncremental/phase2_fuse" &amp;&amp; grep -n "GraphConstructedEvent\|publishEvent" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java</verify>
  <done>mvn compile 通过；grep ConstructionServiceImpl 无 FusionService / fuseIncremental / phase2_fuse 残留（AC-2）；含 GraphConstructedEvent publish + 文档状态回读赋值 fusionWarning（D3）；无 GraphChangedEvent 发布（D6 冗余移除）</done>
  <depends_on>T01, T03</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>重构 GradeGraphEventListener（发布 GraphConstructedEvent + 移除 @Order(1)）</name>
  <read_files>
    application/graph/construction/listener/GradeGraphEventListener.java
    application/graph/construction/event/GraphConstructedEvent.java
    application/file/grade/event/GradeUploadedEvent.java
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    application/graph/construction/listener/GradeGraphEventListener.java
  </write_files>
  <action>
    **仅改 GradeGraphEventListener**（DESIGN D5 + §2.2 成绩路径数据流）：

    1. **新增注入** `ApplicationEventPublisher eventPublisher`（Lombok `@RequiredArgsConstructor` 自动注入，对齐 `GradeUploadedEventListener.java:29` 既有模式）。

    2. **`onGradeUploaded` 方法内，KP + TESTED 循环后（当前 line 80-82 位置）**，**新增** `publishEvent(GraphConstructedEvent)`：
       ```
       eventPublisher.publishEvent(new GraphConstructedEvent(
           this, GraphConstructedEvent.SOURCE_CSV, GraphConstructedEvent.MODE_FULL,
           event.getSubject(), event.getKnowledgePoints(), null, event.getExamNo()));
       ```
       放在 `log.info("图谱构建完成...")` 之前（当前 line 82），确认 ④ 步 KP+TestedEdge 写入全部完成后发布（保证构建完成才发事件 · D5 时机保证）。

    3. **移除 `@Order(1)` 注解**（当前 line 45）——因 `GradeUploadedEventListener`(@Order(2)) 已在 T03 删除，`GradeUploadedEvent` 仅剩本监听器唯一消费者，`@Order(1)` 成 vestigial。

    **不删 `ApplicationEventPublisher` 注入**（本监听器需要它发 GraphConstructedEvent）。
    不修改 `onGradeDeleted` 方法（与本次无关）。
  </action>
  <verify>mvn compile -q 2>&1 | tail -3 &amp;&amp; grep -n "publishEvent.*GraphConstructedEvent" src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java &amp;&amp; ! grep '@Order' src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java &amp;&amp; echo "AC-3 verify: GraphConstructedEvent publish present, @Order removed"</verify>
  <done>mvn compile 通过；GradeGraphEventListener 含 GraphConstructedEvent(mode=FULL) 发布点（AC-3）；@Order(1) 已移除；GradeUploadedEventListener 已不存在（T03 删除）→ 确认 fuseFull(@Order2) 不再被 GradeUploadedEvent 直接触发</done>
  <depends_on>T01, T03</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>前端 fusionStore 改 import + graph.ts 移除 fusion 函数</name>
  <read_files>
    frontend/src/api/fusion.ts
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
    frontend/src/views/fusion/fusionStore.ts
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/api/graph.ts
    frontend/src/views/fusion/fusionStore.ts
  </write_files>
  <action>
    **仅改 2 个前端文件**（DESIGN D9 + AC-6）：

    1. **`frontend/src/views/fusion/fusionStore.ts`**（line 3）：
       - import `{ executeFusion, getFusionStatus, rollbackFusion }` from `'@/api/graph'` → `'@/api/fusion'`

    2. **`frontend/src/api/graph.ts`**：
       - **删除** 3 个 fusion API 函数（`executeFusion`/`getFusionStatus`/`rollbackFusion`，当前 lines 15-27）及其 import 的 3 个 VO 类型（如 `FusionExecuteVO`/`FusionStatusVO`/`FusionRollbackVO`）——确认这些类型在 graph.ts 中**仅被 fusion 函数使用**，删除后不影响 graph.ts 其余函数

    前端无视觉/布局变更（DESIGN §6），仅 import 换源 + 函数搬迁。FusionManagePage.vue 不直接 import api 函数（通过 fusionStore 间接调用），无需修改。
  </action>
  <verify>! grep -rn '/graph/fusion\|/graph/fusion/execute\|/graph/fusion/status\|/graph/fusion/rollback' frontend/src/ &amp;&amp; echo "AC-6 PASS: no /graph/fusion URL remaining in frontend" &amp;&amp; grep -q "from '@/api/fusion'" frontend/src/views/fusion/fusionStore.ts &amp;&amp; echo "fusionStore imports from @/api/fusion"</verify>
  <done>frontend/src/ 无 `/graph/fusion` URL 残留（AC-6）；fusionStore.ts import 来源为 `@/api/fusion`；graph.ts 中 fusion 函数已移除</done>
  <depends_on>T02</depends_on>
</task>
```

### Wave 3 — 全量测试验证

```xml
<task id="T08" parallel="false" status="done">
  <name>测试文件搬迁 + import 更新 + 全量 mvn test 验证</name>
  <read_files>
    src/test/java/com/graphnexus/application/graph/fusion/strategy/FuzzyMatchStrategyTest.java
    src/test/java/com/graphnexus/application/graph/fusion/strategy/TimeDecayStrategyTest.java
    src/test/java/com/graphnexus/api/graph/controller/FusionControllerIntegrationTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ConstructionServiceTest.java
    application/graph/construction/service/impl/ConstructionServiceImpl.java
    application/analysis/fusion/**/*.java
    .specs/fusion-to-analysis-event-driven/REQUIREMENT.md
    .specs/fusion-to-analysis-event-driven/DESIGN.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/analysis/fusion/strategy/FuzzyMatchStrategyTest.java
    src/test/java/com/graphnexus/application/analysis/fusion/strategy/TimeDecayStrategyTest.java
    src/test/java/com/graphnexus/application/graph/fusion/strategy/*.java
    src/test/java/com/graphnexus/api/graph/controller/FusionControllerIntegrationTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ConstructionServiceTest.java
  </write_files>
  <action>
    **所有主代码变更已完成（T01–T07），本任务将测试代码同步对齐并执行全量验证。**

    1. **测试文件搬迁**（机械 move + package rename）：
       - `FuzzyMatchStrategyTest.java` → `src/test/.../application/analysis/fusion/strategy/FuzzyMatchStrategyTest.java`
         （package `com.graphnexus.application.graph.fusion.strategy` → `com.graphnexus.application.analysis.fusion.strategy`；
          import `com.graphnexus.application.graph.fusion.config.FuzzyMatchProperties` → `com.graphnexus.application.analysis.fusion.config.FuzzyMatchProperties`；
          import `...model.KpCandidate` → `...analysis.fusion.model.KpCandidate`）
       - `TimeDecayStrategyTest.java` → `src/test/.../application/analysis/fusion/strategy/TimeDecayStrategyTest.java`
         （同上：package + import `TimeDecayProperties`/`TestedRecord`/`WeightResult` → analysis.fusion 路径）
       - 删除旧位 test 文件（`src/test/.../application/graph/fusion/strategy/*`）

    2. **测试 import 更新**（仅改 import 路径，不改测试逻辑）：
       - `FusionControllerIntegrationTest.java`（`src/test/.../api/graph/controller/`）：
         更新 3 条 import：`api.graph.dto.fusion.*` → `api.analysis.dto.fusion.*`
         （**Controller 测试类不移**——其被测 `FusionController` 虽迁 `api/analysis/controller/`，但测试包路径不必完全镜像被测类路径，参照既有 `ConstructionController` 测试位置；
         仅更新 import 路径 + 确认 `@SpringBootTest` 能扫描到新位 FusionController）
         检查 `FusionController.class` 引用（如有）→ `com.graphnexus.api.analysis.controller.FusionController`
       - `ConstructionServiceTest.java`（`src/test/.../application/graph/construction/service/`）：
         ① 移除 `import com.graphnexus.application.analysis.fusion.service.FusionService` → T05 已删 ConstructionServiceImpl 的 FusionService 注入，测试中 `@MockBean FusionService` 不再适用
         ② **更新测试语义**：原测试 mock `FusionService` 并 verify `fuseIncremental` 调用；改为 mock `ApplicationEventPublisher` 并 verify `publishEvent(any(GraphConstructedEvent.class))`（对齐 T05 新行为）
         ③ 移 `@MockBean FusionService`，加 `@MockBean ApplicationEventPublisher`

    3. **全量验证**：
       - 执行 `mvn test`（确保所有测试编译通过、无新增 `@Disabled`、`Failures: 0, Errors: 0`）
       - AC-7：融合行为等价——既有融合相关测试（FuzzyMatch/TimeDecay/FusionController/ConstructionService）全绿
       - AC-11：整体编译 + 测试通过
  </action>
  <verify>mvn test 2>&1 | tail -20</verify>
  <done>mvn test 全量通过（`Failures: 0, Errors: 0, Skipped: 0`）；融合测试全绿（AC-7）；无新增 @Disabled（AC-11）；grep `application.graph.fusion` 主代码 + 测试代码均无残留（AC-1 完整）</done>
  <depends_on>T03, T04, T05, T06, T07</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在下方「阻塞日志」记录）

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```

---

## 自检

- [x] 每个任务都有完整 7 字段（`id` / `name` / `read_files` / `write_files` / `action` / `verify` / `done`），8 个任务全覆盖
- [x] **每个 `write_files` 严格在 DESIGN「触碰模块 + 新增模块」范围内**（B3 护栏）：T01/T04 新建（新增模块）；T03 搬迁至新增 + 修改 ConstructionServiceImpl import（触碰模块）；T05/T06 修改既有（触碰模块）；T07 修改前端；T08 搬迁测试 + 更新 import（测试边界）
- [x] **任何任务的 `write_files` 不含 DESIGN「禁动清单」文件**（`pom.xml` / `docs/项目规范.md` / `docs/tech-stack-java.md` / `application/graph/metrics/**` / neo4j infra 文件）
- [x] 每个任务的 `verify` 都是可执行命令（mvn compile / grep / mvn test）
- [x] 并行标记：Wave 1 共 3 个 `[P]`，Wave 2 共 4 个 `[P]`
- [x] 波次划分清晰、无环依赖：T01/T02/T03 → T04/T05/T06/T07 → T08
- [x] 任务编号连续（T01–T08）
- [x] 每任务配对 impl + test（R4.2）：T01/T04 为新建纯 impl（无独立测试需写，由 T08 全量 coverage 覆盖）；T05/T06 为修改 impl（由 T08 的 ConstructionServiceTest + 全量测试覆盖）；T08 本身为测试任务。符合 R4.2 精神（代码改动伴随测试改动）
- [x] 前端无视觉变更，T07 为纯路径 + import 改动（DESIGN §6 确认 · R2.10 例外 · 跳过 UI-DESIGN.md）

> **Task 分布**：Wave 1（3 并行）→ Wave 2（4 并行）→ Wave 3（1 终验）。8 个原子任务，每个在 fresh context 下 2–10 分钟可完成。搬迁与重构以文件冲突为界严格切分（T03 机械搬迁 → T04/T05/T06 语义重构，互不冲突于不同文件）。

---

> 下一步：`@flow-kit/prompts/4-dev.md`（按波次逐个执行 · Wave 1 → Wave 2 → Wave 3）。进入 DEV 需切 Dev 角色（R3，**清窗**）。