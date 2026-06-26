-- ============================================================
-- GraphNexus 学生考试情况与知识点掌握情况 Cypher 查询
-- 数据模型：
--   (s:Student)-[:ATTENDED]->(e:Exam)-[:TESTED]->(kp:KnowledgePoint)
--   (s:Student)-[:MASTERS]->(kp:KnowledgePoint)  -- 聚合掌握度边
-- ============================================================

-- ----------------------------------------------------------
-- 1. 查询学生参加的所有考试（按学号）
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[:ATTENDED]->(e:Exam)
RETURN s.studentNo, s.name, s.className,
       e.examNo, e.name AS examName, e.examDate, e.subject
ORDER BY e.examDate DESC;


-- ----------------------------------------------------------
-- 2. 查询某次考试涉及的所有知识点
-- ----------------------------------------------------------
MATCH (e:Exam {examNo: 'EXAM_202401'})-[:TESTED]->(kp:KnowledgePoint)
RETURN e.examNo, e.name AS examName, e.subject,
       kp.name AS kpName, kp.description, kp.gradeLevel
ORDER BY kp.name;


-- ----------------------------------------------------------
-- 3. 查询学生在某次考试中的知识点覆盖情况
--    （该学生参加了哪些考试，这些考试考了哪些知识点）
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[:ATTENDED]->(e:Exam {examNo: 'EXAM_202401'})
MATCH (e)-[:TESTED]->(kp:KnowledgePoint)
OPTIONAL MATCH (s)-[m:MASTERS]->(kp)
RETURN s.studentNo, s.name,
       e.examNo, e.name AS examName,
       kp.name AS knowledgePoint,
       kp.description,
       m.weight AS masteryLevel,
       m.description AS masteryDetail
ORDER BY COALESCE(m.weight, -1) DESC;


-- ----------------------------------------------------------
-- 4. 查询学生所有知识点的掌握情况（MASTERS 聚合边）
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[m:MASTERS]->(kp:KnowledgePoint)
RETURN s.studentNo, s.name,
       kp.name AS knowledgePoint,
       kp.subject,
       kp.description,
       m.weight AS masteryLevel,
       m.description AS masteryDetail
ORDER BY m.weight ASC;


-- ----------------------------------------------------------
-- 5. 查询学生的薄弱知识点（掌握度 < 0.6）
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[m:MASTERS]->(kp:KnowledgePoint)
WHERE m.weight < 0.6
RETURN s.studentNo, s.name,
       kp.name AS weakKnowledgePoint,
       kp.subject,
       m.weight AS masteryLevel
ORDER BY m.weight ASC;


-- ----------------------------------------------------------
-- 6. 查询学生各科目知识点的平均掌握度
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[m:MASTERS]->(kp:KnowledgePoint)
RETURN kp.subject,
       COUNT(kp) AS kpCount,
       ROUND(AVG(m.weight) * 100, 1) AS avgMasteryPercent,
       MIN(m.weight) AS minMastery,
       MAX(m.weight) AS maxMastery
ORDER BY avgMasteryPercent ASC;


-- ----------------------------------------------------------
-- 7. 综合查询：学生考试全景 + 知识点掌握（多维度）
--    列出所有考试、每次考试涉及的知识点、以及学生对该知识点的掌握度
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[:ATTENDED]->(e:Exam)
MATCH (e)-[:TESTED]->(kp:KnowledgePoint)
OPTIONAL MATCH (s)-[m:MASTERS]->(kp)
RETURN s.studentNo, s.name, s.className,
       e.examNo, e.name AS examName, e.examDate, e.subject,
       kp.name AS knowledgePoint,
       COALESCE(m.weight, -1) AS masteryLevel,
       CASE
           WHEN m.weight IS NULL THEN '未计算'
           WHEN m.weight >= 0.8 THEN '优秀'
           WHEN m.weight >= 0.6 THEN '良好'
           WHEN m.weight >= 0.4 THEN '一般'
           ELSE '薄弱'
       END AS masteryLabel
ORDER BY e.examDate DESC, masteryLevel ASC;


-- ----------------------------------------------------------
-- 8. 查询某个知识点下所有学生的掌握度分布
--    （方便对比同班/同年级水平）
-- ----------------------------------------------------------
MATCH (s:Student)-[m:MASTERS]->(kp:KnowledgePoint {name: '二次函数'})
WHERE kp.subject = 'Math'
RETURN kp.name, kp.subject,
       s.studentNo, s.name, s.className,
       m.weight AS masteryLevel,
       m.description AS masteryDetail
ORDER BY m.weight DESC;


-- ----------------------------------------------------------
-- 9. 查询学生知识点依赖链中的薄弱环节
--    （某个知识点的前置知识点掌握情况）
-- ----------------------------------------------------------
MATCH (s:Student {studentNo: '2024001'})-[m:MASTERS]->(kp:KnowledgePoint)
MATCH (prereq:KnowledgePoint)-[:PREREQUISITE_OF]->(kp)
OPTIONAL MATCH (s)-[pm:MASTERS]->(prereq)
RETURN kp.name AS targetKnowledgePoint,
       m.weight AS targetMastery,
       prereq.name AS prerequisiteKnowledgePoint,
       COALESCE(pm.weight, -1) AS prerequisiteMastery,
       CASE
           WHEN pm.weight IS NULL THEN '缺少前置掌握度数据'
           WHEN pm.weight < 0.6 AND m.weight < 0.6 THEN '前置薄弱 → 当前薄弱（可能关联）'
           WHEN pm.weight < 0.6 THEN '前置薄弱但当前正常'
           WHEN m.weight < 0.6 THEN '前置正常但当前薄弱'
           ELSE '均正常'
       END AS diagnosis
ORDER BY kp.name, COALESCE(pm.weight, -1) ASC;


-- ----------------------------------------------------------
-- 10. 查询学生未参加过的考试（可用于推荐补考或模拟）
-- ----------------------------------------------------------
MATCH (e:Exam)
WHERE e.subject IN [
    MATCH (s:Student {studentNo: '2024001'})-[:ATTENDED]->(attended:Exam)
    RETURN DISTINCT attended.subject
]
AND NOT EXISTS {
    MATCH (s:Student {studentNo: '2024001'})-[:ATTENDED]->(e)
}
RETURN e.examNo, e.name AS examName, e.examDate, e.subject
ORDER BY e.subject, e.examDate DESC;