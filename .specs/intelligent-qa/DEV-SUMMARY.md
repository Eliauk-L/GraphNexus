# DEV-SUMMARY: intelligent-qa Wave 1-6

- **日期**: 2026-06-17
- **执行波次**: Wave 1-5 完成, Wave 6 部分完成

---

## 提交记录

| 提交 | 任务 | 内容 |
|------|------|------|
| `ff3473d` | T01 | ErrorCode A0019/A0020/A0021 |
| `b63a3a2` | T02 | QueryProperties 配置类 |
| `b5084ac` | T03-T06 | QueryIntent + analysis BOs + query_task 持久化 + Strategy接口 + Prompt模板 |
| `982f3ca` | T07-T08 | GraphNodeRepository 6 个只读查询方法 + ExamRecordRepository 扩展 |
| `3132f01` | T09-T10 | StudentDiagnosisStrategy + PromptTemplateService |
| `fc9feeb` | T11 | QueryService 接口 + QueryServiceImpl 编排实现 |
| `8395af8` | T12-T13 | QueryController + AnalysisController + AsyncConfig |
| `2d2482a` | T14-T15 | Neo4j QA 索引 + query yml 配置 |
| `e3a164c` | T16 | 单元测试 7/7 pass |

## 新增文件 24 个

### L1 API
- `api/query/controller/QueryController.java` — POST /ask, POST /ask-async, GET /result/{taskId}
- `api/query/dto/QueryAskRequest.java`, `QueryAskResponse.java`, `QueryAsyncResponse.java`, `QueryResultResponse.java`
- `api/analysis/controller/AnalysisController.java` — GET /api/v1/analysis/subgraph/{taskId}
- `api/analysis/dto/SubgraphResponse.java`

### L2 application/query/
- `config/QueryProperties.java`, `AsyncConfig.java`
- `model/QueryIntent.java`, `QueryResultBO.java`
- `prompt/PromptTemplateService.java`
- `service/QueryService.java`, `impl/QueryServiceImpl.java`

### L2 application/analysis/
- `model/PruningRequest.java`, `PrunedSubgraph.java` (含 PruningMeta)
- `strategy/SubgraphPruningStrategy.java`, `StudentDiagnosisStrategy.java`

### L3 infrastructure
- `mysql/query/QueryTaskStatus.java`, `QueryTaskDO.java`, `QueryTaskRepository.java`

### resources
- `prompts/student-diagnosis-system.md`, `student-diagnosis-user.md`

## 修改既有文件 3 个
- `common/exception/ErrorCode.java` — +A0019/A0020/A0021
- `infrastructure/neo4j/repository/GraphNodeRepository.java` — +6 read-only methods
- `infrastructure/mysql/document/ExamRecordRepository.java` — +findByStudentNoAndSubject
- `infrastructure/neo4j/config/Neo4jIndexConfig.java` — +2 indexes
- `application-dev.yml` — +query config block

## 测试
- QueryPropertiesTest: 2/2 ✅
- PromptTemplateServiceTest: 5/5 ✅
- T17 集成测试 deferred（需 podman Neo4j/MySQL + LLM API Key）

## AC 覆盖
AC-1~AC-12 均在代码实现中覆盖，集成验证待 T17。