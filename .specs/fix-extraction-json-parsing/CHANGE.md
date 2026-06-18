# CHANGE: LLM 抽取 JSON 解析鲁棒性增强

- **Change ID**: `fix-extraction-json-parsing`
- **创建日期**: 2026-06-16
- **路径建议**: 快速修复（`CHANGE → TASK → DEV → TEST`）
- **状态**: draft

---

## Why（为什么做）

`knowledge-graph-extraction` 中 `ExtractionService#parseResponse` 通过大模型进行实体提取时，JSON 解析失败率较高。LLM 本质上不是结构化输出器，即使 prompt 要求"仅输出纯 JSON"，仍可能产生尾逗号、单引号、注释、无引号字段名、多余包裹文字等非法 JSON，当前仅做了去 markdown 包裹 + 简单正则清理，覆盖面窄。

线上表现为 A0010 错误率偏高，影响文档抽取成功率。

## What（做什么）

分层防御 LLM JSON 格式问题：

1. **Jackson 宽松模式**：配置 ObjectMapper 支持尾逗号、注释、单引号、无引号字段名、未转义控制字符、忽略未知属性
2. **改进 JSON 提取算法**：用括号匹配替代简单正则 `^[^{]*` / `[^}]*$`，正确处理嵌套 `{}`；增加 BOM、JS 注释、截断标记的预处理清理
3. **保留原二次解析兜底**：宽松模式仍失败时走原激进正则 + 重试

## Scope

- 新建 `ExtractionJsonParser` 组件，封装 JSON 预处理 + 宽松解析逻辑
- 修改 `ExtractionService`，注入 `ExtractionJsonParser` 替代内联的 `parseResponse`/`preprocessJson`
- 修改 ObjectMapper 配置（新建一个宽松版 ObjectMapper Bean 或在组件内配置）