# T05-SUMMARY: 6 类 Neo4j 图关系边实体

- **Task ID**: T05
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 6 个图边子类，继承 GraphEdge，覆盖 8 种逻辑边类型：

| 类 | 逻辑类型 | 方向 | 特有字段 |
|----|----------|------|----------|
| ExtractsEdge | EXTRACTS | Document→Entity | - |
| ReferencesEdge | REFERENCES/DERIVES/CONTAINS | Entity→Entity | referenceType, description |
| AlignedToEdge | ALIGNED_TO | Entity→KnowledgePoint | - |
| BelongsToEdge | BELONGS_TO | KnowledgePoint→KnowledgeCategory | - |
| ChildOfEdge | CHILD_OF | KnowledgeCategory→KnowledgeCategory | - |
| PrerequisiteEdge | PREREQUISITE_OF | KnowledgePoint→KnowledgePoint | strength, description |

DERIVES 和 CONTAINS 合并入 ReferencesEdge（用 `referenceType` 字段区分）。

## 改动文件（6 个新增）

- `ExtractsEdge.java` / `ReferencesEdge.java` / `AlignedToEdge.java` / `BelongsToEdge.java` / `ChildOfEdge.java` / `PrerequisiteEdge.java`

## verify

`mvn compile -q` — 通过

## 越界检查

TASK write_files: 6 项 | diff: 6 项 | 越界: 0 ✅

## 完成判定

全部 8 种逻辑边类型通过 6 个 Java 类覆盖。