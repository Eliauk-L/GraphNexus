# DESIGN: 学情诊断历史记录查询与导出

- **Change ID**: `diagnosis-history-export`
- **关联**: `@.specs/diagnosis-history-export/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 项目技术栈已在 CONTEXT.md「已锁技术决策」中完整锁定，跳过卡片选择。

- **选定**：沿用既有 GraphNexus 全栈
- **前端**：Vue 3 + TypeScript + Vite + Naive UI + Pinia
- **后端**：Java 17 + Spring Boot 3.3 + Spring Data JPA + Apache POI 5.2.5
- **数据库**：MySQL 8.0（`query_task` 表，复用）
- **关键依赖**：Apache POI 5.2.5（已在 pom.xml，复用 SXSSFWorkbook 流式写 Excel）
- **理由**：零新增依赖，完全复用既有栈。历史查询走 JPA 分页；导出走 POI 流式写 + Spring ResponseEntity 文件下载
- **明确排除**：不引入 EasyExcel（POI 已够用）、不引入 Quartz/异步导出队列（v1 同步）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 确认）：
- src/main/java/com/graphnexus/api/query/controller/QueryController.java（L1 · 新增端点）
- src/main/java/com/graphnexus/application/query/chat/service/QueryService.java（L2 · 新增接口方法）
- src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java（L2 · 新增实现）
- src/main/java/com/graphnexus/infrastructure/mysql/query/repository/QueryTaskRepository.java（L3 · 扩展查询能力）
- src/main/java/com/graphnexus/infrastructure/mysql/query/entity/QueryTaskDO.java（L3 · 复用，不改）
- src/main/java/com/graphnexus/common/exception/ErrorCode.java（common · 新增 A0023）

新增模块：
- frontend/src/views/query/components/HistoryPanel.vue（新组件）
- frontend/src/api/query.ts（追加导出函数）
- frontend/src/api/types.ts（追加类型定义）
- frontend/src/views/query/queryStore.ts（追加 history 状态）
- frontend/src/views/query/IntelligentQAPage.vue（嵌入新组件）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- src/main/java/com/graphnexus/application/query/chat/intent/*（意图识别，与历史查询无关）
- src/main/java/com/graphnexus/application/analysis/strategy/*（剪枝策略，无关）
- src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java（Neo4j，历史查询不需要图数据）
- frontend/src/views/query/components/ChatInput.vue（输入组件，不改）
- frontend/src/views/query/components/MarkdownReport.vue（渲染组件，只复用不改）
- frontend/src/views/query/components/HtmlSvgViewer.vue（渲染组件，只复用不改）
- pom.xml（禁动清单保护）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 分页查询 | `PageResult` + Spring Data `Pageable` | 沿用（grade 模块同模式） |
| 动态筛选查询 | 既有 `ExamRecordRepository` 用 `@Query` + 方法名派生，无 Specification 先例 | **引入新模式** JPA Specification（理由：4 个可选筛选参数组合爆炸，方法名派生不可行） |
| 数据表格 | `DataTable.vue`（通用组件，Naive UI 封装） | 沿用 |
| 状态标签 | `StatusBadge.vue` | 沿用 |
| Excel 生成 | Apache POI 5.2.5（`ExcelGradeParser` 已用） | 沿用（用 `SXSSFWorkbook` 流式写，防 OOM） |
| 文件下载响应 | `ResponseEntity<InputStreamResource>` 或 `StreamingResponseBody` | **引入新模式**（项目首次文件下载 API，选 `StreamingResponseBody` 异步非阻塞） |
| Markdown 渲染 | `MarkdownReport.vue` | 沿用（详情展开时复用） |
| HTML/SVG 渲染 | `HtmlSvgViewer.vue` | 沿用（详情展开时按 outputFormat 选择） |
| 折叠面板 | Naive UI `NCollapse` | 沿用 |
| 日期选择 | Naive UI `NDatePicker` | 沿用 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：**沿用** Repository + JPA 模式（既有 repos/* 都是这风格），**引入** JpaSpecificationExecutor（项目首次，理由：4+ 可选筛选参数，@Query 方法名组合爆炸）
- 错误处理：**沿用** BusinessException + ErrorCode 枚举 + GlobalExceptionHandler
- API 路由组织：**沿用** api/query/controller/QueryController.java 新增方法
- 前端状态管理：**沿用** Pinia store 范式（queryStore.ts），追加 history 状态切片
- 前端组件组织：**沿用** views/query/components/ 目录放子组件，页面级组件放 views/query/
- 文件下载：**引入新模式** StreamingResponseBody + blob download（项目首次文件导出 API，无既有抽象可复用）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| **D1** | 历史查询 API 放 `QueryController`（`/api/v1/query/history`），不新建独立 Controller | 新建 `QueryHistoryController`（`/api/v1/query-history`） | 历史查询与问答核心链路共享 `query_task` 表和 `QueryService`，放同一 Controller 内聚性更好；URL `/query/history` 层次清晰（`/query` 命名空间下的子资源） | 若未来历史功能膨胀（统计/对比/分享），可能需要拆分为独立 Controller；v1 场景下 Controller 方法数可控（从 4 个 → 7 个） |
| **D2** | 动态筛选用 JPA `Specification` + `JpaSpecificationExecutor` | ① `@Query` 多条件 JPQL；② 方法名派生；③ QueryDSL | 4 个可选筛选参数（studentName/studentNo/subject/status）+ 时间范围，条件组合多，JPQL 拼接可读性差、方法名爆炸。Specification 是 Spring Data JPA 标准方案，无需引入 QueryDSL 依赖 | 放弃方法名派生的"一眼看懂查询条件"优势，Specification 的 where 链需读代码才能理解查询逻辑 |
| **D3** | 批量导出用 `SXSSFWorkbook`（流式写），查询结果直接写 `ServletOutputStream` | ① 先查全部 → `XSSFWorkbook` → 写字节数组；② EasyExcel | `SXSSFWorkbook` 仅保留 100 行在内存，其余刷盘，避免 5000 条 OOM；POI 已在 pom.xml，零新增依赖 | 相比 EasyExcel，POI API 稍繁琐（需手动建 Sheet/Row/Cell）；但 v1 导出字段固定 9 列，复杂度可控 |
| **D4** | 文件下载用 `StreamingResponseBody` 而非 `ResponseEntity<byte[]>` | `ResponseEntity<byte[]>` 或 `InputStreamResource` | `StreamingResponseBody` 异步非阻塞写，Tomcat 线程不阻塞等待；与 Spring Boot 响应式写文件模式一致 | 错误处理略复杂（HTTP 头已发送后写入失败无法改状态码）；v1 通过在写流前做预校验（taskId 存在性、超限检查）缓解 |
| **D5** | 单条导出复用 `query_task.answer` 字段原样输出，不做格式转换 | 根据 `outputFormat` 做 Markdown→HTML 或 HTML→Markdown 转换再导出 | answer 字段存储的是已校验通过的最终格式文本（Markdown 或 HTML），导出原样即可保持与页面展示一致；转换增加复杂度且可能丢失格式 | 若 answer 是 HTML+SVG 格式，用户用文本编辑器打开 `.md` 文件体验不佳；通过 `Content-Type` 正确设置 + 文件名后缀反映实际格式缓解（见 D7） |
| **D6** | 前端导出用 `axios` 直接请求 + `responseType: 'blob'`，绕过 JSON 拦截器 | ① 通过后端生成临时下载链接；② 新开 `window.open` | `responseType: 'blob'` 是标准做法；`client.ts` 拦截器 unwrap `ApiResult.data` 只对 JSON 响应生效，blob 响应不受影响 | 需在调用处手动创建下载链（`URL.createObjectURL` + `<a>` click），不能复用 client 实例的错误处理逻辑 |
| **D7** | 导出文件名反映实际内容格式 | 固定后缀 | 单条导出：检测 `answer` 内容特征——以 `<` 开头 → `.html`，以 `#` 开头 → `.md`；批量导出固定 `.xlsx` | 文件名格式检测规则简单（看首字符），极端情况（Markdown 以 HTML 标签开头）可能误判；概率极低（answer 经 prompt 约束首字符），可接受 |
| **D8** | 前端 HistoryPanel 通过 queryStore 新增 `historyState` 管理，与现有 `chatState` 共存 | 新建 `historyStore.ts` | 历史记录与问答流程同属学情诊断页面，共享同一 View 上下文；拆分 store 增加跨 store 通信成本（如新诊断完成后刷新历史列表）。Pinia store 内通过命名分组即可区分 | queryStore 文件变长（预计从 108 行 → ~200 行）；若未来页面进一步膨胀可再拆 |
| **D9** | 单条展开详情复用 `MarkdownReport.vue` / `HtmlSvgViewer.vue`，按 `outputFormat` 选择 | 新建 `HistoryDetail.vue` | 渲染逻辑完全一致（Markdown/HTML+SVG 渲染），复用避免重复实现和安全净化逻辑散落 | 展开上下文不同（历史记录行内 vs 问答结果区），需确保组件在两种布局下都能正常渲染；两者均为纯渲染组件无布局依赖，风险低 |
| **D10** | 新增错误码 `A0023`：导出记录数超过上限 | 复用 `A0002`（参数校验失败） | 语义更精确，前端可根据错误码做针对性提示（如"请缩小筛选范围"），区分于通用参数错误 | 错误码表增长 1 行；A0022 已被"考试编号已存在"占用，A0023 是下一个可用编号 |

