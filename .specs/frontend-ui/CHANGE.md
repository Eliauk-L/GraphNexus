# CHANGE: GraphNexus 前端管理界面

- **Change ID**: `frontend-ui`
- **创建日期**: 2026-06-17
- **路径建议**: 完整（`REQUIREMENT → UI-DESIGN → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

后端 API 已初步实现（文档管理、图谱抽取、融合引擎、图指标、智能问答），但系统完全没有用户界面。管理员/教师只能通过 curl/Postman 操作，无法在实际业务场景中使用。需要构建前端界面让用户**看得见图谱、问得出诊断、管得了数据**。

## What（做什么）

基于 Vue 3 + TypeScript 构建 SPA 前端管理界面，覆盖 6 大功能模块：

1. **文档管理** — PDF/CSV 上传、列表、解析状态跟踪、删除
2. **知识图谱可视化** — 节点-边图渲染，文档子图浏览
3. **成绩管理** — 按考试查看成绩、删除
4. **智能问答** — 自然语言输入 → Markdown 诊断报告展示
5. **融合管理** — 触发融合、查看状态、回滚
6. **图指标看板** — PageRank、度中心性展示

V1 策略：**先搭建页面框架，文档管理 + 智能问答优先上线，其余逐步迭代**。

## 视觉调性（前端项目必填，由 0-change 步骤 0.6 预选填入）

- **选定**：2️⃣ 极简（Minimal）
- **理由**：GraphNexus 是专业教育分析工具，教师需要专注、无干扰的数据交互体验。极简的几何字体 + 大留白让知识图谱可视化和诊断报告成为视觉焦点。
- **参考产品**：Linear、Vercel、Stripe（产品端）
- **明确排除**：4️⃣ 玩具（教育 ≠ 儿童产品，这是给教师用的专业工具）、7️⃣ 复古未来（太 distract，与严肃教研场景冲突）、5️⃣ 奢华（教育/公共部门场景不对味）

> 此选择会被 `2a-ui-design.md` 继承，2a 阶段不再重选调性，只在此基础上深化（颜色 / 字体 / 间距等）。

## 影响面

- [x] 影响 `REQUIREMENT.md`（新功能，需完整需求文档）
- [x] 影响 `DESIGN.md` / 引入新 ADR（前端技术选型、组件库、图可视化库、状态管理等）
- [ ] 影响现有 AC（前端纯消费后端 API，不修改后端）
- [ ] 影响数据模型 / 迁移
- [ ] 影响外部 API 兼容性
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- 登录/鉴权页面（V1 跳过，后端 Spring Security 临时放开或内网部署）
- 学生端/家长端界面（仅管理员后台）
- 移动端适配（桌面优先）
- 实时通知/WebSocket 推送
- 国际化（i18n）
- 深色模式（V1 仅亮色极简主题）
- 自定义仪表盘/拖拽布局

## 验收线（粗粒度，不是 AC）

1. 管理员能通过 UI **上传 PDF 文档 → 触发解析 → 看到文档列表和状态变化**
2. 管理员能通过 UI **输入自然语言问题 → 看到 Markdown 格式的学生诊断报告**
3. 6 个页面框架完整，导航可切换，核心页面（文档管理 + 智能问答）功能完整可用

## 后端 API 清单（2026-06-18 深度审查）

> 全部 6 个 Controller · 20 个端点已实现。**注意**：当前仓库存在未提交的大规模包重构（`ApiResponse`→`ApiResult`，`api/document/`→`api/file/` 等），但端点 URL 全部不变。

### 端点明细

| # | Swagger Tag | Controller 路径 | 端点 | 方法 | 路径 | 关键请求/响应 |
|---|------------|----------------|------|------|------|-------------|
| 1 | `文档处理` | `api/file/controller/DocumentController` | 上传文件 | `POST` | `/api/v1/document/upload` | `MultipartFile` + `subject` → `DocumentVO` 或 `GradeUploadResultVO` |
| 2 | | | 触发解析 | `POST` | `/api/v1/document/{id}/process` | → `ParseResultVO`（textContent + pageCount） |
| 3 | | | 分页列表 | `GET` | `/api/v1/document?pageNum=&pageSize=` | → `PageResult<DocumentVO>`（页码从 1） |
| 4 | | | 文档详情 | `GET` | `/api/v1/document/{id}` | → `DocumentVO`（含 status 状态机） |
| 5 | | | 更新名称 | `PUT` | `/api/v1/document/{id}` | `{name}` → `DocumentVO` |
| 6 | | | 删除文档 | `DELETE` | `/api/v1/document/{id}` | → `null`（级联 MinIO+Neo4j） |
| 7 | | | 成绩查询 | `GET` | `/api/v1/document/grade/exam/{examNo}` | → `List<GradeRecordVO>` |
| 8 | | | 成绩删除 | `DELETE` | `/api/v1/document/grade/exam/{examNo}` | → `DeleteResultVO` |
| 9 | `知识图谱` | `api/graph/controller/GraphController` | 触发抽取 | `POST` | `/api/v1/graph/extract/{documentId}` | → `ExtractionResultVO`（entity/KP/category/edge 计数） |
| 10 | | | 文档子图 | `GET` | `/api/v1/graph/document/{documentId}` | → `GraphSubgraphVO`（nodes: `[{id,nodeType,documentId}]` + edges: `[{source,target,type}]`） |
| 11 | `宽图谱融合` | `api/graph/controller/FusionController` | 全量融合 | `POST` | `/api/v1/graph/fusion/execute` | → `FusionExecuteVO`（fusionLogId + KP 组数 + MASTERS 边数） |
| 12 | | | 融合状态 | `GET` | `/api/v1/graph/fusion/status` | → `FusionStatusVO`（含审计 JSON） |
| 13 | | | 回滚融合 | `POST` | `/api/v1/graph/fusion/rollback/{logId}` | → `FusionRollbackVO` |
| 14 | `图指标` | `api/graph/controller/MetricsController` | PageRank | `GET` | `/api/v1/graph/metrics/pagerank?nodeTypes=&edgeTypes=` | → `List<MetricResultVO>`（nodeId/type/name/value） |
| 15 | | | 度中心性 | `GET` | `/api/v1/graph/metrics/degree?nodeTypes=&edgeTypes=` | → `List<MetricResultVO>`（inDegree + outDegree） |
| 16 | `智能问答` | `api/query/controller/QueryController` | 同步问答 | `POST` | `/api/v1/query/ask` | `{question, studentName?, studentNo?, subject}` → `QueryAskResponse`（Markdown answer） |
| 17 | | | 异步问答 | `POST` | `/api/v1/query/ask-async` | 同上 → `QueryAsyncResponse`（taskId，轮询用） |
| 18 | | | **智能对话** ⭐ | `POST` | `/api/v1/query/chat` | **仅** `{question}` → `QueryAskResponse`（系统自动 LLM 提取实体） |
| 19 | | | 轮询结果 | `GET` | `/api/v1/query/result/{taskId}` | → `QueryResultResponse`（PENDING→PROCESSING→COMPLETED/FAILED） |
| 20 | `图分析` | `api/analysis/controller/AnalysisController` | 剪枝子图 | `GET` | `/api/v1/analysis/subgraph/{taskId}` | → `SubgraphResponse`（nodes含properties Map + edges含weight + pruningMeta） |

### 统一响应格式

```json
// 成功：ApiResult<T>（Java record）
{ "code": 200, "message": "success", "data": {...}, "traceId": "...", "timestamp": 1718000000000 }

