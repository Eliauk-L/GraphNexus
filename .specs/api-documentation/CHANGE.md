# CHANGE: API 接口文档 — SpringDoc OpenAPI 3.0 自动生成

- **Change ID**: `api-documentation`
- **创建日期**: 2026-06-17
- **路径建议**: 最短（`TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: confirmed
- **用户决策**:
  - Q1 文档形式: A — OpenAPI 3.0（SpringDoc + Swagger UI）
  - Q2 覆盖范围: B — 排除 `LlmController`（调试接口不对外）
  - Q3 详细程度: B — 完整版（含错误码表、认证说明、curl 示例、业务场景）

---

## Why（为什么做）

后端 API 已全部实现，共 **6 个业务 Controller、19 个端点**，覆盖：

| 模块 | Controller | 端点 | 路径前缀 |
|------|-----------|------|---------|
| 文档处理 | `DocumentController` | 8 | `/api/v1/document` |
| 知识图谱 | `GraphController` | 2 | `/api/v1/graph` |
| 宽图谱融合 | `FusionController` | 3 | `/api/v1/graph/fusion` |
| 图指标 | `MetricsController` | 2 | `/api/v1/graph/metrics` |
| 智能问答 | `QueryController` | 3 | `/api/v1/query` |
| 图分析 | `AnalysisController` | 1 | `/api/v1/analysis` |
| ~~LLM调试~~ | ~~`LlmController`~~ | ~~2~~ | ~~排除~~ |

当前状态：
- SpringDoc 依赖已在 `pom.xml` 中声明（`springdoc-openapi-starter-webmvc-ui` 2.6.0），但**未配置、未启用**
- 所有 Controller 端点无任何 Swagger 注解（`@Tag`/`@Operation`/`@ApiResponses`/`@Schema`）
- DTO/VO 类无 `@Schema` 描述，响应结构对前端不透明
- Spring Security **已全局关闭**（`application.yml` 排除 `SecurityAutoConfiguration`），所有端点无需认证，Swagger UI 无需额外放行

前端人员目前只能通过阅读源码理解接口，效率低且易出错。

## What（做什么）

**一句话**：为 6 个业务 Controller 的 19 个端点补齐 SpringDoc OpenAPI 注解 + 配置，生成 Swagger UI 在线文档。

### 1. SpringDoc 配置化启用

`application-dev.yml` 中新增 `springdoc` 配置段：
- API 元信息（标题 `GraphNexus API`、版本 `1.0.0`、描述）
- Swagger UI 路径：`/swagger-ui.html`（默认）
- OpenAPI JSON 路径：`/v3/api-docs`（默认）
- 按 Profile 控制：dev 启用，prod 通过 `springdoc.api-docs.enabled=false` 关闭
- **无需配置安全方案**：当前 Spring Security 已关闭，所有端点公开

### 2. Controller 层注解补齐（6 个 Controller × 19 端点）

每个端点添加：
- `@Tag(name = "...")` — Controller 级别分组标签
- `@Operation(summary = "...", description = "...")` — 端点摘要 + 详细描述
- `@ApiResponses({@ApiResponse(responseCode = "200", ...), @ApiResponse(responseCode = "400", ...), ...})` — 各 HTTP 状态码响应
- `@Parameter(description = "...", required = true/false, example = "...")` — 参数说明（路径参数、查询参数）

**全量端点清单**：

```
DocumentController (/api/v1/document)
  POST   /upload                          — 上传文件（PDF/CSV 统一入口）
  POST   /{id}/process                    — 触发文档解析
  GET    /                                — 分页查询文档列表
  GET    /{id}                            — 查询单个文档
  PUT    /{id}                            — 更新文档名称
  DELETE /{id}                            — 删除文档（级联 MinIO + Neo4j）
  GET    /grade/exam/{examNo}             — 按考试编号查成绩
  DELETE /grade/exam/{examNo}             — 按考试编号级联删除成绩

GraphController (/api/v1/graph)
  POST   /extract/{documentId}            — 触发知识图谱抽取
  GET    /document/{documentId}           — 查询文档子图

FusionController (/api/v1/graph/fusion)
  POST   /execute                         — 手动全量融合
  GET    /status                          — 查询最近融合状态
  POST   /rollback/{fusionLogId}          — 回滚指定融合

MetricsController (/api/v1/graph/metrics)
  GET    /pagerank?nodeTypes=...&edgeTypes=...  — 查询 PageRank
  GET    /degree?nodeTypes=...&edgeTypes=...    — 查询度中心性

QueryController (/api/v1/query)
  POST   /ask                             — 同步问答
  POST   /ask-async                       — 异步问答（返回 taskId）
  GET    /result/{taskId}                 — 查询异步结果

AnalysisController (/api/v1/analysis)
  GET    /subgraph/{taskId}               — 查询剪枝子图数据
