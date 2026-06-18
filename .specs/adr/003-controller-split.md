# ADR-003: Controller 拆分 — FileController + GradeController

- **状态**: accepted
- **日期**: 2026-06-18
- **Change**: `extensible-file`
- **决策者**: AI Architect + 人工 review

---

## Context

当前 `FileController`（`/api/v1/file/document`）同时承载文档文件（PDF）和成绩文件（CSV）的端点。Controller 内部通过 `FileParserRegistry.getParser(filename)` 返回类型判断路由：

```java
if (parser.isPresent() && parser.get().supportedType() == CSV_GRADE) {
    return gradeService.uploadGradeCsv(file, subject);   // → GradeUploadResultVO
}
// 默认走 PDF 链路
return fileService.upload(file, subject);                // → FileVO
```

问题：
1. **职责混淆**：一个 Controller 返回两种不同响应类型（`FileVO` vs `GradeUploadResultVO`），`upload()` 返回类型为 `ApiResult<?>` 通配符
2. **扩展受阻**：文档链路和成绩链路的处理逻辑、状态管理、列表查询完全不同，耦合在一个 Controller 中
3. **需求要求**：CSV 成绩文件走独立端点，不在 `GET /api/v1/document` 列表中混合

## Decision

拆分为两个独立 Controller：

### FileController（`/api/v1/document`）

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|----------|------|
| POST | `/upload` | `MultipartFile + subject` → `ApiResult<FileVO>` | 文档文件上传（PDF/TXT），走 DocumentProcessingPipeline |
| GET | (root) | `?pageNum&pageSize&file_type&name` → `ApiResult<PageResult<FileVO>>` | 分页列表，支持条件筛选 |
| GET | `/{id}` | → `ApiResult<FileVO>` | 文档详情 |
| PUT | `/{id}` | `UpdateFileRequest` → `ApiResult<FileVO>` | 重命名 |
| DELETE | `/{id}` | → `ApiResult<Void>` | 级联删除 |
| POST | `/{id}/process` | → `ApiResult<ParseResultVO>` | 手动触发处理（断点续跑） |

### GradeController（`/api/v1/grade`）

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|----------|------|
| POST | `/upload` | `MultipartFile + subject` → `ApiResult<GradeUploadResultVO>` | CSV 成绩上传 |
| GET | (root) | `?pageNum&pageSize` → `ApiResult<PageResult<GradeUploadResultVO>>` | 成绩列表分页 |
| GET | `/exam/{examNo}` | → `ApiResult<List<GradeRecordVO>>` | 按考试编号查询成绩 |
| DELETE | `/exam/{examNo}` | → `ApiResult<DeleteResultVO>` | 级联删除成绩 |

### 端点路径迁移

| 旧路径 | 新路径 | 迁移说明 |
|--------|--------|---------|
| `POST /api/v1/file/document/upload`（PDF） | `POST /api/v1/document/upload` | 路径简化，语义不变 |
| `POST /api/v1/file/document/upload`（CSV） | `POST /api/v1/grade/upload` | **CSV 调用方需更新** |
| `GET /api/v1/file/document` | `GET /api/v1/document` | 路径简化 |
| `GET /api/v1/file/document/{id}` | `GET /api/v1/document/{id}` | 路径简化 |
| `POST /api/v1/file/document/{id}/process` | `POST /api/v1/document/{id}/process` | 路径简化 |
| `GET /api/v1/file/document/grade/exam/{examNo}` | `GET /api/v1/grade/exam/{examNo}` | 迁入 GradeController |
| `DELETE /api/v1/file/document/grade/exam/{examNo}` | `DELETE /api/v1/grade/exam/{examNo}` | 迁入 GradeController |

> **注意**：当前 `FileController` 的 `@RequestMapping` 已经是 `/api/v1/file/document`。本次调整为去掉 `/file` 中间段，简化为 `/api/v1/document`。同时新增 `/api/v1/grade`。

## Consequences

- **正面**：每个 Controller 职责单一，返回类型明确（不再需要 `ApiResult<?>` 通配符），API 文档更清晰
- **正面**：两个 Controller 可独立演进，互不影响（如 GradeController 未来加日期范围筛选不影响 FileController）
- **正面**：`GET /api/v1/document` 只返回文档列表，不会混入成绩数据，前端无需在列表中判断类型
- **负面**：旧 CSV 上传调用方（前端 SPA、CI 脚本、集成测试）需更新端点路径，短期有适配成本
- **负面**：端点路径变更（去掉 `/file` 段），已有前端代码需同步调整。缓解：前端为本次 change 的联动方，已在假设中声明
- **负面**：两个 Controller 共享底层 `FileService` 和 `GradeService`，但依赖同一套基础设施（MinIO、MySQL、Neo4j），基础设施变更会同时影响两者——这是合理的共享，不视为耦合