---

## 2. 数据流 / 架构图

### 2.1 后端：历史查询数据流

```
  Browser ──GET /api/v1/query/history?studentName=&subject=&status=&startDate=&endDate=&pageNum=&pageSize──> QueryController
                                                                                                                                 │
                                                                                                                                 v
                                                                                                              QueryService.queryHistory(dto)
                                                                                                                                 │
                                                                                                                                 v
                                                                                                    QueryTaskRepository.findAll(spec, pageable)
                                                                                                                                 │
                                                                                                                                 v
                                                                                                       MySQL query_task 表 (SELECT ... WHERE ... LIMIT)
                                                                                                                                 │
                                                                                                                                 v
                                                                                            PageResult<HistoryRecordVO> ──JSON──> Browser
```

### 2.2 后端：单条导出数据流

```
  Browser ──GET /api/v1/query/history/{taskId}/export──> QueryController
                                                                        │
                                                                        v ① validate taskId (UUID regex)
                                                                        │
                                                                        v ② QueryTaskRepository.findByTaskId(taskId)
                                                                        │
                                                                        ├── NOT FOUND ──> 404 A0021
                                                                        │
                                                                        └── FOUND ──> ③ 检测 answer 首字符 → 定 Content-Type + 文件名
                                                                                              │
                                                                                              v ④ StreamingResponseBody: outputStream.write(answer.getBytes(UTF-8))
                                                                                              │
                                                                                              v Browser 下载 .md 或 .html 文件
```

