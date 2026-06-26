-- ============================================================
-- GraphNexus 知识节点 PageRank 计算 — Cypher 查询集
-- ============================================================
-- 图模型说明：
--   (kpA)-[:PREREQUISITE_OF {weight, strength}]->(kpB)
--   A 是 B 的前置知识点，B 依赖 A。边的 weight = strength (0~1)。
--   对于 PageRank，"重要性流"从被依赖方流向依赖方：
--   一个被很多知识节点依赖的基础概念天然具有高 PageRank。
--
-- 使用前提（GDS 方案）：Neo4j 服务器已安装 GDS 插件
--   docker: neo4j:5-enterprise 自带 GDS
--   云: Neo4j AuraDS / AuraDB Professional+ 已内置
--   验证: RETURN gds.version()
-- ============================================================


-- ============================================================
-- Part 0: 环境检查
-- ============================================================

-- 检查 GDS 是否可用
RETURN gds.version() AS gdsVersion;

-- 查看知识节点和前置边规模
MATCH (kp:KnowledgePoint)
RETURN COUNT(kp) AS totalKnowledgePoints;

MATCH ()-[r:PREREQUISITE_OF]->()
RETURN COUNT(r) AS totalPrerequisiteEdges;


-- ============================================================
-- Part 1: GDS PageRank — 纯知识依赖图（推荐）
-- ============================================================
-- 图投影：KnowledgePoint 节点 + PREREQUISITE_OF 边
-- PageRank 方向：依赖边的自然方向
--   一个节点被越多其他节点依赖 → 入度越高 → PageRank 越高
--   对应"基础概念更重要的直觉：被依赖的越多，排名越高

-- Step 1: 创建内存图投影（带权重）
CALL gds.graph.project(
    'kp-prerequisite-graph',       -- 图名
    'KnowledgePoint',              -- 节点标签
    {
        PREREQUISITE_OF: {         -- 关系投影
            type: 'PREREQUISITE_OF',
            orientation: 'NATURAL',
            properties: 'weight'   -- 权重字段
        }
    }
);

-- Step 2: 执行 PageRank（stream 模式，不写回）
CALL gds.pageRank.stream('kp-prerequisite-graph', {
    maxIterations: 20,
    dampingFactor: 0.85,
    relationshipWeightProperty: 'weight',
    tolerance: 0.0001              -- 收敛阈值
})
YIELD nodeId, score
MATCH (kp:KnowledgePoint) WHERE id(kp) = nodeId
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       kp.gradeLevel AS gradeLevel,
       ROUND(score, 6) AS pageRank,
       kp.fusionSource AS source
ORDER BY pageRank DESC
LIMIT 50;


-- Step 3: 写回模式 — 将 PageRank 分数写入节点属性
CALL gds.pageRank.write('kp-prerequisite-graph', {
    maxIterations: 20,
    dampingFactor: 0.85,
    relationshipWeightProperty: 'weight',
    writeProperty: 'pageRank'      -- 写入的属性名
})
YIELD nodePropertiesWritten, ranIterations, didConverge;

-- 验证写入结果
MATCH (kp:KnowledgePoint)
WHERE kp.pageRank IS NOT NULL
RETURN kp.name, kp.subject, kp.pageRank
ORDER BY kp.pageRank DESC
LIMIT 20;


-- Step 4: 清理图投影（释放内存）
CALL gds.graph.drop('kp-prerequisite-graph');


-- ============================================================
-- Part 2: GDS PageRank — 按学科独立计算
-- ============================================================
-- 同一学科内的知识依赖才有意义，跨学科依赖极少

-- 以 Math 学科为例，用 Cypher 投影过滤
CALL gds.graph.project.cypher(
    'kp-math-graph',
    'MATCH (kp:KnowledgePoint {subject: "Math"}) RETURN id(kp) AS id',
    'MATCH (a:KnowledgePoint {subject: "Math"})-[r:PREREQUISITE_OF]->(b:KnowledgePoint {subject: "Math"}) RETURN id(a) AS source, id(b) AS target, r.weight AS weight'
)
YIELD graphName, nodeCount, relationshipCount;

-- 执行 PageRank
CALL gds.pageRank.stream('kp-math-graph', {
    maxIterations: 20,
    dampingFactor: 0.85,
    relationshipWeightProperty: 'weight'
})
YIELD nodeId, score
MATCH (kp:KnowledgePoint) WHERE id(kp) = nodeId
RETURN kp.name   AS knowledgePoint,
       kp.gradeLevel AS gradeLevel,
       ROUND(score, 6) AS pageRank,
       kp.description
ORDER BY pageRank DESC;

CALL gds.graph.drop('kp-math-graph');


-- ============================================================
-- Part 3: 个性化 PageRank — 单个知识节点视角
-- ============================================================
-- 从某个特定知识节点出发，计算其他知识节点相对于它的重要性
-- 场景：学生已掌握 "一次函数"，哪些后续知识点最重要？

-- 先找到目标节点的 Neo4j 内部 ID
MATCH (kp:KnowledgePoint {name: '一次函数'})
RETURN id(kp) AS sourceNodeId;

