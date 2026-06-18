# REVIEW: 图谱节点指标度量（PageRank + 度中心性）

- **Change ID**: graph-metrics
- **关联**: `@.specs/graph-metrics/REQUIREMENT.md`、`@.specs/graph-metrics/DESIGN.md`、`@.specs/graph-metrics/TEST.md`
- **审查范围**: 13 个生产文件 + 5 个测试文件, 351 insertions, 5 deletions
- **项目类型**: 后端 API → 跳过第三轮 UI 审查

---

## 第一轮 · Spec 合规审查

### AC 实现检查

| AC | 实现位置 | 测试覆盖 | 判定 |
|----|---------|---------|------|
| AC-1 PageRank | `MetricsController.getPageRank()` → `MetricsServiceImpl.queryPageRank()` → `GdsAdapter.calculatePageRank()` | `GdsAdapterTest` + `MetricsControllerTest` + UAT curl | ✅ 通过 |
| AC-2 度中心性 | `MetricsController.getDegree()` → `GdsAdapter.calculateDegree()` (NATURAL + REVERSE) | `MetricsServiceTest.testQueryDegree_ReturnsBothDirections()` + UAT | ✅ 通过 |
| AC-3 过滤投影 | `MetricsQuery.nodeTypes/edgeTypes` → `GdsAdapter.buildNodeProjection/buildRelationshipProjection` | `GdsAdapterTest.testProjection_AllNodesWildcard` + UAT curl | ✅ 通过 |
| AC-4 多边组合 | `MetricsQuery.toCacheKey()` 按参数组合产生不同 key | `MetricsQueryTest` (4 tests) | ✅ 通过 |
| AC-5 空图→[] | `GdsAdapter.fetchResults()` 异常 catch 返回空列表 | `MetricsControllerTest.testGetPageRank_EmptyGraph...` + UAT curl | ✅ 通过 |
| AC-6 自动重算 | `GraphChangedEvent` + `MetricsCacheInvalidator` + 4 个发布点 (GraphServiceImpl/GradeServiceImpl/FusionServiceImpl×2) | `MetricsCacheInvalidatorTest` + UAT curl | ✅ 通过 |
| AC-7 幂等性 | `Caffeine Cache.get(key, loader)` 语义 | `MetricsServiceTest.testCache_Hit` + UAT R1=R2 | ✅ 通过 |
| AC-8 参数校验 | `MetricsServiceImpl.validateParams()` 用 `NodeType.fromLabel/EdgeType.fromType` | `MetricsServiceTest` (2 tests) + UAT 400 | ✅ 通过 |

### 范围蔓延检查

- [x] 是否引入了 `out of scope` 排除了的内容？ **否** — 未实现 v2 指标（介数等）、未写回节点属性、未集成到智能问答
- [x] 是否新增了 REQUIREMENT.md 里没有的功能？ **否** — 14 个新增文件完全对应 TASK.md 定义的 8 个任务
- [x] 是否触动了 DESIGN.md 之外的架构？ **否** — 所有新增代码在 DESIGN §0.5.1 声明的范围内

### 第一轮结论：✅ 通过，无 Critical 项

---

## 第二轮 · 代码质量审查（6 维衰退风险）

> brooks-lint 未安装 → 内置快查

### R1 · Cognitive Overload 认知过载

#### 🟡 R1-1 · `GdsAdapter.java` 方法职责混合

