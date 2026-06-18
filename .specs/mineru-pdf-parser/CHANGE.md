# CHANGE: 接入 MinerU API 作为主 PDF 解析器（v1 + v4 可切换）

- **Change ID**: `mineru-pdf-parser`
- **创建日期**: 2026-06-16
- **路径建议**: 中等（REQUIREMENT 增量 → TASK → DEV → TEST → REVIEW → INTEGRATION）
- **状态**: active

---

## Why（为什么做）

当前 `PdfBoxDocumentParser` 使用 Apache PDFBox 做纯文本提取（`PDFTextStripper.setSortByPosition(true)`），
**无法正确识别数学公式**（公式被提取为乱码或丢失），导致后续 LLM 图谱抽取环节拿到的文本缺失公式信息，
抽出的 `EntityNode` 中公式类实体质量差。这是用户可感知的功能缺陷。

MinerU 是专为文档版面分析设计的工具，支持公式识别（`enable_formula`）、表格识别、OCR，
输出结构化 Markdown。项目 `CONTEXT.md` 术语表中已将 MinerU 列为"主用方案"，PDFBox 为"兜底"，
`DocumentParser` 接口的 Javadoc 也已预留 `MinerUDocumentParser` 扩展点 — 现在是时候实现了。

## What（做什么）

### 1. 新增 `MinerUDocumentParser`

实现 `DocumentParser` 接口。通过 MinerU v1 Agent API（默认）完成签名上传→轮询→下载 Markdown 的完整链路。
支持通过 `mineru.api.version` 切换为 v4 精准解析 API。

### 2. 可扩展 API 客户端架构（策略模式）

- `MinerUApiClient` — 策略接口（`submitTask` / `pollTaskResult` / `downloadResult`）
- `MinerUV1Client` — v1 Agent API（免 Token，默认）
- `MinerUV4Client` — v4 精准解析 API（需 Token，zip 解压）
- `MinerUClient` — 统一入口，注入 `List<MinerUApiClient>` 自动发现，按 `mineru.api.version` 匹配
- **扩展方式**：新增 `@Component implements MinerUApiClient`，返回自定义 `getVersion()`，零代码改动

### 3. 兜底策略

```
MinerU 任意异常（网络/超时/state=failed/轮询超时）
  → WARN 日志 + 自动 fallback 到 PdfBoxDocumentParser
  → PDFBox 也失败 → FAILED（含双重错误原因）
  → mineru.enabled=false → 直接 PDFBox
```

### 4. HTTP 客户端

使用 Spring 6.1 `RestClient`（`spring-web` 已包含），**无新增 pom.xml 依赖**。
文件上传至 OSS 预签名 URL 时清空所有请求头（`requestInterceptor` 清空 headers + 仅保留 Content-Length）。

### 5. 配置项（在 `application-dev.yml`）

```yaml
mineru:
  enabled: true
  api:
    version: v1                        # v1（默认）/ v4 / custom
    base-url: https://mineru.net
    submit-path: /api/v1/agent/parse/file
    poll-path-template: /api/v1/agent/parse/{taskId}
    token: ${MINERU_API_TOKEN:}
    parser-name: mineru-v1
    poll-timeout: 300s
    poll-interval: 3s
  parse:
    enable-formula: true
    enable-table: true
    language: ch
```

### 6. `ParseResult.metadata` 增加解析器标记

`metadata.parser` 字段记录实际使用的解析器（`mineru-v1` / `pdfbox-fallback` / `pdfbox-direct`），
持久化到 MySQL `metadata_json` 列，可用于统计 MinerU 使用率。

## 影响面

- [x] 影响 `REQUIREMENT.md`（新增 AC：MinerU 公式识别 + fallback 行为）
- [ ] 影响 `DESIGN.md` / 引入新 ADR（策略模式在 CHANGE 级决策，无需独立 ADR）
- [ ] 影响现有 AC（不修改已有 AC，只新增）
- [ ] 影响数据模型 / 迁移（`ParseResult` 不变，`DocumentDO` 不变）
- [ ] 影响外部 API 兼容性（Controller 接口不变）
- [x] 使用 Spring 6.1 `RestClient`（`spring-web` 已包含，**无需新增 pom.xml 依赖**）
- [x] 新增配置项：`application-dev.yml` 中 `mineru` 配置块
- [x] `DocumentServiceImpl` 构造器注入从 `DocumentParser` 改为 `MinerUDocumentParser` + `PdfBoxDocumentParser` + `MinerUProperties`

## 范围排除（这次不做）

- **不改造 `FileParser` 接口**（`DocumentParser` 和 `FileParser` 的统一留待独立 refactor change）
- **不支持批量解析**（单个 PDF 逐个提交）
- **不使用 callback 模式**（走轮询）
- **不修改 `DocumentParser` 接口契约**（仍然是 `byte[] → ParseResult`）

## 验收线（粗粒度，不是 AC）

- 含公式的 PDF 经 MinerU 解析后，`textContent` 中公式以 Markdown LaTeX 格式保留（如 `$E=mc^2$`）
- MinerU 任意异常 → 自动 fallback PDFBox，文档 COMPLETED
- MinerU + PDFBox 双失败 → FAILED，含双重错误原因
- `mineru.enabled=false` → 直接走 PDFBox，零 MinerU HTTP 请求
- 现有 PDFBox 单解析路径测试全部通过
- 切换 `mineru.api.version=v4` + 配置对应路径 → 走 v4 解析

## 风险与未知

- **v1 API 限制**：v1 Agent API 限制 ≤10MB / ≤20 页。超限文件 MinerU 返回 failed，自动 fallback PDFBox。
  若反馈量大，切换到 `mineru.api.version=v4` 即可（≤200MB / ≤200 页，需 Token）
- **mineru.net 可达性**：MinerU OSS 上传地址需从服务端网络可达。不可达时全部走 PDFBox 兜底
- **轮询延迟**：异步解析 + 轮询，单个 PDF 耗时 10~60s（正常路径），超时后触发 PDFBox 兜底

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。