# STATE — GraphNexus 跨会话状态

> 每次阶段切换或清窗时更新，供下一个会话快速恢复上下文。

---

## 当前活动

- **Change ID**: `graph-construction-refactor`
- **当前阶段**: CHANGE ✅ → REQUIREMENT ✅ → DESIGN ✅ → TASK ✅ → DEV ✅ → 下一步 TEST/REVIEW
- **当前角色**: Dev → 下一步 Reviewer
- **最后更新**: 2026-06-20

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | SQL DDL — DROP csv columns + index | `905542e` | ✅ |
| T02 | pom.xml — Apache POI 5.2.5 | `905542e` | ✅ |
| T03 | ErrorCode A0022 exam_no duplicate | `905542e` | ✅ |
| T04 | GradeFileType CSV + EXCEL split | `ea04746` | ✅ |
| T05 | CsvParsePayload → GradeParsePayload | `ea04746` | ✅ |
| T06 | ExamRecordDO csv field removal | `ea04746` | ✅ |
| T07 | Grade BO/VO csv field cleanup | `ea04746` | ✅ |
| T08 | CsvGradeParser adapt new enums | `0a08d13` | ✅ |
| T09 | ExcelGradeParser new | `0a08d13` | ✅ |
| T10 | GradeUploadedEvent + GradeDeletedEvent | `0a08d13` | ✅ |
| T11 | ExamRecordRepository refactor | `0a08d13` | ✅ |
| T12 | GradeUploadService event-driven | `0a08d13` | ✅ |
| T13 | GradeServiceImpl spec query + event | `0a08d13` | ✅ |
| T14 | GradeGraphEventListener new | `0a08d13` | ✅ |
| T15 | GradeUploadedEventListener @Order(2) | `0a08d13` | ✅ |
| T16 | GradeController conditional query | `0a08d13` | ✅ |
| T17 | CsvGradeParserTest adapt | `0a08d13` | ✅ |
| T18 | ExcelGradeParserTest new | `9b9ba7b` | ✅ |
- **最后更新**: 2026-06-19

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | TextbookRepository 5 条 @Query → 方法名派生 | `98fd00d` | ✅ |
| T02 | ExamRecordRepository 5 替换 + 1 改名 | `98fd00d` | ✅ |
| T03 | TextbookRepository 调用方更新（3 Service + 3 Test） | `396b61a` | ✅ |
| T04 | ExamRecordRepository 调用方更新（3 Service + 1 Strategy） | `396b61a` | ✅ |
| T05 | 全量测试验证 + AC 核验 | `396b61a` | ✅ |

## 上一个活动

- **Change ID**: `intelligent-qa`
- **当前阶段**: TASK → 等待 DEV
- **最后更新**: 2026-06-17

## 并行 Change

| Change ID | 阶段 | 状态 |
|-----------|------|------|
| `init-platform` | DEV → TEST | ✅ 9/9 任务完成 |
| `document-process-pdf-minimal` | DEV | ✅ 10/10 任务完成 |
| `knowledge-graph-extraction` | DEV | ✅ 11/11 任务完成，集成测试全通过 |
| `csv-grade-import` | DEV ✅ → TEST | 🔄 进行中（17/17 单测 + 6/6 集成通过） |
| `intelligent-qa` | TASK | 🔄 等待确认，17 任务 6 波次，下一步 DEV |
| `fix-extraction-json-parsing` | DEV | ✅ T01 完成 |
| `package-restructure` | DEV ✅ | 🔄 15+5 任务完成，T01-T20 全部 done，下一步 TEST/REVIEW |
| `refine-package-structure` | DONE | ✅ 7/7 任务完成（本次将 supersede） |
| `frontend-ui` | DEV | ✅ 18/18 任务完成，5 波次全部通过，vue-tsc 0 错误 |

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | ExtractionJsonParser + Jackson 宽松解析 | — | ✅ |
| T01 | ErrorCode 新增 A0008/A0009/A0010 | `51626a0` | ✅ |
| T02 | GraphNode/GraphEdge 抽象 + NodeType/EdgeType 枚举 | `63840a8` | ✅ |
| T03 | LlmGateway + SpringAiLlmGateway + yml 配置 | `fd15560` | ✅ |
| T04 | DocumentNode/EntityNode/KnowledgePointNode/KnowledgeCategoryNode | `e599eb1` | ✅ |
| T05 | 6 类图边（Extracts/References/Derives/Contains/AlignedTo/BelongsTo/ChildOf/Prerequisite） | `6d03eab` | ✅ |
| T06 | GraphNodeRepository 通用图仓库 | `c7aba8e` | ✅ |
| T07 | ExtractionService Prompt+JSON Schema+校验 | `c90c4f4` | ✅ |
| T08 | GraphService 接口+实现 | `38c7e61` | ✅ |
| T09 | GraphController + VO/DTO | `976f1ac` | ✅ |
| T10 | 单元测试（16 tests, 0 failures） | `989a40d` | ✅ |
| T11 | 集成测试（4/4 通过，直连 podman + DeepSeek API） | `a0b1ef5` | ✅ |

## 中断任务

暂无。

## 待完成事项

- 进入 `@flow-kit/prompts/5-test.md` 测试矩阵 + UAT
- 进入 `@flow-kit/prompts/6-review.md` 双轮审查

---

> 完整流程见 `.specs/knowledge-graph-extraction/` 下各工件。