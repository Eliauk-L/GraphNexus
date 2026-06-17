package com.graphnexus.infrastructure.neo4j.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Neo4j 索引初始化 — 应用启动时自动创建必要索引。
 *
 * <p>索引创建是幂等的（IF NOT EXISTS），多次启动不会报错。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Neo4jIndexConfig {

    private final Neo4jClient neo4jClient;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        log.info("初始化 Neo4j 索引...");

        // documentId 查找索引 — 所有 :GraphNode 节点共享
        try {
            neo4jClient.query(
                    "CREATE INDEX doc_document_id IF NOT EXISTS FOR (n:Document) ON (n.documentId)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX entity_document_id IF NOT EXISTS FOR (n:Entity) ON (n.documentId)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX kp_document_id IF NOT EXISTS FOR (n:KnowledgePoint) ON (n.documentId)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX kpcat_document_id IF NOT EXISTS FOR (n:KnowledgeCategory) ON (n.documentId)"
            ).run();
            log.info("各节点类型 documentId 索引就绪");
        } catch (Exception e) {
            log.warn("创建索引 graphnode_documentId 失败（可能已存在）: {}", e.getMessage());
        }

        // 节点 ID 查找索引
        try {
            neo4jClient.query(
                    "CREATE INDEX graphnode_id IF NOT EXISTS FOR (n:Document) ON (n.id)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX entity_id IF NOT EXISTS FOR (n:Entity) ON (n.id)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX kp_id IF NOT EXISTS FOR (n:KnowledgePoint) ON (n.id)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX kpcat_id IF NOT EXISTS FOR (n:KnowledgeCategory) ON (n.id)"
            ).run();
            log.info("各节点类型 id 索引就绪");

        // Student 与 KnowledgePoint 查询索引（intelligent-qa T14）
        try {
            neo4jClient.query(
                    "CREATE INDEX student_student_no IF NOT EXISTS FOR (s:Student) ON (s.studentNo)"
            ).run();
            neo4jClient.query(
                    "CREATE INDEX kp_subject IF NOT EXISTS FOR (kp:KnowledgePoint) ON (kp.subject)"
            ).run();
            log.info("QA 查询索引就绪（Student.studentNo, KnowledgePoint.subject）");
        } catch (Exception e) {
            log.warn("创建 QA 索引失败（可能已存在）: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("创建索引 graphnode_id 失败（可能已存在）: {}", e.getMessage());
        }

        log.info("Neo4j 索引初始化完成");
    }
}