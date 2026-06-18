# REQUIREMENT: 图谱节点指标度量（PageRank + 度中心性）

- **Change ID**: graph-metrics
- **关联**: `@.specs/graph-metrics/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/教学分析人员，我想查看知识图谱中知识点的 PageRank 值和度中心性，以便识别"核心枢纽"知识点，辅助教学重点判断。
- **US-2**：作为系统，在图谱数据变更后，我想自动重算图指标缓存，以便后续查询始终返回与当前图谱一致的结果，无需人工干预。
- **US-3**：作为 API 调用方，我想按节点类型和边类型过滤参与计算的子图范围，以便针对不同业务场景（如只看前置依赖关系的知识网络 vs 全部关系）获得有针对性的指标。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 查询 PageRank（默认全图）

- **Given** Neo4j 宽图谱中已有至少 1 个 KnowledgePoint 节点和 1 条 PREREQUISITE_OF 边
- **When** 调用 `GET /api/v1/graph/metrics/pagerank`
- **Then** 返回 HTTP 200，响应体为节点 ID + PageRank 值列表，按值降序排列；响应时间 < 2s
- **验证方式**: 在已导入 CSV 成绩和至少 1 份文档图谱后，用 curl 请求端点，检查 JSON 结构与排序

### AC-2 · 查询度中心性

- **Given** Neo4j 宽图谱中已有 Student/Exam/KnowledgePoint 节点及 ATTENDED/TESTED 边
- **When** 调用 `GET /api/v1/graph/metrics/degree`
- **Then** 返回 HTTP 200，每个节点含 `inDegree`、`outDegree`、`totalDegree` 三个字段；未指定过滤时默认全图所有节点类型
- **验证方式**: curl 请求，对比返回的 degree 值与 Cypher 手动 `COUNT` 结果一致

### AC-3 · 按节点类型和边类型过滤投影

- **Given** 图谱中同时存在 KnowledgePoint、Student、Exam、Entity 四种节点
- **When** 调用 `GET /api/v1/graph/metrics/pagerank?nodeTypes=KnowledgePoint&edgeTypes=PREREQUISITE_OF`
- **Then** 仅 KnowledgePoint 节点参与 PageRank 计算，投影子图中仅含 PREREQUISITE_OF 边；其他节点类型不出现在结果中；响应时间 < 2s
- **验证方式**: 带参数 curl 请求，确认返回节点数 = `MATCH (n:KnowledgePoint) RETURN count(n)`，且无 Student/Exam 节点

### AC-4 · 多边类型组合过滤

- **Given** 图谱中已有 PREREQUISITE_OF、ALIGNED_TO、BELONGS_TO 三种边
- **When** 调用 `GET /api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint,Entity&edgeTypes=PREREQUISITE_OF,ALIGNED_TO`
- **Then** 仅 KnowledgePoint 和 Entity 节点参与计算，投影子图仅含指定两种边类型；度值基于过滤后的子图计算
- **验证方式**: curl + 参数，Neo4j Browser 中手工 Cypher 投影对比度值

### AC-5 · 空图/无匹配节点时返回空结果

- **Given** Neo4j 中无匹配过滤条件的节点（如 `nodeTypes=Student` 但未导入任何成绩）
- **When** 调用 `GET /api/v1/graph/metrics/pagerank?nodeTypes=Student`
- **Then** 返回 HTTP 200，空数组 `[]`，不报错
- **验证方式**: 在空库或指定不存在的节点类型时 curl 请求，确认 200 且 `[]`

### AC-6 · 图谱变更后自动重算

- **Given** 已成功调用过一次 `GET /api/v1/graph/metrics/pagerank` 并获得结果 A
- **When** 通过 `POST /api/v1/graph/fusion/execute` 触发融合（图谱结构变更：KP 合并、MASTERS 边更新）
- **Then** 融合完成后，再次调用 `GET /api/v1/graph/metrics/pagerank`，返回的结果 B 与 A 不同（反映融合后的图结构），且无需手动调用任何 compute 端点
- **验证方式**: 顺序执行 curl 验证（查询 → 融合 → 再查询），对比两次结果不同

### AC-7 · 指标计算幂等性

- **Given** 图谱数据未发生变更
- **When** 连续两次调用 `GET /api/v1/graph/metrics/pagerank`（相同参数）
- **Then** 两次返回的 PageRank 值完全一致（相同节点、相同值、相同排序）
- **验证方式**: 连续 curl 两次，diff 响应体

### AC-8 · 参数校验

- **Given** 客户端
- **When** 调用 `GET /api/v1/graph/metrics/pagerank?nodeTypes=InvalidType`（不存在的节点类型）
- **Then** 返回 HTTP 400，含错误码和提示消息，列出有效节点类型
- **验证方式**: curl 传入非法参数，确认 400 响应

---

## 范围切分

### v1（本次必做）

- PageRank + 度中心性（Degree Centrality）两种 GDS 指标
- 通过 Cypher 调用 Neo4j GDS 过程（`gds.pageRank.stream` / `gds.degree.stream`）
- 可配置图投影过滤：`nodeTypes`（多选，逗号分隔） + `edgeTypes`（多选，逗号分隔）
- 默认投影全图（不传参数 = 所有节点 + 所有边）
- REST API 查询，结果仅通过 JSON 返回，不持久化到 Neo4j 节点属性
- 图谱变更后自动触发重算（事件驱动，监听融合 + 文档抽取 + CSV 导入完成事件）
- 重算结果内存缓存（Caffeine），缓存 TTL 可配置
- 参数校验（有效的节点类型/边类型枚举）

### v2（下一轮考虑）

- 介数中心性、紧密中心性、特征向量中心性等其他 GDS 指标
- 指标数值写回 Neo4j 节点属性（持久化），支持在 Cypher 查询中直接引用
- 基于指标的智能问答剪枝集成（如 Token 预算按 PageRank 优先级排序）
- 指标历史快照对比（本次 vs 上次的差异分析）
- 前端图可视化渲染（指标结果以热力图或节点大小展示）

### out（永远不做）

- 自研图算法实现（永远用 GDS 原生实现，不重复造轮子）
- 实时流式图指标更新（如每增一条边就重算 PageRank，代价太高）
- 基于指标的自动告警/异常检测（属监控系统职责，不在图谱指标范围内）

---

## 非功能性需求

- **性能**: 单次 PageRank/度中心性查询响应时间 < 2s（10 万节点以内规模）；并行计算超时 30s，超时返回降级错误
- **可观测性**: 记录每次 GDS 调用的投影规模（节点数、边数）、耗时、是否命中缓存到日志；指标查询 API 计入 TraceIdFilter
- **安全**: 所有端点需通过 Spring Security JWT 认证；无新增权限角色，复用现有认证链
- **兼容性**: Neo4j 5.26-community + GDS 5.x 插件（当前 podman-compose 已声明 `NEO4J_PLUGINS=["graph-data-science"]`）；Java 17 + Spring Boot 3.3.x，无版本变更
- **缓存**: 使用 Caffeine 本地缓存，默认 TTL 5 分钟（可配置），最大条目 50 个（每种投影参数组合一个条目）

## 依赖与假设

- **依赖**: Neo4j GDS 5.x 插件已可用（podman-compose 中 `NEO4J_PLUGINS` 已声明，需确认首次启动时插件已下载安装）
- **依赖**: Spring 事件机制（`ApplicationEventPublisher` + `@EventListener`）——当前项目未使用，本次首次引入
- **依赖**: Caffeine 本地缓存（`pom.xml` 中已有 Caffeine 依赖，通过 `CONTEXT.md` 已锁决策确认）
- **假设**: 当前图谱规模 < 10 万节点 + 50 万边，GDS 内存投影在此规模下可在 2s 内完成
- **假设**: GDS 图投影采用命名图（named graph）的 transient 模式（每次计算后释放，不持久化图投影），避免内存泄漏
- **假设**: 去抖（debounce）窗口不在此 change 实现——图谱变更事件驱动的重算直接触发，融合等操作通常不会短时间内连续触发

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
