# CHANGE: 班级薄弱概览 — 智能问答新增第二意图

- **Change ID**: `class-weakness-overview`
- **创建日期**: 2026-06-23
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft（待用户确认）
- **用户决策**:
  - Q1 意图范围: A — 一个班级 + 一个学科，聚合全班 MASTERS 数据，按知识点统计薄弱人数/平均掌握度，输出班级整体薄弱知识点排行 + 共性根因分析
  - Q2 前端交互: C — 仅通过自然语言触发（LLM 意图识别自动区分），前端 UI 不变
  - Q3 输出格式: A — 沿用 HTML+SVG（默认）+ Markdown 可配置回滚

---

## Why（为什么做）

`llm-intent-recognition` 已落地了可插拔意图识别链 + 策略注册表 + HTML+SVG 输出，但只支持 `STUDENT_DIAGNOSIS` 一种意图。该 CHANGE 明确将 `CLASS_OVERVIEW` 列为 v2 范围：

> v2（下一轮考虑，不本次）: 启用注释中预留的 v2 意图（`KP_ANALYSIS`、`CLASS_OVERVIEW`、`PREREQUISITE_CHAIN`、`GENERAL`）及对应剪枝策略实现

现在教师用户需要**班级视角**的诊断能力：

- **当前痛点**：教师想知道"初三(1)班数学哪些知识点整体薄弱"，现有系统只能逐个学生诊断，无法聚合班级级别数据
- **使用场景**：教师问"分析初三(1)班数学薄弱知识点"，系统应自动识别为班级概览意图 → 聚合全班学生 MASTERS 数据 → 按知识点统计薄弱人数和平均掌握度 → 生成排行 + 共性根因分析报告
- **与 STUDENT_DIAGNOSIS 互补**：学生诊断是"点"（个体深度分析），班级概览是"面"（群体广度概览），教师先看班级整体薄弱点 → 再对有问题的学生做个体诊断

本 change 复用 `llm-intent-recognition` 的全部可插拔基础设施（`IntentRecognitionStrategy` 链 + `PruningStrategyRegistry`），新增意图只需「加枚举 + 加策略 + 加模板」，核心链路零改动。

## What（做什么）

### 1. 新增 QueryIntent 枚举值 `CLASS_WEAKNESS_OVERVIEW`

```
QueryIntent 枚举新增：
  CLASS_WEAKNESS_OVERVIEW(
    "班级薄弱概览",
    "聚合全班学生在指定学科上的 MASTERS 数据，统计薄弱知识点排行，分析共性根因"
  )
```

同时更新 `intent-classification-system.md` 的 few-shot 示例和 `KeywordIntentRecognitionStrategy` 的关键词 Map（新增"班级""全班""某班"等关键词）。

### 2. 新增 ClassWeaknessOverviewStrategy 剪枝策略

实现 `SubgraphPruningStrategy` 接口，Bean name = `"CLASS_WEAKNESS_OVERVIEW"`。剪枝逻辑：

```
输入: className + subject
Step 1: 从 MySQL exam_record 查询该班级所有学生（DISTINCT studentNo）
Step 2: 查询每个学生的 MASTERS 边（或降级走 TESTED 路径计算原始得分率）
Step 3: 按 KP 聚合：统计每个知识点的薄弱人数（weight < 阈值）、平均掌握度、最低掌握度
Step 4: 对薄弱 KP 展开 PREREQUISITE_OF 前置依赖链（≤2 跳）
输出: PrunedSubgraph（班级节点 + KP 节点 + 聚合 MASTERS 边 + 前置依赖边 + 聚合元数据）
```

与 `StudentDiagnosisStrategy` 的关键区别：
- 实体解析：`className` 而非 `studentNo`
- 聚合层：按 KP groupBy 而非单 student→KP 映射
- 输出：班级级聚合数据（薄弱人数、平均掌握度）而非单个学生的掌握度

### 3. 新增 4 个 Prompt 模板文件

| 文件 | 用途 |
|------|------|
| `class-weakness-overview-system.md` | Markdown 版 system prompt：班级诊断专家角色 + 分析框架 + 输出格式约束 |
| `class-weakness-overview-user.md` | Markdown 版 user prompt：班级子图数据注入 + 用户问题 |
| `class-weakness-overview-system-html.md` | HTML+SVG 版 system prompt：含 SVG 图表约束（班级掌握度分布柱状图、薄弱 KP 排行条形图、依赖链拓扑图） |
| `class-weakness-overview-user-html.md` | HTML+SVG 版 user prompt |

模板变量与 `STUDENT_DIAGNOSIS` 不同：

