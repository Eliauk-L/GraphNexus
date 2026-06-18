# CHANGE: 文件上传可扩展架构

- **Change ID**: extensible-file
- **创建日期**: 2026-06-18
- **路径建议**: 完整
- **状态**: requirement-done

---

## Why（为什么做）

**痛点 1 — 上传链路硬编码**：当前文件上传接口的实现方式是硬编码分支：Controller 层 `if (CSV) → gradeService` else `默认走 PDF`；Service 层 `upload()` 硬编码 `application/pdf` MIME 校验；`process()` 硬编码 MinerU → PDFBox 回退链。同时存在两套互不统一的解析器接口（`FileParser` 和 `DocumentParser`），`FileParseType.PDF_DOCUMENT` 枚举已定义但无任何 `FileParser` 实现使用它。**新增一种文件类型（如 `.txt`）需要修改 Controller + Service + 可能新增接口方法，违反开闭原则。**

**痛点 2 — 列表查询无筛选能力**：当前 `GET /api/v1/file/document` 分页列表接口只接受 `pageNum` 和 `pageSize`，没有任何筛选参数。`file` 表缺少 `file_type` 字段，前线无法按文件类型筛选；也无法按文件名模糊搜索。随着文件类型和数量增长，用户只能逐页翻找，使用体验差。

## What（做什么）

### A. 文件上传可扩展架构

设计可扩展的文件上传处理架构，使新增文件类型只需添加对应处理器并注册，无需修改 Controller 和 Service 核心逻辑。具体包含：

1. 统一文件处理抽象：定义 `FileProcessingPipeline` 接口，封装"上传 → 解析 → 入库 → 图谱"全链路
2. **同步链路设计**：文档上传后自动串联执行 解析 → 图谱抽取 → 融合，一次请求完成全链路，无需手动逐步触发
3. **处理状态扩展**：当前 `FileStatus`（`UPLOADED → PROCESSING → COMPLETED/FAILED`）不足以表达同步链路的各阶段。需扩展状态机，覆盖解析中/抽取中/融合中等中间状态，使失败时能定位到具体步骤并支持从断点重试
4. **保留手动接口**：同时保留 `POST /{id}/process`（单独重试解析）、`POST /fusion/execute`（手动触发融合）等独立操作端点，用于异常恢复和手动干预
5. PDF 和 TXT 共享同一文档处理链路（Document Pipeline）：解析文本 → 存 MinIO → 写入 `file` 表 → LLM 知识图谱抽取
6. CSV 成绩文件保留独立处理链路（Grade Pipeline），走独立的 `GradeController` 和 `GradeService`。**上传入口完全拆分**：文档上传走 `POST /api/v1/file/document/upload`，成绩上传走 `POST /api/v1/file/grades/upload`，两个端点职责清晰、互不耦合
7. 当前 `DocumentController` 拆分为 `FileController` + `GradeController`，职责清晰
8. 保留未来新增第三种、第四种处理链路的扩展能力（不仅限于加新解析器，而是可以加全新业务链路）
9. 首次新增文件类型：`.txt` 文本文件，UTF-8/GBK 编码，走文档处理链路

### B. 分页列表条件查询 + 接口拆分

1. **Controller 拆分**：将当前 `DocumentController` 拆为两个独立 Controller：
   - **`FileController`** — 文档类文件（PDF、TXT 及未来文档格式）：上传、列表查询、获取详情、更新、删除、触发解析
   - **`GradeController`** — 成绩类文件（CSV）：上传、列表查询、按考试编号查询、删除
2. **分页条件查询**：`GET /api/v1/file/document`（FileController）支持以下可选筛选参数：
   - `file_type`：精确匹配（如 `DOCUMENT`、`CSV_GRADE`），依赖 `file` 表新增 `file_type` 字段
   - `name`：文件名模糊搜索（LIKE 匹配）
   - 不传筛选参数时行为与当前完全一致
3. **成绩列表**：`GET /api/v1/file/grade`（GradeController）独立分页查询，不混入文档列表
4. 保持现有分页参数（`pageNum`、`pageSize`）不变

## 影响面

- [x] 影响 `REQUIREMENT.md`（新增 TXT 文件类型处理能力 + 分页条件查询功能 + Controller 拆分）
- [x] 影响 `DESIGN.md` / 引入新 ADR（可扩展文件处理架构设计决策 + 条件查询设计 + API 端点拆分决策）
- [x] 影响现有 AC（Controller 拆分为 FileController + GradeController，上传端点路径可能调整）
- [x] 影响数据模型 / 迁移（`file` 表新增 `file_type` 字段 + `status` 枚举扩展新状态值；需 DDL 变更 + `FileDO`/`FileStatus` 更新）
- [x] 影响外部 API 兼容性（需评估现有端点路径是否保留兼容映射，还是前端同步更新）

