# ADR-017: Excel 解析库选型 — Apache POI

- **日期**: 2026-06-19
- **状态**: accepted
- **来源**: `grade-management-refactor` DESIGN

---

## Context

`grade-management-refactor` 需新增 Excel 格式（`.xlsx`/`.xls`）成绩文件解析能力，与现有 CSV 双行表头格式保持一致。需要在 Java 生态中选择一个 Excel 解析库。

备选方案：
- **方案 A**：Apache POI 5.2.x（`poi` + `poi-ooxml`）
- **方案 B**：EasyExcel（Alibaba，基于 POI 的流式封装）
- **方案 C**：JExcelApi（老牌库，仅支持 `.xls`）

## Decision

采用 **方案 A：Apache POI 5.2.5**。

选择理由：
1. **Java 生态事实标准**：Spring Boot 生态中最广泛使用的 Excel 库，社区成熟，文档丰富
2. **功能匹配场景**：项目成绩文件规模小（≤50 学生 × ≤30 题），POI 的 `XSSFWorkbook`（内存模式）完全够用，无需流式读取
3. **日期处理完善**：`DateUtil.isCellDateFormatted()` 可区分 Excel 日期数值和普通数字，配合 `CellType` 检测可同时兼容日期数值和字符串两种输入格式
4. **格式自动识别**：`WorkbookFactory.create(InputStream)` 自动识别 `.xls`（HSSF）和 `.xlsx`（XSSF），不需要调用方判断文件扩展名
5. **依赖轻量**：仅需 `poi` + `poi-ooxml` 两个 artifact（约 4MB），引入成本低

EasyExcel 的流式优势（逐行读取、低内存占用）在本场景（≤50 学生）中属于过度工程。JExcelApi 不支持 `.xlsx` 直接排除。

## Consequences

- **正面**：
  - Excel 解析器可复用 `GradeParsePayload` 作为输出，与 CSV 解析器统一后续链路
  - `WorkbookFactory.create()` 自动识别格式简化了 `ExcelGradeParser` 实现
- **负面**：
  - POI 的 `Cell` API 较底层，需手动处理空单元格、空白行、数值/字符串类型判断，代码量比 CSV 解析器多
  - `poi-ooxml` 打包进 uber-JAR 增加约 4MB 体积
- **风险**：POI 5.x 在读取某些 Excel 文件（如 WPS 生成的文件）时可能遇到兼容性问题。缓解：解析失败时返回明确错误信息（含错误码）而非静默吞异常