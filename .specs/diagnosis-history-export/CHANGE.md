# CHANGE: 学情诊断历史记录查询与导出

- **Change ID**: `diagnosis-history-export`
- **创建日期**: 2026-06-22
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

当前学情诊断页面（[IntelligentQAPage.vue](frontend/src/views/query/IntelligentQAPage.vue)）只支持单次问答，`history` 存储在 Pinia store 内存中，刷新即丢失。用户每次诊断后无法回溯历史分析结论，也无法将诊断报告导出为文件存档或分享。

后端 `query_task` 表已持久化每次问答的完整记录（问题/答案/学生/学科/token 用量/耗时/状态），但缺少：
- 前端历史列表 UI 和查询 API
- 单条诊断报告的导出下载能力
- 批量历史记录的筛选导出能力

## What（做什么）

在学情诊断页面增加**历史记录面板**（折叠式，位于输入框下方），支持：

1. **历史记录查询**：
   - 分页列表展示：提问时间 + 问题文本 + 学生 + 学科 + 状态
   - 筛选条件：学生姓名/学号、学科、状态（COMPLETED/FAILED）、时间范围
   - 点击单条可展开查看完整诊断报告

2. **单条诊断导出**：
   - 导出单条诊断的完整 LLM 分析报告为 HTML 文件（`.html`）

## 影响面

- [x] 影响 `REQUIREMENT.md` — 全新功能需求（历史查询/导出 API + 前端交互的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① 历史查询 API 的筛选 + 分页协议；② 导出 API 的文件格式与流式下载方案；③ 前端 HistoryPanel 组件状态管理
- [ ] 影响现有 AC — 无已有 AC 冲突，本 change 是增量功能
- [x] 影响数据模型 / 迁移 — `QueryTaskRepository` 需新增带筛选条件的分页查询方法（JPA Specification 或 `@Query`）；**不新增表、不修改 `query_task` 表结构**
- [x] 影响外部 API 兼容性 — 新增 2 个 REST 端点，仅增量不破坏：
  - `GET /api/v1/query/history` — 历史记录分页查询
  - `GET /api/v1/query/history/{taskId}/export` — 单条报告 HTML 导出
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- ❌ **历史记录删除/编辑**：历史记录为日志类数据，只读不写不改不删
- ❌ **统计分析仪表盘**：不做"诊断次数趋势图""学科分布饼图"等可视化统计
- ❌ **全文搜索**：不对 `question`/`answer` 列做全文索引（MySQL LIKE 模糊搜索即可）
- ❌ **批量导出**：不做 Excel 批量导出
- ❌ **导出 PDF**：单条导出仅 HTML 原文件下载，不做 PDF 渲染转换
- ❌ **导出 Markdown**：导出固定为 HTML 格式
- ❌ **历史记录跨设备同步**：不做云端同步或跨浏览器历史共享
- ❌ **诊断结论对比**：不做"选择两条诊断记录对比分析"功能

## 验收线（粗粒度，不是 AC）

1. **历史列表可查**：用户在学情诊断页面展开历史面板，可看到分页的历史诊断记录列表，支持按学生/学科/状态/时间筛选
2. **单条报告可导出**：用户点击某条历史记录的"导出"按钮，浏览器下载一个 `.html` 文件，内容为该次诊断的完整 LLM 分析报告

## 风险与未知

- **Excel 导出依赖 Apache POI**：`pom.xml` 中已有 POI 5.2.5（来自 `csv-grade-import`），复用无新增依赖风险
- **大数据量导出**：若历史记录量很大（> 10 万条），批量导出 Excel 可能 OOM。v1 通过分页参数限制单次导出上限（如最多 5000 条），超出提示用户缩小筛选范围
- **answer 列大小**：`answer` 是 MEDIUMTEXT（最大 16MB），批量导出不包含此字段，避免 Excel 文件膨胀。单条导出直接流式输出原始文本
- **前端历史面板性能**：分页加载（每页 10~20 条），不一次性加载全量数据

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。