## 范围排除（这次不做）

- 不新增 PDF、CSV、TXT 以外的文件类型
- 不修改 CSV 成绩处理的核心业务逻辑（只从 DocumentController 拆出独立 GradeController + 接口适配）
- 不改动已有 API 的请求/响应结构（新参数均为可选，不影响已有调用方）
- Controller 拆分后，文档文件上传使用 `POST /api/v1/file/document/upload` 端点；CSV 成绩上传使用独立端点 `POST /api/v1/file/grades/upload`，两者完全解耦
- 不改变 MinIO 存储路径结构
- 不引入 OSGi/SPI 等重量级插件框架（只用 Spring 依赖注入 + 策略模式）
- 不实现全文检索（文件名模糊搜索用 MySQL LIKE，不接入 Elasticsearch）
- 不实现多条件组合的高级搜索（本次仅支持 `file_type` + `name` 两个独立筛选条件，不做 AND/OR 组合表达式）
- 不分页的成绩查询（`GET /api/v1/file/grades/exam/{examNo}` 保持现状，不在本次改造范围）

## 验收线（粗粒度，不是 AC）

- 新增一种文件类型只需：① 实现解析器接口 + ② 注册到处理器注册表，Controller 和 Service 核心代码零改动
- `.txt` 文件上传后，系统自动识别为文档类型，走与 PDF 相同的"解析 → 存储 → LLM 抽取"全链路，最终可在图谱中查询到抽取的知识点
- **同步链路**：`POST /api/v1/file/document/upload` 返回时，文件已完成"上传 → 解析 → 图谱抽取 → 融合"全流程，文档状态为 `COMPLETED`，无需再手动调用 `/process`
- **手动接口保留**：`POST /api/v1/file/document/{id}/process` 仍可独立触发解析（用于解析失败后重试），融合接口仍可手动调用
- 现有 PDF 上传的全链路行为与重构前完全一致（回归测试通过）；CSV 成绩上传通过独立端点 `POST /api/v1/file/grades/upload`，行为与重构前一致
- `GET /api/v1/file/document?pageNum=1&pageSize=10&file_type=DOCUMENT&name=二次函数` 返回按文件类型和文件名联合筛选后的分页结果，不传筛选参数时行为与当前一致

## 风险与未知

- **接口设计风险**：`FileParser` 和 `DocumentParser` 两套接口如何统一，需要 DESIGN 阶段评估是合并还是新建适配层
- **TXT 解析质量**：纯文本文件缺少 PDF 的版面结构信息，LLM 抽取效果可能不如 PDF，需在 REQUIREMENT 阶段明确 AC 阈值
- **CSV 链路适配**：CSV 的 `uploadGradeCsv()` 当前由 `DocumentService` 代理调用 `GradeService`，重构为 Pipeline 模式时需要确保不破坏现有的 `GradeUploadedEvent` 事件发布和增量融合触发
- **`file_type` 回填**：现有 `file` 表已有 PDF 历史数据，需确定存量数据的 `file_type` 回填策略（脚本补填 vs. 代码默认值）
- **同步链路超时风险**：MinerU 解析（轮询可能 30s+）+ LLM 抽取（10-30s）+ 融合（若干秒）串联后单次请求可能 60-90s，需评估 HTTP 超时配置和用户体验（前端 loading 时长）。DESIGN 阶段需考虑是否引入异步模式作为补充
- **状态机设计**：当前 `FileStatus` 仅有 4 个状态（`UPLOADED/PROCESSING/COMPLETED/FAILED` + `DELETING`），同步链路需新增 `PARSING/EXTRACTING/FUSING` 等中间状态表示各阶段进度。状态转换规则（哪些跳转合法）、失败后从哪个状态恢复、手动操作允许在哪些状态触发，都需要 DESIGN 阶段精确定义
- **API 兼容性**：`DocumentController` 拆为 `FileController` + `GradeController` 后，文档和成绩各有独立上传端点（`POST /api/v1/file/document/upload` 和 `POST /api/v1/file/grades/upload`）。前端需同步更新以调用对应端点。旧 CSV 上传调用方（如有脚本/测试）需更新 URL
- **同步链路失败处理**：全链路中任一步骤失败（如 LLM 抽取超时），已完成的步骤（MinIO 上传、文本解析入库）如何回滚或保留中间状态，需在 DESIGN 阶段定义状态机和补偿策略

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。