```

### 3. DTO/VO Schema 注解补齐（~15 个类）

所有 VO 和 Request 类添加 `@Schema` 注解：

| 类别 | 类 | 字段数 |
|------|-----|--------|
| Request | `QueryAskRequest` | 4 |
| Request | `UpdateDocumentRequest` | 1 |
| VO | `DocumentVO` | 9 |
| VO | `ParseResultVO` | 4 |
| VO | `GradeUploadResultVO` | 8 |
| VO | `GradeRecordVO` | 9 |
| VO | `DeleteResultVO` | 4 |
| VO | `ExtractionResultVO` | 5 |
| VO | `GraphSubgraphVO` (含嵌套) | 2 + GraphNodeVO(4) + GraphEdgeVO(4) |
| VO | `FusionExecuteVO` | 3 |
| VO | `FusionStatusVO` | 8 |
| VO | `FusionRollbackVO` | 3 |
| VO | `MetricResultVO` | 4 |
| VO | `QueryAskResponse` (含嵌套 TokenUsageVO) | 6 + 3 |
| VO | `QueryAsyncResponse` | 3 |
| VO | `QueryResultResponse` | 9 |
| VO | `SubgraphResponse` (含嵌套 NodeVO/EdgeVO/PruningMetaVO) | 5 + 3 + 3 + 7 |

### 4. 通用响应结构文档化

- `ApiResponse<T>` — 统一响应体（code, message, data, traceId, timestamp）注册到 OpenAPI schema
- `PageResult<T>` — 分页响应体（list, total, pageNum, pageSize）
- `ErrorResponse` — 错误响应体（errorCode, errorMessage, userTip, traceId, timestamp）
- 全局错误码表（21 个 `ErrorCode` 枚举）以描述文本形式呈现在文档概述中

### 5. 全局异常响应文档化

利用 SpringDoc 的 `@ApiResponse` 在每个端点上声明可能的错误码：
- `400` — 参数校验失败（A0002, A0004, A0008, A0009, A0011, A0012, A0013, A0019...）
- `404` — 资源不存在（A0001, A0006, A0015, A0016, A0021）
- `409` — 资源冲突（A0007, A0014, A0017, A0018, A0020）
- `500` — 系统内部错误（B0001）

## 影响面

- [ ] 影响 `REQUIREMENT.md` — 不涉及新功能需求，纯文档化
- [ ] 影响 `DESIGN.md` / 引入新 ADR — 不涉及架构决策
- [ ] 影响现有 AC — 无
- [ ] 影响数据模型 / 迁移 — 无
- [x] 影响外部 API 兼容性 — 仅添加注解，不改变端点路径/参数/响应结构，**完全向后兼容**
- [ ] 仅修复 bug，无范围变化
- [x] 影响 `application-dev.yml` — 新增 `springdoc` 配置段

## 核心约束

- **不修改端点行为**：只加注解和配置，不改任何业务逻辑、路径、参数、响应结构
- **不新增端点**：仅文档化已有端点
- **不修改 pom.xml**：SpringDoc 依赖已存在（v2.6.0），无需变更
- **不生成静态 Markdown**：Q1 选了 A，Swagger UI 为唯一文档载体（`openapi.json` 可导出）
- **遵循既有注解风格**：中文描述，作者 Jay
- **排除 LlmController**：`/api/v1/llm/ping`、`/api/v1/llm/debug` 为内部调试端点，不加 Swagger 注解
- **无需配置安全白名单**：Spring Security 已全局关闭，Swagger UI 路径 `/swagger-ui/**`、`/v3/api-docs/**` 天然可访问

## 范围排除（这次不做）

- ❌ **LlmController 文档化**：调试接口不对外暴露
- ❌ **静态 Markdown/PDF 文档**：Q1 选了 Swagger，不额外维护手写文档
- ❌ **API 版本管理策略**：不做 `/api/v2/` 或多版本共存规划
- ❌ **API 自动化测试生成**：不做基于 OpenAPI schema 的自动测试
- ❌ **API 网关集成**：openapi.json 的消费由前端/工具链自行决定
- ❌ **请求/响应示例的真实数据**：`@Schema(example = "...")` 使用合理占位值
- ❌ **API 鉴权方案**：当前无安全层，本次不加 Spring Security 集成

## 验收线（粗粒度，不是 AC）

1. **Swagger UI 可访问**：dev 启动后访问 `/swagger-ui.html`，可见完整 API 文档，6 个 Tag 分组，19 个端点可展开查看参数和响应 schema
2. **openapi.json 可获取**：`/v3/api-docs` 返回完整 OpenAPI 3.0 规范 JSON，包含全部 6 个 Controller 的端点定义
3. **前端可直接对接**：前端人员仅凭 Swagger UI 即可完成接口调用，每个端点可见：路径、方法、参数（必填/可选+类型+示例）、请求体 schema、响应体 schema（含嵌套结构）、可能的错误码及含义

## 风险与未知

- **风险低**：SpringDoc 是成熟方案，依赖已存在，注解补齐是机械性工作，不影响运行时行为
- **泛型擦除**：`ApiResponse<T>` 和 `PageResult<T>` 的泛型 T 在 Swagger UI 中可能显示为 `object`。SpringDoc 2.6 对 Java record 的泛型解析支持较好，但仍需实测验证。若自动解析不理想，为泛型端点添加 `@ApiResponse(content = @Content(schema = @Schema(implementation = ...)))` 显式声明
- **`@Data` vs `record`**：项目混用 Lombok `@Data`（部分 VO）和 Java `record`（部分 VO + ApiResponse/PageResult）。SpringDoc 对两者均支持，但 `record` 的字段名识别更可靠
- **MetricsController 参数绑定**：使用手动 `@RequestParam List<String>` + 自建 `MetricsQueryRequest`，非标准 `@ModelAttribute` 绑定。SpringDoc 可能无法自动发现 `nodeTypes`/`edgeTypes` 参数，需手动添加 `@Parameter` 注解

---

> 后续详细任务拆解进入 `TASK.md`，本文件不再扩展。