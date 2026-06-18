# ADR-003: Controller 拆分 — FileController（统一上传入口）+ GradeController（查询/删除）

- **状态**: accepted
- **日期**: 2026-06-18
- **Change**: `extensible-file`
- **决策者**: AI Architect + 人工 review

---

## Context

当前 `FileController`（`/api/v1/file/document`）同时承载文档文件（PDF）和成绩文件（CSV）的端点。Controller 内部通过 `FileParserRegistry.getParser(filename)` 返回类型 + **字符串比较**判断路由：

```java
if (parser.isPresent() && parser.get().supportedType().name().equals("CSV_GRADE")) {
    return gradeService.uploadGradeCsv(file, subject);   // → GradeUploadResultVO
}
// 默认走 PDF 链路
return fileService.upload(file, subject);                // → FileVO
```

问题：
1. **职责混淆**：一个 Controller 返回两种不同响应类型（`FileVO` vs `GradeUploadResultVO`），`upload()` 返回类型为 `ApiResult<?>` 通配符
2. **字符串比较**：`name().equals("CSV_GRADE")` — 枚举重命名时静默断裂
3. **成绩端点混杂**：`GET/DELETE /grade/exam/{examNo}` 与文档 CRUD 混在同一 Controller
4. **需求要求**：成绩列表走独立端点，不在 `GET /api/v1/file/document` 中混合

## Decision

### 统一上传入口 + 职责拆分

**`FileController`（`/api/v1/file/document`，保持不变）** — 所有文件类型的**统一上传入口**：

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|----------|------|
| POST | `/upload` | `MultipartFile + subject` → `ApiResult<?>` | **统一上传入口**（PDF/TXT/CSV），通过 Registry → Pipeline 路由 |
| GET | (root) | `?pageNum&pageSize&file_type&name` → `ApiResult<PageResult<FileVO>>` | 文档列表，支持条件筛选 |
| GET | `/{id}` | → `ApiResult<FileVO>` | 文档详情 |
| PUT | `/{id}` | `UpdateFileRequest` → `ApiResult<FileVO>` | 重命名 |
| DELETE | `/{id}` | → `ApiResult<Void>` | 级联删除 |
| POST | `/{id}/process` | → `ApiResult<ParseResultVO>` | 手动触发处理（断点续跑） |

**`GradeController`（`/api/v1/file/grade`）** — 成绩查询/删除，**不提供上传端点**：

| 方法 | 路径 | 请求/响应 | 说明 |
|------|------|----------|------|
| GET | (root) | `?pageNum&pageSize` → `ApiResult<PageResult<GradeUploadResultVO>>` | 成绩列表分页 |
| GET | `/exam/{examNo}` | → `ApiResult<List<GradeRecordVO>>` | 按考试编号查询成绩 |
| DELETE | `/exam/{examNo}` | → `ApiResult<DeleteResultVO>` | 级联删除成绩 |

### 上传路由逻辑

```
FileController.upload(file, subject):
  parser = fileParserRegistry.getParser(filename)
  if (parser.isEmpty) → 400 "不支持的文件类型"
  
  type = parser.get().supportedType()
  return switch (type):
    DOCUMENT  → documentPipeline.process(file, subject)  // → FileVO
    CSV_GRADE → gradePipeline.process(file, subject)      // → GradeUploadResultVO
```

改用 **枚举 switch**，消除字符串比较。

### 端点路径迁移

| 旧路径 | 新路径 | 迁移说明 |
|--------|--------|---------|
| `POST /api/v1/file/document/upload` | `POST /api/v1/file/document/upload` | **不变**（统一入口） |
| `GET /api/v1/file/document` | `GET /api/v1/file/document` | **不变** |
| `GET /api/v1/file/document/{id}` | `GET /api/v1/file/document/{id}` | **不变** |
| `POST /api/v1/file/document/{id}/process` | `POST /api/v1/file/document/{id}/process` | **不变** |
| `GET /api/v1/file/document/grade/exam/{examNo}` | `GET /api/v1/file/grade/exam/{examNo}` | ⚠️ 迁入 GradeController |
| `DELETE /api/v1/file/document/grade/exam/{examNo}` | `DELETE /api/v1/file/grade/exam/{examNo}` | ⚠️ 迁入 GradeController |

> **注意**：`POST /api/v1/file/document/upload` 保持为所有文件类型的统一上传入口。`FileController` 内部通过 `FileParserRegistry` 查扩展名 → 枚举 switch 路由到 `DocumentProcessingPipeline` 或 `GradeProcessingPipeline`。不再使用 `name().equals("CSV_GRADE")` 字符串比较。

## Consequences

- **正面**：统一上传入口对调用方友好——前端只需记住一个上传 URL，文件类型由后端自动识别
- **正面**：枚举 switch 替代字符串比较，编译期安全
- **正面**：每个 Controller 职责单一，`GET /api/v1/file/document` 只返回文档列表，不会混入成绩数据
- **正面**：两个 Controller 可独立演进（如 GradeController 未来加日期范围筛选不影响 FileController）
- **负面**：旧 CSV 上传调用方（前端 SPA、CI 脚本、集成测试）需更新端点路径，短期有适配成本
- **负面**：端点路径变更（去掉 `/file` 段），已有前端代码需同步调整。缓解：前端为本次 change 的联动方，已在假设中声明
- **负面**：两个 Controller 共享底层 `FileService` 和 `GradeService`，但依赖同一套基础设施（MinIO、MySQL、Neo4j），基础设施变更会同时影响两者——这是合理的共享，不视为耦合