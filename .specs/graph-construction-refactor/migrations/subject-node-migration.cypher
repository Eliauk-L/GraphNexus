// Subject Node Migration Script (幂等)
// 用途：将存量 N4j 节点的 subject 属性迁移为 SubjectNode + BELONGS_TO_SUBJECT 边
// 执行方式：Neo4j Browser / cypher-shell 中逐段执行
// 备份：执行前请先执行 neo4j-admin dump 全量备份
//
// Phase 1: 创建 SubjectNode（从现有 subject 属性去重提取）
// 幂等性：MERGE 确保同名 Subject 不重复创建
MATCH (n)
WHERE n.subject IS NOT NULL
WITH DISTINCT n.subject AS subj
MERGE (s:Subject {name: subj})
SET s.id = COALESCE(s.id, randomUUID()),
    s.nodeType = 'Subject',
    s.createdAt = COALESCE(s.createdAt, datetime())
RETURN count(s) AS subjectNodesCreated;

// Phase 2: 创建 BELONGS_TO_SUBJECT 边
// 幂等性：MERGE 确保已存在的边不重复创建
MATCH (n)
WHERE n.subject IS NOT NULL
MATCH (s:Subject {name: n.subject})
MERGE (n)-[:BELONGS_TO_SUBJECT]->(s)
RETURN count(*) AS edgesCreated;

// Phase 3: 移除节点上的 subject 属性
// 幂等性：移除已不存在的属性不报错
MATCH (n)
WHERE n.subject IS NOT NULL
REMOVE n.subject
RETURN count(n) AS nodesCleaned;

// 验证查询：
// 确认零 subject 属性残留：
//   MATCH (n) WHERE n.subject IS NOT NULL RETURN count(n)  → 应为 0
// 确认 SubjectNode 数量正确：
//   MATCH (s:Subject) RETURN s.name ORDER BY s.name
// 确认 BELONGS_TO_SUBJECT 边数量 = 原 subject 属性节点数：
//   MATCH ()-[r:BELONGS_TO_SUBJECT]->() RETURN count(r)