### 2.3 后端：批量导出数据流

```
  Browser ──GET /api/v1/query/history/export?subject=&status=&...──> QueryController
                                                                                  │
                                                                                  v ① 查询 count（不加分页）
                                                                                  │
                                                                                  ├── count > 5000 ──> 400 A0023
                                                                                  │
                                                                                  └── count ≤ 5000 ──> ② Stream<QueryTaskDO> (JPA Stream)
                                                                                                                │
                                                                                                                v ③ SXSSFWorkbook 逐行写 Sheet
                                                                                                                │
                                                                                                                v ④ StreamingResponseBody → ServletOutputStream
                                                                                                                │
                                                                                                                v Browser 下载 .xlsx 文件
```

### 2.4 前端：HistoryPanel 数据流

```
  User
   │
   ├── 展开面板 ──> queryStore.loadHistory() ──> GET /query/history ──> DataTable 渲染列表
   │
   ├── 修改筛选 ──> queryStore.filters = {...} ──> loadHistory() ──> 列表刷新 + 分页重置
   │
   ├── 翻页 ──> queryStore.loadHistory(page=2) ──> 列表更新
   │
   ├── 点击行展开 ──> 本地渲染（answer 已在 history record 中返回？）
   │                      │
   │                      ├── 列表接口不返回 answer（太大）
   │                      └── 展开时需调用 GET /query/result/{taskId} 获取完整 answer
   │                           │
   │                           └── 按 outputFormat 选 MarkdownReport / HtmlSvgViewer 渲染
   │
   ├── 单条导出 ──> axios GET /query/history/{taskId}/export {responseType:'blob'}
   │                     ──> URL.createObjectURL(blob) ──> <a>.click() 下载
   │
   └── 批量导出 ──> axios GET /query/history/export?<当前筛选参数> {responseType:'blob'}
                         ──> URL.createObjectURL(blob) ──> <a>.click() 下载
```

