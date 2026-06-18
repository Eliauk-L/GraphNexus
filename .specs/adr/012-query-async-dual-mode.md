# ADR-012: 同步/异步双模式 API + query_task 状态机 + MASTERS 降级

## Context

`intelligent-qa` 的问答链路涉及 Neo4j 图遍历（可能慢）+ LLM API 调用（可能 30~60s），存在超时风险。同时，融合可能从未执行，导致 MASTERS 边不存在，需要降级处理。

核心问题：
1. 如何设计 API 以适应不同延迟的问答请求？
2. 如何在 MASTERS 边缺失时仍能提供有价值的分析？
3. 异步任务的状态如何持久化和追踪？

## Decision

### D1: 同步/异步双模式 API

| 端点 | 模式 | 超时 | 返回 |
|------|------|------|------|
| `POST /api/v1/query/ask` | 同步（默认） | 30s | 200 + 完整结果 或 202 + taskId（超时降级） |
| `POST /api/v1/query/ask-async` | 异步 | 无 | 202 + taskId |
| `GET /api/v1/query/result/{taskId}` | 轮询 | — | 200 + status(PROCESSING/COMPLETED/FAILED) |

同步超时降级：Controller 层 `Future.get(30s)` 超时后返回 202，后台继续执行 → 完成后写 query_task。

异步执行通过 Spring `@Async` + 自定义线程池（`query.async.core-pool-size=2, max=5, queue=10`）。

### D2: query_task 状态机

```
PENDING → PROCESSING → COMPLETED/FAILED
```

- PENDING：异步任务写入后等待 @Async 拾取
- PROCESSING：@Async 开始执行
- COMPLETED：包含 answer + subgraph_json + token_usage_json
- FAILED：包含 error_message + retry_count

同步模式跳过 PENDING（在内存中流转，完成后一次性写入 COMPLETED）。

### D3: MASTERS 降级处理

查询顺序：
1. 先查 MASTERS：`(s)-[m:MASTERS]->(kp)` 
2. 若返回空 → 降级查 TESTED 路径：`(s)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp)` + MySQL `exam_record.score_details` 计算原始得分率（简单算术平均，无时间衰减）
3. 在 PruningMeta 中标记 `mastersAvailable: false`
4. Prompt 中追加提示：`⚠️ 融合数据不可用，以下为原始考试得分率...`

## Consequences

### 正面
- 双模式满足不同场景：简单问题即时响应，复杂问题可靠异步
- 同步超时自动降级避免客户端无限等待
- 降级策略保证融合未执行时也能使用（降低功能上线门槛）
- query_task 日志表提供完整审计追踪

### 负面
- 双模式增加 API 端点和代码路径（`QueryController` 4 个端点 vs 1 个）
- @Async 线程池需额外配置（core/max/queue），不当配置可能导致任务堆积
- 降级路径的掌握度计算（原始得分率）与融合后的时间衰减加权结果有系统偏差
- query_task 表无 is_deleted，随使用量增长需后续加 TTL 清理策略

### 风险缓解
- 线程池 CallerRunsPolicy：队列满时在 Tomcat 线程同步执行，宁可慢不丢任务
- 降级提示在 Prompt 模板中以 ⚠️ 警告形式显式告知 LLM 和用户
- query_task 查询始终按 task_id 单行查（主键索引），性能不受表大小影响
- 监控 metrics：async_task_queue_size、avg_elapsed_ms、status_distribution