// 分页：ApiResult<PageResult<T>>
{ "code": 200, "data": { "list": [...], "total": 100, "pageNum": 1, "pageSize": 10 } }

// 错误：ErrorResponse（由 GlobalExceptionHandler 统一返回）
{ "errorCode": "A0002", "errorMessage": "...", "userTip": "...", "traceId": "...", "timestamp": ... }
```

### Swagger UI

- `OpenApiConfig` 位于 `common/config/`，已覆盖全部 6 个模块包
- 可用端点：`/swagger-ui.html`
- LLM 调试端点（`LlmController`）已在 business 分组中排除

## 风险与未知

- **`ApiResponse`→`ApiResult` 重命名**（commit `417d2e8`）：前端 HTTP client 需感知此变更，虽然 JSON 结构不变（code/message/data/traceId/timestamp），但 Swagger schema 名称已变
- **正在进行的包重构**：当前 `main` 分支存在 50+ 未提文件变更（rename/delete/modify），建议前端开工前先让后端团队完成此重构并 commit，避免在移动靶上开发
- **图可视化库选型**：需在 Vue 3 生态中评估 ECharts/D3.js/Cytoscape.js 等，需同时适配「文档子图」（`GraphSubgraphVO`，节点无 properties）和「剪枝子图」（`SubgraphResponse`，节点含完整 properties Map）两种不同数据结构
- **CORS**：前端 Vite dev server（`:5173`）需配置 proxy 转发 `/api` 到后端，或后端添加 CORS 配置
- **智能问答 `/chat` 端点是前端最佳入口**：只需传 `{question}`，无需手动填 studentName/studentNo/subject，应作为前端默认问答交互方式

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `UI-DESIGN.md` / `DESIGN.md`，本文件不再扩展。