> **注意**：列表接口（`/query/history`）**不返回 `answer` 字段**——避免一次响应传输大量 MEDIUMTEXT 数据。单条展开时通过已有的 `GET /api/v1/query/result/{taskId}` 按需加载完整 answer。

### 2.5 前端：组件树

```
IntelligentQAPage.vue
├── ChatInput.vue                    （不改动）
├── loading/error state              （不改动）
├── qa-history（会话内展示）         （不改动）
└── HistoryPanel.vue                 （新增 · NCollapse 折叠面板）
    ├── 筛选栏（NInput + NSelect + NDatePicker + NButton）
    ├── DataTable.vue                （复用 · 历史列表）
    ├── NPagination                  （分页）
    ├── 批量导出按钮                  （NButton）
    └── 展开行（单条详情）
        ├── MarkdownReport.vue       （复用 · outputFormat=markdown 时）
        ├── HtmlSvgViewer.vue        （复用 · outputFormat=html-svg 时）
        ├── TokenUsageBar.vue        （复用 · token 用量展示）
        └── 单条导出按钮              （NButton）
```

---

## 3. 关键状态机

本次无新增状态机。`query_task.status` 为既有字段（PENDING/PROCESSING/COMPLETED/FAILED），历史查询仅作为筛选条件读取。

---

## 4. ADR 索引

| ADR | 标题 | 说明 |
|-----|------|------|
| ADR-030 | 历史查询 JPA Specification | 动态筛选用 `JpaSpecificationExecutor` |
| ADR-031 | 诊断报告导出格式 | 单条 Markdown/HTML 流式输出 + 批量 SXSSFWorkbook Excel |

### ADR-030: 历史查询 JPA Specification — 动态条件查询方案

- **Context**: 历史记录查询需支持 4 个可选筛选参数（studentName 模糊、studentNo 精确、subject 精确、status 精确）+ 时间范围（startDate/endDate），共 2^6 = 64 种参数组合。Spring Data JPA 方法名派生不支持可选参数（`null` 时应忽略条件），`@Query` JPQL 拼接 `WHERE (:param IS NULL OR field = :param)` 虽可行但可读性差。
- **Decision**: `QueryTaskRepository` 扩展 `JpaSpecificationExecutor<QueryTaskDO>`，在 Service 层构建 `Specification` 动态 where 链。
- **Consequences**: ① Repository 接口增加 `JpaSpecificationExecutor` 父接口（无侵入，JPA 原生支持）；② Service 层新增 `buildHistorySpec()` 私有方法构建 where 条件；③ 项目首次引入 Specification 模式——后续其他需要动态筛选的 Repository 可参照此模式。

### ADR-031: 诊断报告导出格式 — 单条 .md/.html + 批量 .xlsx

- **Context**: 用户需要导出诊断报告。单条导出应保留 LLM 分析原文（Markdown 或 HTML+SVG），批量导出应输出结构化列表（Excel）。需决定文件格式、下载方式和文件名策略。
- **Decision**: 单条导出原样输出 `answer` 字段文本，Content-Type 按内容首字符判定（`<` → `text/html`，`#` → `text/markdown`），文件名后缀随之（`.html` / `.md`）。批量导出用 `SXSSFWorkbook` 写 `.xlsx`，固定 9 列，不包含 `answer` 正文。文件下载均用 `StreamingResponseBody`。
- **Consequences**: ① 单条导出的文件可在浏览器中直接打开（.html）或用 Markdown 编辑器打开（.md），保留原始格式；② 批量导出的 Excel 可被 Excel/WPS/Google Sheets 打开；③ 若未来需要统一导出 PDF，需引入 HTML→PDF 渲染引擎（如 Flying Saucer），替换 ADR-031 的单条导出方案。

---

## 5. 风险