- **Symptom**: [GdsAdapter.java:170-213](src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java#L170-L213) `buildNodeProjection` / `buildRelationshipProjection` / `fetchResults` 三个方法内联了 Cypher 字符串拼接 + 结果映射 + 排序，三种职责混在同一方法链
- **Source**: McConnell · Code Complete · Ch.7 "High-Quality Routines" — 一个 routine 应只做一件事
- **Consequence**: 新增 GDS 算法（如 v2 介数中心性）时需要复制整段 fetch → map → sort 逻辑，增加维护成本
- **Remedy**: 将 `fetchResults` 的结果映射逻辑抽为独立的 `mapRowToMetricResult(Map row, String metricName)` 方法；排序用 `Comparator.comparing` 已在 `fetchResults` 末尾完成，暂可接受

#### 🟢 R1-2 · `MetricsController` getPageRank/getDegree 代码重复但可接受

- **Symptom**: [MetricsController.java:41-46](src/main/java/com/graphnexus/api/graph/controller/MetricsController.java#L41-L46) 和 [MetricsController.java:66-71](src/main/java/com/graphnexus/api/graph/controller/MetricsController.java#L66-L71) 中 Request 构建逻辑重复
- **Source**: Fowler · Refactoring · "Extract Method"
- **Consequence**: 低 — 仅 6 行重复，后续新增端点时才需关注
- **Remedy**: 可抽 `buildRequest(List, List)` 私有方法，但当前不阻塞

### R2 · Change Propagation 变更传播

#### 🟡 R2-1 · 事件发布点硬编码在 3 个既有 Service 中

- **Symptom**: [GraphServiceImpl.java:116](src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java#L116)、[GradeServiceImpl.java:173](src/main/java/com/graphnexus/application/document/service/impl/GradeServiceImpl.java#L173)、[FusionServiceImpl.java:87+135](src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java#L87) — 事件发布嵌入在具体 Service 方法中
- **Source**: Gamma et al. · Design Patterns · "Observer" — 发布方与消费方应解耦
- **Consequence**: 若未来新增图变更源（如批量导入），需记得在每个新位置加 `publishEvent`。ADR-013 已声明此约定，但缺乏编译期强制
- **Remedy**: v2 可考虑 AOP 切面（`@AfterReturning` on `@GraphMutation` 注解）自动发布事件，但 v1 手工钩子够用。**当前不阻塞，记入技术债**

#### 🟢 R2-2 · `buildNodeProjection` 和 `buildRelationshipProjection` 的标签拼接用 `String.format` 无参数化

- **Symptom**: [GdsAdapter.java:121-128](src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java#L121-L128) — 标签名通过字符串拼接注入 Cypher
- **Source**: OWASP · "SQL/Cypher Injection Prevention"
- **Consequence**: 低 — nodeTypes/edgeTypes 已经过 `NodeType.fromLabel()` 枚举白名单校验（`MetricsServiceImpl.validateParams`），非法值在到达 GdsAdapter 前被拦截（HTTP 400）。**注入风险已缓解**
- **Remedy**: 当前白名单校验充分，不要求修改

### R3 · Knowledge Duplication 知识重复

#### ✅ 无重大概念级重复

- 边方向映射 (`ORIENTATION_MAP`) 在 `GdsAdapter` 中集中定义一处
- 参数校验规则 (`validateParams`) 在 `MetricsServiceImpl` 中集中定义一处
- 缓存策略 (TTL/maxSize) 在 `MetricsProperties` 集中定义，通过 yml 绑定

#### 🟢 R3-1 · `MetricsQueryRequest` 类注释中残留 "转为大写去重" 描述

- **Symptom**: [MetricsQueryRequest.java:28](src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java#L28) 和 [MetricsQueryRequest.java:40](src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java#L40) 注释说 "转为大写去重的 Set" 但代码已移除 `toUpperCase()`
- **Source**: Fowler · Refactoring · "Comments" — 注释与代码不一致即为债务
- **Consequence**: 极低 — 误导后续维护者
- **Remedy**: 更新注释为 "去重的 Set（保留原始大小写，由 MetricsServiceImpl 归一化为规范标签）" → **🟢 Minor，下个 commit 顺手修**

### R4 · Accidental Complexity 偶然复杂

#### 🟡 R4-1 · `calculateDegree` 对同一命名图两次 stream 调用

- **Symptom**: [GdsAdapter.java:82-94](src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java#L82-L94) — `calculateDegree` 先 `runDegreeStream(NATURAL)` 再 `runDegreeStream(REVERSE)`，两次 stream 共享同一命名图
- **Source**: Brooks · "No Silver Bullet" — accidental complexity: GDS 本身不支持一次调用同时返回 in/out degree
- **Consequence**: 合理的复杂性 — GDS 限制导致必须两次调用。当前实现清晰注释了原因
- **Remedy**: 不需要修改。GDS 限制，已最小化影响

#### 🟢 R4-2 · `MetricsServiceImpl.queryDegree` 两个 cache.get 调用

- **Symptom**: [MetricsServiceImpl.java](src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java) — `queryDegree` 分别调用 `cache.get(inDegree)` 和 `cache.get(outDegree)`，这意味着同一投影参数的 in/out degree 占用两个独立缓存条目
- **Consequence**: 极低 — 缓存条目数翻倍但实际查询的投影组合少（< 50 个）
- **Remedy**: 可优化为单次缓存条目包含两个方向的结果，但当前不需要

### R5 · Dependency Disorder 依赖混乱

#### ✅ 依赖方向正确

- **L1 (MetricsController)** → **L2 (MetricsService)** → **L3 (GdsAdapter)**: ✅ 四层架构单向依赖
- **L2 event (GraphChangedEvent/MetricsCacheInvalidator)** → **L2 service (MetricsService)**: ✅ 同层委托，不跨层
- **既有的 GraphServiceImpl/GradeServiceImpl/FusionServiceImpl** → **L2 event (GraphChangedEvent)**: ✅ L2 → L2，事件发布方向正确
- 无业务层 import 基础设施实现类：✅ `MetricsService` 通过接口调用 `GdsAdapter`
- 无反向依赖：✅ `GdsAdapter` (L3) 不依赖任何 L2 类（只依赖 L2 传递的 record `MetricsQuery`/`MetricResultBO`，这是数据对象，符合分层规范）

### R6 · Domain Model Distortion 领域扭曲

#### ✅ 领域命名清晰

- `MetricsQuery` / `MetricResultBO` / `MetricsProperties` — 直接映射业务概念
- `GraphChangedEvent` — 准确描述事件语义
- `MetricsCacheInvalidator` — 准确描述消费者职责
- `GdsAdapter` — 准确描述技术角色（GDS 适配器）
- `nodeId` / `nodeType` / `metricName` / `metricValue` — 字段名与 Neo4j 图指标领域一致

#### 🟢 R6-1 · `metricName` 使用硬编码字符串

- **Symptom**: [GdsAdapter.java:50-55](src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java#L50-L55) — `switch` 分支用字符串 `"pagerank"` / `"inDegree"` / `"outDegree"`，[MetricsServiceImpl.java:66-69](src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java#L66-L69) 同样用字符串
- **Source**: Evans · DDD · "Supple Design" — 领域概念应收敛为常量或枚举
- **Consequence**: 若未来修改指标名称需改多处；拼写错误在运行时才暴露
- **Remedy**: 引入 `MetricType` 枚举（`PAGERANK` / `IN_DEGREE` / `OUT_DEGREE`）替代字符串。**记入 v2 backlog**

### 第二轮结论

| 严重度 | 数量 | 项目 |
|--------|------|------|
| 🔴 Critical | 0 | — |
| 🟡 Major | 2 | R1-1 (GdsAdapter 方法拆分), R2-1 (事件发布解耦) |
| 🟢 Minor | 4 | R1-2 (Controller 重复), R2-2 (注释不一致), R3-1 (注释过时), R4-2 (cache 条目), R6-1 (硬编码字符串) |

---

## 第三轮 · UI 视觉审查

> ❌ 跳过 — 后端 API 项目，无 UI 文件。

---

## 第四轮 · 补充审查

### 4.1 技术债评估

> 跳过 — 未装 brooks-lint，非里程碑版本。

### 4.2 跨模型 spot-check

> 跳过 — 未触发命中条件（无安全/认证变更、无并发/分布式、无 >80 行函数、测试覆盖率未下降）。

---

## 严重度汇总

| # | 轮次 | 维度 | 严重度 | 文件:行 | 说明 |
|---|------|------|--------|--------|------|
| 1 | R1 | Cognitive Overload | 🟡 Major | GdsAdapter.java:170-213 | 方法职责混合（Cypher 拼+映射+排序） |
| 2 | R2 | Change Propagation | 🟡 Major | GraphServiceImpl:116 等 4 处 | 事件发布硬编码，缺乏编译期强制 |
| 3 | R3 | Knowledge Duplication | 🟢 Minor | MetricsQueryRequest.java:28,40 | 注释残留 "转为大写" |
| 4 | R6 | Domain Distortion | 🟢 Minor | GdsAdapter.java:50, MetricsServiceImpl:66 | metricName 硬编码字符串 |

---

## Fix 任务

> 🔴 Critical: 0 → 无需立即修复任务
> 🟡 Major: 2 → 均为架构级改进，不在本次 change 修复范围内，记入 v2 backlog

### 本次修复任务

```xml
<task id="T-FIX-01" status="pending">
  <name>修复 MetricsQueryRequest 注释：移除 "转为大写" 描述</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
  </write_files>
  <action>
    MetricsQueryRequest.java 中两处 JavaDoc 注释 (line 28, line 40) 仍描述 "转为大写去重的 Set"，
    但代码已移除 toUpperCase()。更新注释为 "去重的 Set（保留原始大小写，由 MetricsServiceImpl
    归一化为规范标签）"。
  </action>
  <verify>grep -n "大写" src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java → 0 命中</verify>
  <done>注释与代码一致</done>
  <depends_on></depends_on>
</task>
```

### v2 Backlog（不本次）

- **R1-1**: GdsAdapter 方法职责拆分（extract `mapRowToMetricResult`）
- **R2-1**: AOP 切面自动发布 GraphChangedEvent（`@GraphMutation` 注解）
- **R6-1**: 引入 `MetricType` 枚举替代硬编码字符串

---

## 审查结论

✅ **REVIEW 通过** — 0 个 Critical 项，2 个 Major 项均为架构级优化（不阻塞本次交付），4 个 Minor 项中 1 个可立即修复（T-FIX-01）。

> 审查基于 git diff main~6..main，13 个生产文件 + 5 个测试文件，351 insertions, 5 deletions。