-- 假设 sourceNodeId = 42，执行 Personalized PageRank
CALL gds.graph.project(
    'kp-pers-graph',
    'KnowledgePoint',
    {
        PREREQUISITE_OF: {
            type: 'PREREQUISITE_OF',
            orientation: 'NATURAL',
            properties: 'weight'
        }
    }
);

MATCH (source:KnowledgePoint {name: '一次函数'})
CALL gds.pageRank.stream('kp-pers-graph', {
    maxIterations: 20,
    dampingFactor: 0.85,
    relationshipWeightProperty: 'weight',
    sourceNodes: [source]              -- 指定个性化起点
})
YIELD nodeId, score
MATCH (kp:KnowledgePoint) WHERE id(kp) = nodeId
WHERE kp.name <> '一次函数'           -- 排除起点自身
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       ROUND(score, 6) AS personalizedPageRank
ORDER BY personalizedPageRank DESC
LIMIT 20;

CALL gds.graph.drop('kp-pers-graph');


-- ============================================================
-- Part 4: 多关系复合 PageRank — 融合多种关系信号
-- ============================================================
-- 仅用 PREREQUISITE_OF 可能遗漏被大量实体/考试引用的知识节点
-- 复合投影加入 ALIGNED_TO 和 TESTED 的反向边作为重要性信号
--   (Entity)-[:ALIGNED_TO]->(kp) → kp 被实体引用，反转为人气信号
--   (Exam)-[:TESTED]->(kp)       → kp 被考试覆盖，反转为人气信号

CALL gds.graph.project.cypher(
    'kp-composite-graph',
    -- 节点：所有 KnowledgePoint
    'MATCH (kp:KnowledgePoint) RETURN id(kp) AS id',
    -- 边：PREREQUISITE_OF 自然方向 + 其他关系反转（作为人气信号）
    'MATCH (a:KnowledgePoint)-[r:PREREQUISITE_OF]->(b:KnowledgePoint)
     RETURN id(a) AS source, id(b) AS target, r.weight AS weight
     UNION ALL
     MATCH (e:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint)
     MATCH (kp2:KnowledgePoint)
     WHERE kp.id <> kp2.id
     WITH kp, kp2, COUNT(e) AS cnt
     WHERE cnt > 0
     RETURN id(kp) AS source, id(kp2) AS target, 0.1 * cnt AS weight
     UNION ALL
     MATCH (exam:Exam)-[:TESTED]->(kp:KnowledgePoint)
     MATCH (kp2:KnowledgePoint)
     WHERE kp.id <> kp2.id
     WITH kp, kp2, COUNT(exam) AS cnt
     WHERE cnt > 0
     RETURN id(kp) AS source, id(kp2) AS target, 0.05 * cnt AS weight'
)
YIELD graphName, nodeCount, relationshipCount;

CALL gds.pageRank.stream('kp-composite-graph', {
    maxIterations: 20,
    dampingFactor: 0.85,
    relationshipWeightProperty: 'weight'
})
YIELD nodeId, score
MATCH (kp:KnowledgePoint) WHERE id(kp) = nodeId
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       ROUND(score, 6) AS compositePageRank
ORDER BY compositePageRank DESC
LIMIT 30;

CALL gds.graph.drop('kp-composite-graph');


-- ============================================================
-- Part 5: 手动近似 PageRank（无 GDS 时使用）
-- ============================================================
-- 使用入度 + 递归加权近似 PageRank，不依赖 GDS 库
-- 核心逻辑：一个节点的重要性 ∝ 指向它的节点的重要性 / 它们的出度

-- 5a. 入度排名（一阶近似：被依赖越多越重要）
MATCH (kp:KnowledgePoint)<-[:PREREQUISITE_OF]-(dependent:KnowledgePoint)
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       COUNT(dependent) AS inDegree,           -- 被依赖次数
       ROUND(AVG(r.weight), 3) AS avgDepWeight -- 平均依赖权重
ORDER BY inDegree DESC
LIMIT 30;


-- 5b. 加权入度（考虑依赖强度）
MATCH (kp:KnowledgePoint)<-[r:PREREQUISITE_OF]-(dependent:KnowledgePoint)
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       COUNT(r) AS inDegree,
       ROUND(SUM(r.weight), 3) AS weightedInDegree
ORDER BY weightedInDegree DESC
LIMIT 30;


-- 5c. 二级传播近似 — 考虑依赖者的出度惩罚
--      重要性 ≈ 所有后置节点的贡献之和
--      贡献 = (后置节点的重要性 / 后置节点的入度数)
--      用加权入度作为代理重要性，再除以出度
MATCH (kp:KnowledgePoint)<-[r:PREREQUISITE_OF]-(dependent:KnowledgePoint)
WITH kp, dependent, r.weight AS depWeight
OPTIONAL MATCH (dependent)-[r2:PREREQUISITE_OF]->()
WITH kp, dependent, depWeight, COUNT(r2) AS dependentOutDegree
WITH kp,
     SUM(depWeight / CASE WHEN dependentOutDegree = 0 THEN 1 ELSE dependentOutDegree END) AS rawScore
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       ROUND(rawScore, 6) AS approxPageRank
ORDER BY approxPageRank DESC
LIMIT 30;