| 变量 | 说明 | 替换 student 版哪个变量 |
|------|------|------------------------|
| `{{className}}` | 班级名称（如"初三(1)班"） | `{{studentName}}` / `{{studentNo}}` |
| `{{classSize}}` | 班级学生总数 | 新增 |
| `{{subject}}` | 学科 | 不变 |
| `{{subgraphText}}` | 聚合子图文本 | 不变（但内容格式不同） |
| `{{userQuestion}}` | 用户原始问题 | 不变 |
| `{{weakThreshold}}` | 薄弱阈值 | 不变 |
| `{{maxHops}}` | 最大依赖跳数 | 不变 |
| `{{mastersAvailable}}` | MASTERS 是否可用 | 不变 |

### 4. QueryServiceImpl 适配班级级查询

当前 `QueryServiceImpl` 以"学生"为唯一实体假设，需做以下适配：

**4a. 实体解析分支**：新增 `resolveClass(className)` 方法，从 MySQL `exam_record` 查询班级是否存在 + 获取学生人数。`ask()` 中按 intent 分支：
- `STUDENT_DIAGNOSIS` → 现有 `resolveStudent()` 流程
- `CLASS_WEAKNESS_OVERVIEW` → 新增 `resolveClass()` 流程

**4b. 实体提取扩展**：`chat()` 的 LLM 实体提取 prompt 和正则兜底需支持提取 `className`（如从"初三(1)班"中提取）。提取逻辑：
- LLM prompt 新增 `className` 字段（与 `studentName`/`studentNo` 互斥，至少提取一组）
- 正则兜底新增班级名模式匹配（如 `XX(X)班`）

**4c. 子图序列化**：新增 `serializeClassSubgraph()` 方法，输出格式改为班级聚合视图（班级信息 + KP 排行表 + 前置依赖链），与现有 `serializeSubgraph()`（学生视图）并列。

**4d. 模板变量组装**：新增 `buildClassTemplateVars()` 方法（className/classSize/subject 等），替代学生版变量。

### 5. 数据层补充

- `ExamRecordRepository` 新增：`findDistinctStudentsByClassName(classname)` — 查询班级所有学生
- `QueryGraphRepository` 新增（可选）：`findStudentsByClassName(classname)` — 从 Neo4j 查班级学生节点
- 无需新增 MySQL 表或 Neo4j 节点类型

### 6. 前端变更

Q2=C 决定了前端 UI 不变，但仍有最小变更：

- `frontend/src/api/types.ts`：`QueryAskResponse` 中 `intent` 字段可能为 `CLASS_WEAKNESS_OVERVIEW`（类型定义已为 `string`，无需修改）
- 无需新增页面、组件或路由
- 现有 `HtmlSvgViewer` / `MarkdownViewer` 自动适配新意图的报告渲染

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增班级薄弱概览的 AC
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① 班级级剪枝策略的 Cypher 查询与聚合逻辑；② QueryServiceImpl 的意图分支重构方案（实体解析 + 序列化 + 模板变量）；③ chat() 实体提取的班级名识别扩展；④ 聚合子图的序列化格式
- [ ] 影响现有 AC — `llm-intent-recognition` 的 AC 全部保持通过。班级概览为新增功能，不影响现有 STUDENT_DIAGNOSIS 链路。回归验证：现有诊断 AC 全部照常通过
- [ ] 影响数据模型 / 迁移 — 无 schema 变更。复用既有 `query_task` 表（`intent` 字段存 `CLASS_WEAKNESS_OVERVIEW`）
- [x] 影响外部 API 兼容性 — `QueryAskResponse.intent` 新增可能值 `"CLASS_WEAKNESS_OVERVIEW"`，旧客户端忽略不报错。`QueryAskRequest` 可能需要新增可选 `className` 字段（`/ask` 端点显式调用时使用），可选字段向后兼容
- [ ] 影响前端依赖 — 无新增依赖
- [x] 影响 prompt 模板 — 新增 4 个文件：`class-weakness-overview-{system,user}.md` + `class-weakness-overview-{system,user}-html.md`。修改 1 个文件：`intent-classification-system.md`（新增 CLASS_WEAKNESS_OVERVIEW 的 few-shot 示例）
- [ ] 仅修复 bug，无范围变化

## 核心设计约束（进入 DESIGN 前必须遵守）

- **复用可插拔基础设施**：意图识别走既有 `IntentRecognitionService` 策略链（LLM → keyword fallback），剪枝走既有 `PruningStrategyRegistry`。不修改 `IntentRecognitionStrategy` 接口、`IntentRecognitionService` 编排器、`PruningStrategyRegistry` 路由逻辑
- **QueryServiceImpl 意图分支**：`ask()` 按 intent 分支选择实体解析器（student vs class）+ 序列化器 + 模板变量。不引入"实体解析策略接口"——仅 2 种意图，接口是过度工程（与 DESIGN D2 的"仅 2 种格式不引入 Strategy 接口"逻辑一致）
- **PruningRequest 语义扩展**：`entityId` 字段对 STUDENT_DIAGNOSIS = studentNo，对 CLASS_WEAKNESS_OVERVIEW = className。record Javadoc 更新为"目标实体标识（如 studentNo 或 className）"，不新增字段
- **聚合在 Java 层完成**：班级级 KP 聚合（groupBy + 统计薄弱人数/平均掌握度）在 `ClassWeaknessOverviewStrategy` Java 层完成，不走 Neo4j Cypher 聚合（与 ADR-010 的"分步 Cypher + Java 合并"模式一致）
- **班级识别优先 LLM**：`chat()` 实体提取的班级名识别走 LLM-first + 正则 fallback（与现有 `extractViaLlm` → `extractViaRegex` 模式一致）
- **输出格式完全复用**：班级概览的 HTML+SVG/Markdown 输出切换、校验、重试机制完全复用 `llm-intent-recognition` 的 D2/D3/D4 决策，仅 prompt 模板内容不同
- **遵循既有四层架构 + 构造器注入**。新代码放 `application/analysis/strategy/ClassWeaknessOverviewStrategy.java`（剪枝策略）+ `src/main/resources/prompts/class-weakness-overview-*.md`（模板）

