// Agent 改造验收图谱。
// 唯一方向语义：A-[:PREREQUISITE_OF]->B 表示 A 是 B 的前置知识。

MERGE (subject:Subject {id: 'fixture-subject-math', name: '数学'})

MERGE (definition:KnowledgePoint {id: 'fixture-kp-definition', name: '二次函数定义'})
MERGE (general:KnowledgePoint {id: 'fixture-kp-general', name: '二次函数一般式'})
MERGE (axis:KnowledgePoint {id: 'fixture-kp-axis', name: '对称轴'})
MERGE (vertex:KnowledgePoint {id: 'fixture-kp-vertex', name: '顶点坐标'})
MERGE (application:KnowledgePoint {id: 'fixture-kp-application', name: '二次函数综合应用'})

MERGE (definition)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (general)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (axis)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (vertex)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (application)-[:BELONGS_TO_SUBJECT]->(subject)

MERGE (definition)-[:PREREQUISITE_OF {strength: 0.95}]->(general)
MERGE (general)-[:PREREQUISITE_OF {strength: 0.90}]->(axis)
MERGE (axis)-[:PREREQUISITE_OF {strength: 0.95}]->(vertex)
MERGE (vertex)-[:PREREQUISITE_OF {strength: 0.85}]->(application)

MERGE (student:Student {
  id: 'fixture-student-001',
  studentNo: 'S-FIXTURE-001',
  name: '张三',
  className: '九年级一班'
})
MERGE (student)-[:MASTERS {weight: 0.90, sampleCount: 3}]->(definition)
MERGE (student)-[:MASTERS {weight: 0.72, sampleCount: 3}]->(general)
MERGE (student)-[:MASTERS {weight: 0.35, sampleCount: 3}]->(axis)
MERGE (student)-[:MASTERS {weight: 0.20, sampleCount: 3}]->(vertex);
