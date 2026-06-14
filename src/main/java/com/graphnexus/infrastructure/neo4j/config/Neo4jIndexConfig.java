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
                    "CREATE INDEX graphnode_documentId IF NOT EXISTS FOR (n:GraphNode) ON (n.documentId)"
            ).run();
            log.info("索引 graphnode_documentId 就绪");
        } catch (Exception e) {
            log.warn("创建索引 graphnode_documentId 失败（可能已存在）: {}", e.getMessage());
        }

        // 节点 ID 查找索引
        try {
            neo4jClient.query(
                    "CREATE INDEX graphnode_id IF NOT EXISTS FOR (n:GraphNode) ON (n.id)"
            ).run();
            log.info("索引 graphnode_id 就绪");
        } catch (Exception e) {
            log.warn("创建索引 graphnode_id 失败（可能已存在）: {}", e.getMessage());
        }

        log.info("Neo4j 索引初始化完成");
    }
}