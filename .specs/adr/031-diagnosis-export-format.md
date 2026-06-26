# ADR-031: 诊断报告导出格式 — 单条原样输出 + 批量 Excel

- **状态**: accepted
- **日期**: 2026-06-22
- **Change**: `diagnosis-history-export`
- **关联**: DESIGN §1 D3, D4, D5, D7

---

## Context

诊断报告导出分为两种场景：

1. **单条导出**：用户想保存某次诊断的完整 LLM 分析报告
2. **批量导出**：用户想将筛选后的历史记录列表导出为可编辑的表格文件

`query_task.answer` 字段存储的是 LLM 生成的最终格式文本——可能是 Markdown（`query.output-format=markdown` 时）或 HTML+SVG 片段（`query.output-format=html-svg` 时）。导出时需考虑：
- 文件格式应尽量保留原始分析内容，便于阅读
- 批量时不应包含 `answer` 正文（MEDIUMTEXT，单条可能数十 KB），否则 Excel 单元格超大文本不可用
- 项目已有 Apache POI 5.2.5，可生成 `.xlsx`

## Decision

| 导出类型 | 格式 | Content-Type | 文件名 | 内容 |
|---------|------|-------------|--------|------|
| 单条 | 原样输出 | `text/markdown` 或 `text/html` | `diagnosis-{taskId前8位}.md` 或 `.html` | `query_task.answer` 字段原文本 |
| 批量 | Excel `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `diagnosis-history-{YYYYMMDD}.xlsx` | 9 列（提问时间/问题/学生姓名/学号/学科/状态/意图/Token用量/耗时），不含 answer |

**格式判定逻辑**（单条导出）：
```java
String answer = task.getAnswer();
boolean isHtml = answer != null && answer.trim().startsWith("<");
String contentType = isHtml ? "text/html; charset=UTF-8" : "text/markdown; charset=UTF-8";
String extension = isHtml ? ".html" : ".md";
```

**文件下载方式**：统一用 `StreamingResponseBody` 异步写 `ServletOutputStream`。

**Excel 生成**：用 `SXSSFWorkbook(100)` 流式写，仅保留 100 行在内存，其余写临时磁盘文件。

## Consequences

- **正面**：① 单条导出保留 LLM 原文格式，与页面展示一致，可用浏览器（.html）或 Markdown 编辑器（.md）打开；② 批量 Excel 可被 Excel/WPS/Google Sheets 打开，便于进一步分析；③ 零新增依赖，复用既有 POI；④ `StreamingResponseBody` 不阻塞 Tomcat 线程。
- **负面**：① 格式判定依赖首字符检测（`<` → HTML，`#` → Markdown），极端情况下可能误判（Markdown 文本以 HTML 标签开头）；`answer` 经 prompt 约束首字符（HTML 模式必须以 `<` 开头，Markdown 模式必须以 `#` 开头），误判概率极低；② 单条导出的 HTML 文件不包含 `<html><head><body>` 包装，只是一个 HTML 片段，在某些编辑器里预览可能不完整；③ `StreamingResponseBody` 写入失败时无法改 HTTP 状态码——缓解：写流前做所有预校验（taskId 存在、answer 非空）。
- **长期**：若用户强烈要求统一导出 PDF，需引入 HTML→PDF 渲染引擎（如 Flying Saucer + OpenPDF）或 Markdown→PDF 转换器，替换 ADR-031 的单条导出方案。PDF 渲染涉及中文字体嵌入、分页、SVG 渲染等复杂问题，工作量显著大于当前方案。