## 范围排除（这次不做）

- ❌ **新增其他 v2 意图**：`KP_ANALYSIS`、`PREREQUISITE_CHAIN`、`GENERAL` 仍预留 v2+
- ❌ **跨学科班级概览**：v1 仅支持单个学科 + 单个班级。不支持"初三(1)班全科概览"
- ❌ **多班级对比**：不支持"对比初三(1)班和初三(2)班数学"
- ❌ **班级概览的异步模式**：v1 仅支持同步 `/ask` 和 `/chat`，`/ask-async` 后续扩展
- ❌ **班级内学生分群**：不按掌握度将学生分为"严重薄弱/中等/良好"组输出
- ❌ **班级概览历史导出**：不新增班级概览专属的导出格式（复用现有 HTML 导出端点）
- ❌ **className 自动补全/模糊匹配**：班级名必须精确匹配（如"初三(1)班"），不做模糊搜索或候选列表返回
- ❌ **前端 UI 新增**：Q2=C，不新增页面/组件/路由。不修改 `IntelligentQAPage.vue` 布局
- ❌ **实体解析策略接口**：不引入 `EntityResolutionStrategy` 接口。仅 2 种实体类型（student/class），在 `QueryServiceImpl` 内 if-else 分支足够

## 验收线（粗粒度，不是 AC）

1. **LLM 意图识别**：输入"分析初三(1)班数学薄弱知识点"，LLM 分类为 `CLASS_WEAKNESS_OVERVIEW` → 走班级概览链路。输入"初三(1)班数学哪些知识点需要加强"，同样识别为班级概览
2. **意图区分**：输入"分析张三数学薄弱点" → `STUDENT_DIAGNOSIS`（不被班级概览误判）。输入"初三(1)班数学" → `CLASS_WEAKNESS_OVERVIEW`
3. **班级剪枝**：班级存在且有 MASTERS 数据时，返回含班级所有学生薄弱 KP 聚合数据的子图（含薄弱人数、平均掌握度、最低掌握度）
4. **LLM 班级分析报告**：HTML+SVG 输出包含：班级整体掌握度概览表 + 薄弱知识点排行（柱状图/条形图） + 共性根因分析 + 教学建议
5. **学生诊断不退化**：现有 STUDENT_DIAGNOSIS 全链路行为不变，AC-1 到 AC-12 全部照常通过
6. **关键词 fallback**：LLM 不可用时，含"班级""全班"关键词的问题仍能识别为 CLASS_WEAKNESS_OVERVIEW
7. **班级不存在**：输入不存在的班级名 → HTTP 400 + 错误码 A0006（"未找到班级: XXX"）

## 风险与未知

- **LLM 意图区分边界模糊**："分析初三(1)班张三的数学"含班级名又含学生名 → LLM 可能误判。需在 few-shot 中明确优先级：含明确学生姓名 → STUDENT_DIAGNOSIS；仅含班级名不含学生名 → CLASS_WEAKNESS_OVERVIEW
- **班级聚合数据量大**：一个班级可能 40-50 个学生 × 20+ KP = 大量 MASTERS 边。聚合计算在 Java 层，需关注内存和耗时。通过限制班级大小（最多 60 人）和 KP 数量（Top 20 薄弱 KP）控制
- **MASTERS 降级路径的班级语义**：MASTERS 不存在时，班级概览降级到 TESTED 路径计算原始得分率。但 TESTED 路径需要按学生遍历，N+1 查询问题可能比学生诊断更严重（班级 N 个学生）。需在 QueryGraphRepository 中新增批量查询方法
- **班级名无统一规范**：不同学校班级名格式不同（"初三(1)班" / "九年级1班" / "9年1班"）。LLM 实体提取可能漏提或误提。通过 few-shot + 正则兜底（`XX年级?X+班` 模式）减少遗漏
- **token 消耗**：班级子图序列化文本可能比学生子图更长（多个学生的聚合数据），需控制输出粒度（仅输出聚合统计，不输出每个学生的明细）

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。