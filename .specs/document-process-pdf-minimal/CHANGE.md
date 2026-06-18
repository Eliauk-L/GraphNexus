# CHANGE: 文档处理模块 — PDF 上传/解析/管理最小化实现

- **Change ID**: `document-process-pdf-minimal`
- **创建日期**: 2026-06-11
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

`docs/design-view/dev-view/dev-view.md` 已定义文档处理模块为整个 GraphNexus 系统的**数据入口**——所有 PDF 教辅通过文档处理进入系统，后续图构建、图分析、智能查询全部依赖其输出。`init-platform` 已搭建四层包结构骨架和公共模块，但 `application/document/service/`、`api/document/controller/`、`infrastructure/storage/` 尚为空壳。

没有文档处理模块，下游所有模块都无法启动开发。这是首个业务模块，必须优先落地。

## What（做什么）

基于既有四层架构和 `dev-view.md` § 2.3.1 定义的文档处理子模块，实现 **PDF 文件上传、解析、管理** 的最小可用链路：

1. **PDF 上传**：L1 REST API 接收 PDF 文件 → L2 Service 校验（类型、大小）→ 存入 MinIO → 记录元数据到 MySQL
2. **PDF 解析**：使用 Apache PDFBox 提取 PDF 文本内容、页数、元信息；定义可扩展的 `DocumentParser` 接口，为后续切换 MinerU 预留插槽
3. **文档管理**：文档 CRUD、状态流转（UPLOADED → PARSING → PARSED → FAILED）、列表查询/分页
4. **L1 + L2 全链路**：`api/document/controller/` + `application/document/service/` 完整实现
5. **基础设施连接**：连接真实 MinIO（文件存储）+ MySQL（文档元数据表），不再使用 stub

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增功能需求（PDF 上传/解析/管理的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计 DocumentParser 可扩展接口、MySQL 表结构、MinIO Bucket 策略
- [ ] 影响现有 AC — 无已有 AC，不冲突
- [x] 影响数据模型 / 迁移 — 新增 `document` 表（MySQL），需 DDL
- [x] 影响外部 API 兼容性 — 新增 REST API 端点（`/api/v1/document/*`），仅增量不破坏
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- ❌ **图构建**：不将解析结果写入 Neo4j 知识图谱（那是 `图处理` 模块的事）
- ❌ **实体/关系抽取**：不做 NLP 抽取、概念提取、公式识别（PDFBox 只做文本提取）
- ❌ **MinerU 集成**：PDFBox 为首个实现，`DocumentParser` 接口预留扩展点，但不接入真实 MinerU
- ❌ **数据源管理**：不做 CSV 导入、数据源注册（那是文档处理模块的另一个独立子模块）
- ❌ **文档搜索/全文检索**：不做 Elasticsearch/Meilisearch 集成，仅 MySQL LIKE 查询
- ❌ **前端界面**：纯后端 API，不涉及 UI
- ❌ **异步解析**：解析在请求线程同步完成（后续可改为 MQ 异步），本次不引入消息队列
- ❌ **pom.xml 修改**：PDFBox、MinIO、MySQL 依赖已在 `init-platform` 阶段配置完毕，不新增依赖

## 验收线（粗粒度，不是 AC）

1. **PDF 上传存储**：通过 REST API 上传 PDF → MinIO 中可获取文件 → MySQL 中有对应记录
2. **PDF 解析可读**：上传后调用解析接口 → 返回 PDF 文本内容、页数、元信息（PDFBox 提取）
3. **文档生命周期可追踪**：文档状态在 UPLOADED → PARSING → PARSED（或 FAILED）完整流转，列表查询可翻页

## 风险与未知

- **PDFBox 中文兼容性**：PDFBox 对 CJK 字体提取可能存在乱码，需用中文 PDF 实测验证
- **MinIO 连接配置**：本地需启动 MinIO 容器或使用已有 MinIO 实例，需确认 `application-dev.yml` 中 endpoint/accessKey/secretKey 可用
- **MySQL 表结构**：`document` 表设计（字段、索引）需在 DESIGN 阶段确定，避免后期频繁迁移
- **大文件上传**：Spring Boot 默认 multipart 大小限制（1MB），需配置 `spring.servlet.multipart.max-file-size`

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。