// Agent 改造验收图谱。
// 唯一方向语义：A-[:PREREQUISITE_OF]->B 表示 A 是 B 的前置知识。

MERGE (subject:Subject {id: 'fixture-subject-math', name: '数学'})

MERGE (definition:KnowledgePoint {id: 'fixture-kp-definition', name: '二次函数定义'})
MERGE (functionBase:KnowledgePoint {id: 'fixture-kp-function', name: '函数基础'})
MERGE (general:KnowledgePoint {id: 'fixture-kp-general', name: '二次函数一般式'})
MERGE (completeSquare:KnowledgePoint {id: 'fixture-kp-complete-square', name: '配方法'})
MERGE (axis:KnowledgePoint {id: 'fixture-kp-axis', name: '对称轴'})
MERGE (vertex:KnowledgePoint {id: 'fixture-kp-vertex', name: '顶点坐标'})
MERGE (graph:KnowledgePoint {id: 'fixture-kp-graph', name: '二次函数图像'})
MERGE (factoring:KnowledgePoint {id: 'fixture-kp-factoring', name: '因式分解'})
MERGE (application:KnowledgePoint {id: 'fixture-kp-application', name: '二次函数综合应用'})

MERGE (functionBase)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (definition)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (general)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (completeSquare)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (axis)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (vertex)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (graph)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (factoring)-[:BELONGS_TO_SUBJECT]->(subject)
MERGE (application)-[:BELONGS_TO_SUBJECT]->(subject)

MERGE (functionBase)-[:PREREQUISITE_OF {strength: 0.90}]->(definition)
MERGE (definition)-[:PREREQUISITE_OF {strength: 0.95}]->(general)
MERGE (general)-[:PREREQUISITE_OF {strength: 0.85}]->(completeSquare)
MERGE (general)-[:PREREQUISITE_OF {strength: 0.90}]->(axis)
MERGE (completeSquare)-[:PREREQUISITE_OF {strength: 0.88}]->(axis)
MERGE (completeSquare)-[:PREREQUISITE_OF {strength: 0.92}]->(vertex)
MERGE (axis)-[:PREREQUISITE_OF {strength: 0.95}]->(vertex)
MERGE (vertex)-[:PREREQUISITE_OF {strength: 0.90}]->(graph)
MERGE (factoring)-[:PREREQUISITE_OF {strength: 0.72}]->(application)
MERGE (vertex)-[:PREREQUISITE_OF {strength: 0.85}]->(application)
MERGE (graph)-[:PREREQUISITE_OF {strength: 0.80}]->(application)

MERGE (student:Student {
  id: 'fixture-student-001',
  studentNo: 'S-FIXTURE-001',
  name: '张三',
  className: '九年级一班'
})
MERGE (student)-[:MASTERS {weight: 0.90, sampleCount: 3}]->(definition)
MERGE (student)-[:MASTERS {weight: 0.72, sampleCount: 3}]->(general)
MERGE (student)-[:MASTERS {weight: 0.48, sampleCount: 3}]->(completeSquare)
MERGE (student)-[:MASTERS {weight: 0.35, sampleCount: 3}]->(axis)
MERGE (student)-[:MASTERS {weight: 0.20, sampleCount: 3}]->(vertex);
