# T09-SUMMARY: GraphController + VO/DTO

- **Task ID**: T09
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 L1 API 层，3 个文件：

| 文件 | 职责 |
|------|------|
| ExtractionResultVO | 抽取摘要 VO（documentId + 4 计数） |
| GraphSubgraphVO | 子图响应 VO（nodes[] + edges[]，含内部类 GraphNodeVO/GraphEdgeVO） |
| GraphController | `POST /api/v1/graph/extract/{docId}` + `GET /api/v1/graph/document/{docId}` |

沿袭 DocumentController 风格：`@RestController` + `@RequiredArgsConstructor` + `ApiResponse<T>`。

## 改动文件（3 个新增）

- `api/graph/dto/ExtractionResultVO.java`
- `api/graph/dto/GraphSubgraphVO.java`
- `api/graph/controller/GraphController.java`

## verify

`mvn compile -q` — 通过

## 越界检查

TASK write_files: 3 项 | diff: 3 项 | 越界: 0 ✅

## 完成判定

两端点路径与 DESIGN § D6 一致，编译通过。