-- 5d. 全手工迭代 PageRank（最多 10 轮）
--      需要用 APOC 或写存储过程，这里给出 Neo4j Browser 可执行版本
--      原理：反复传播直到收敛
-- Step 1: 初始化所有节点 PR = 1.0
MATCH (kp:KnowledgePoint)
SET kp._pr = 1.0, kp._nextPr = 0.0;

-- Step 2: 迭代（手动执行，每次替换上一次的 _pr）
--         实际使用时把下面这段包在 apoc.periodic.commit 或脚本循环中
MATCH (kp:KnowledgePoint)
OPTIONAL MATCH (kp)<-[r:PREREQUISITE_OF]-(src:KnowledgePoint)
WITH kp, COLLECT({srcId: src.id, weight: coalesce(r.weight, 1.0)}) AS inEdges
OPTIONAL MATCH (src:KnowledgePoint)-[r2:PREREQUISITE_OF]->()
WITH kp, inEdges, src, COUNT(r2) AS srcOutDegree
WITH kp, inEdges, COLLECT({srcId: src.id, outDegree: srcOutDegree}) AS srcOutMap
WITH kp, inEdges, srcOutMap
UNWIND inEdges AS edge
MATCH (srcKp:KnowledgePoint {id: edge.srcId})
WITH kp, SUM(
    edge.weight * srcKp._pr /
    CASE
        WHEN [m IN srcOutMap WHERE m.srcId = edge.srcId | m.outDegree][0] = 0
        THEN 1
        ELSE [m IN srcOutMap WHERE m.srcId = edge.srcId | m.outDegree][0]
    END
) AS prSum
SET kp._nextPr = 0.15 + 0.85 * prSum;

-- Step 3: 交换 _pr 和 _nextPr，重复 Step 2 直到收敛
MATCH (kp:KnowledgePoint)
SET kp._pr = kp._nextPr, kp._nextPr = 0.0;

-- Step 4: 查看结果并清理
MATCH (kp:KnowledgePoint)
WHERE kp._pr IS NOT NULL
RETURN kp.name AS knowledgePoint,
       kp.subject AS subject,
       ROUND(kp._pr, 6) AS manualPageRank
ORDER BY manualPageRank DESC
LIMIT 30;

-- 清理临时属性
MATCH (kp:KnowledgePoint)
REMOVE kp._pr, kp._nextPr;


-- ============================================================
-- Part 6: PageRank 结果的诊断与分析
-- ============================================================

-- 6a. 每个学科的 PageRank TOP 5
MATCH (kp:KnowledgePoint)
WHERE kp.pageRank IS NOT NULL
WITH kp, kp.subject AS subject
ORDER BY kp.pageRank DESC
WITH subject, COLLECT({name: kp.name, pr: kp.pageRank})[0..5] AS top5
RETURN subject, top5
ORDER BY subject;

-- 6b. PageRank 分布统计
MATCH (kp:KnowledgePoint)
WHERE kp.pageRank IS NOT NULL
RETURN ROUND(AVG(kp.pageRank), 6) AS avgPageRank,
       ROUND(STDEV(kp.pageRank), 6) AS stdevPageRank,
       ROUND(MIN(kp.pageRank), 6) AS minPageRank,
       ROUND(MAX(kp.pageRank), 6) AS maxPageRank,
       ROUND(MAX(kp.pageRank) / AVG(kp.pageRank), 2) AS concentrationRatio;

-- 6c. 结合 MASTERS 边：高 PageRank 但学生掌握度低的"关键薄弱点"
MATCH (kp:KnowledgePoint)
WHERE kp.pageRank IS NOT NULL
MATCH (s:Student)-[m:MASTERS]->(kp)
WHERE m.weight < 0.6
RETURN kp.name   AS knowledgePoint,
       kp.subject AS subject,
       kp.pageRank AS pageRank,
       s.studentNo AS student,
       s.name AS studentName,
       m.weight AS masteryLevel
ORDER BY kp.pageRank DESC, masteryLevel ASC
LIMIT 50;


-- ============================================================
-- Part 7: PageRank + 用户行为信号融合（个性化排序）
-- ============================================================
-- 将 GDS PageRank 与 MASTERS 掌握度结合，生成个性化学习推荐

-- 对单个学生：推荐"高 PageRank 但低掌握度"的知识点
MATCH (s:Student {studentNo: '2024001'})
MATCH (kp:KnowledgePoint)
WHERE kp.pageRank IS NOT NULL
OPTIONAL MATCH (s)-[m:MASTERS]->(kp)
WITH kp, s, COALESCE(m.weight, 0.0) AS masteryLevel
WHERE masteryLevel < 0.6
RETURN kp.name   AS recommendedKp,
       kp.subject,
       kp.pageRank AS globalImportance,
       masteryLevel,
       ROUND(kp.pageRank * (1.0 - masteryLevel), 6) AS priorityScore   -- 重要性 × 不掌握程度
ORDER BY priorityScore DESC
LIMIT 20;