| # | 风险 | 类型 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|---|
| **R1** | 5000 条 Excel 导出仍可能 OOM（SXSSFWorkbook 流式写但 MySQL 结果集需先加载） | 实现风险 | 导出超 5000 条时服务响应慢或 OOM | 中 | 使用 JPA `Stream` 结果集（`@QueryHint(FETCH_SIZE)`）+ `SXSSFWorkbook(100)` 窗口写，逐行处理不持全量引用；导出时监控堆内存，超限 kill |
| **R2** | `Specification` 条件拼接遗漏导致查询结果不对 | 实现风险 | 筛选条件部分失效，用户看到错误数据 | 中 | AC-2 验证各筛选参数独立生效；TASK 中要求 6 个筛选参数各写一个单测（共 6 个 `@DataJpaTest`） |
| **R3** | 单条展开按需加载 answer 增加一次网络请求，用户感知延迟 | 上线风险 | 历史列表展开详情有 ~200ms 延迟 | 低 | answer 加载是单条查询（`findByTaskId` 主键查），响应时间 < 50ms；前端加骨架屏/loading 过渡 |
| **R4** | 导出文件时前端 blob 下载在部分浏览器（Safari < 14.1）不兼容 | 上线风险 | Safari 旧版用户无法下载 | 低 | V1 目标浏览器 Chrome/Firefox/Edge 120+，均支持 `URL.createObjectURL` + `<a>.click()`；Safari 兼容通过 `navigator.msSaveBlob` 兜底（IE 已不要求） |
| **R5** | 历史数据增长导致查询变慢 | 长期债务 | `query_task` 表百万级后分页查询 > 500ms | 中 | v1 依赖 MySQL 索引（`create_time` 已有默认排序，筛选字段按需加联合索引）；v2 引入归档策略（N 个月前数据归档到历史表）或 ES 全文检索 |
| **R6** | 导出接口无鉴权，任何人都可导出任意 taskId 的诊断报告 | 长期债务 | 诊断报告可能含学生隐私数据 | 中 | V1 全站 Spring Security 放通（CONTEXT.md 已锁），导出接口复用；待 V1 引入认证后自动受保护，不额外处理 |

---

## 6. 不在范围

- **导出权限控制**：v1 不做用户级/角色级权限校验，等全局认证体系引入后自动受保护
- **导出水印/页脚**：导出文件不带学校 Logo、生成时间戳水印
- **历史数据归档**：不做 `query_task` 表分区或按时间归档旧数据
- **导出进度通知**：批量导出不做进度条或 WebSocket 推送（同步下载，小数据量无需）
- **历史记录对比**：不选两条记录并排展示了
- **导出格式配置**：不支持用户自定义导出列（批量固定 9 列）、不支持选择文件编码
- **批量打印**：不做浏览器的 `window.print()` 打印样式适配

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/query/chat/service/QueryService.java` | `queryHistory(dto)` + `exportSingle(taskId)` + `exportBatch(dto)` 三个新方法 | 历史查询 + 导出 | —（Service 方法，非可复用抽象） |

本 change 无新增 `lib/` / `utils/` 级可复用抽象。文件下载 `StreamingResponseBody` 模式在 QueryController 内实现，后续其他模块需要文件导出时可提取为公共工具方法。

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 文件下载模式 | `StreamingResponseBody` + `Content-Disposition: attachment` | 所有需要文件导出的端点 | 低 — 替换为 `ResponseEntity<InputStreamResource>` 只需改 Controller 返回类型 |
| 动态查询方案 | JPA `Specification` + `JpaSpecificationExecutor` | 所有需要多条件可选筛选的 Repository | 中 — 后续 Repository 可选是否沿用；已有 `@Query` 方法不受影响 |

### 9.3 新增 / 修改的跨模块契约

```
- 新增 GET /api/v1/query/history（历史分页查询 · 6 个可选筛选参数 + pageNum/pageSize）
- 新增 GET /api/v1/query/history/{taskId}/export（单条报告导出 · 文件下载）
- 新增 GET /api/v1/query/history/export（批量 Excel 导出 · 同筛选参数 · 上限 5000 条）
- 新增 ErrorCode A0023: 导出记录数超过上限（5000 条），请缩小筛选范围
```

### 9.4 新增 / 升级的依赖

无。Apache POI 5.2.5 已在 pom.xml 中，本次仅新增使用。

### 9.5 禁动清单变化

```
- 新增禁动：src/main/java/com/graphnexus/application/query/chat/intent/*（后续 change 如需增加 QueryIntent 枚举值，不影响历史查询）
- 新增禁动：query_task 表结构（本次不改动 DDL，后续 change 如需加字段需走独立 CHANGE）
```

---

> 本文件不包含完整代码实现。函数签名与伪代码见各节框图；接口定义见 §4 ADR。