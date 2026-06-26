# ADR-052: 统计快照 JSON 聚合存储模型

- **日期**: 2026-06-23
- **状态**: accepted
- **来源**: `ops-analytics` DESIGN

---

## Context

每日凌晨需要采集全量统计指标并持久化（REQUIREMENT AC-9），指标维度包括：活跃用户数、各操作类型次数、文档总量/状态分布、图谱各节点/边类型计数、按学科分布。这些指标维度多（10+ 个指标），且未来可能新增统计维度。

可选方案：
1. **MySQL JSON 列**：一条快照记录含 `snapshot_date` + `snapshot_data`（JSON 类型），所有指标聚合在一个 JSON 对象中
2. **EAV 模型（Entity-Attribute-Value）**：`(snapshot_date, metric_name, metric_value)` 三元组，每个指标一行
3. **宽表**：每个指标一个列（如 `user_count`、`login_count`、`kp_count`...），快照日期一行

## Decision

选择 **MySQL JSON 列**。

理由：
- 指标维度多且未来变化频繁，JSON 列免 DDL 变更：新增统计维度（如 v2 加"图谱总边数增长趋势"）只需在 Java 层 `snapshot_data` JSON 中增加 key，不改表结构
- MySQL JSON 类型支持：`JSON_EXTRACT(snapshot_data, '$.usage.loginCount')` 可在 SQL 层提取单个指标；JSON 虚拟列 + BTREE 索引可优化特定指标的跨天查询（如需要）
- 每日一条记录，查询"最近 30 天趋势"只需 `SELECT * FROM stats_snapshot WHERE snapshot_date BETWEEN ? AND ? ORDER BY snapshot_date` 返回最多 30 条，Java 层解析 JSON 汇总
- 一条记录一个事务写入，数据完整性强：要么全写入，要么全失败（无"部分指标写入成功，部分失败"的中间态）

备选排除理由：
- **EAV 模型**：每日 10+ 指标 × 多学科分布 = 100+ 行，行数膨胀快；查询"某天全部指标"需多行转单对象，SQL 复杂（GROUP BY + GROUP_CONCAT 或 Java 层聚合）；指标名是字符串，拼写错误编译器不报错
- **宽表**：列数随新增指标持续膨胀，每次新增指标需 ALTER TABLE；学科分布、操作类型分布等动态维度的列不可预定义（"数学 KP 数"和"物理 KP 数"是不同列——但学科是动态增加的）

## Consequences

- **正面**：schema 灵活，新增指标零 DDL；每日一条记录，查询简单；MySQL JSON 类型成熟稳定（8.0.17+ 支持部分更新）
- **负面**：SQL 层跨天聚合复杂（如"最近 7 天登录总数"需 Java 层解析 7 天 JSON 后求和）；JSON 字段无强类型约束（需 Java 层代码保证 JSON 结构一致性）
- **JSON Schema 设计**（见 DESIGN § 2.2 Java 层定义 `SnapshotData` BO 类，序列化/反序列化由 Jackson 保证结构一致）：
```json
{
  "usage": {
    "activeUsers": 42,
    "loginCount": 156,
    "documentUploadCount": 12,
    "documentProcessCount": 10,
    "qaAskCount": 28
  },
  "documents": {
    "total": 320,
    "byStatus": {"COMPLETED": 280, "FAILED": 15, ...},
    "bySubject": {"数学": 150, "物理": 100, ...}
  },
  "graph": {
    "nodesByType": {"KnowledgePoint": 500, "Entity": 3200, ...},
    "edgesByType": {"ALIGNED_TO": 3200, "PREREQUISITE_OF": 180, ...},
    "bySubject": {
      "数学": {"nodes": {"KnowledgePoint": 200, ...}, "edges": {...}},
      "物理": {"nodes": {"KnowledgePoint": 150, ...}, "edges": {...}}
    }
  }
}
```