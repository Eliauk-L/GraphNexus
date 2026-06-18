package com.graphnexus.infrastructure.neo4j.gds;

import com.graphnexus.infrastructure.neo4j.gds.config.MetricsProperties;
import com.graphnexus.common.model.MetricResultBO;
import com.graphnexus.common.model.MetricsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GdsAdapter 单元测试 — 验证 Cypher 构造与错误处理（Mock Neo4jClient 浅层 mock）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GdsAdapter 测试")
class GdsAdapterTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    private GdsAdapter adapter;

    @BeforeEach
    void setUp() {
        MetricsProperties props = new MetricsProperties();
        adapter = new GdsAdapter(neo4jClient, props);
    }

    // ======================== Cypher 构造 ========================

    @Test
    @DisplayName("PageRank 调用包含 project + stream + drop 三步（AC-1）")
    void testPageRank_ThreeStepCypherCalled() {
        // RETURNS_DEEP_STUBS: neo4jClient.query(any).run() returns default mock
        // neo4jClient.query(any).fetch().all() returns empty Collection mock by default
        MetricsQuery query = new MetricsQuery(
                Set.of("KnowledgePoint"), Set.of("PREREQUISITE_OF"), "pagerank");
        adapter.calculatePageRank(query);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, atLeast(3)).query(captor.capture());
        List<String> cyphers = captor.getAllValues();

        // Step 1: project
        assertTrue(cyphers.stream().anyMatch(c -> c.contains("gds.graph.project")),
                "应调用 gds.graph.project");
        // Step 2: pageRank.stream
        assertTrue(cyphers.stream().anyMatch(c -> c.contains("gds.pageRank.stream")),
                "应调用 gds.pageRank.stream");
        // Step 3: graph.drop
        assertTrue(cyphers.stream().anyMatch(c -> c.contains("gds.graph.drop")),
                "应调用 gds.graph.drop");
    }

    @Test
    @DisplayName("空 nodeTypes 使用 '*' 通配符（AC-3 全图默认）")
    void testProjection_AllNodesWildcard() {
        MetricsQuery query = new MetricsQuery(
                Collections.emptySet(), Set.of("PREREQUISITE_OF"), "pagerank");
        adapter.calculatePageRank(query);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, atLeast(1)).query(captor.capture());
        String projectCypher = captor.getAllValues().stream()
                .filter(c -> c.contains("gds.graph.project"))
                .findFirst().orElse("");
        assertTrue(projectCypher.contains("'*'"),
                "空 nodeTypes 应使用 '*' 通配符: " + projectCypher);
    }

    @Test
    @DisplayName("空 edgeTypes 使用 '*' 通配符字符串（GDS 2.x compat）")
    void testProjection_AllEdgesWildcard() {
        MetricsQuery query = new MetricsQuery(
                Set.of("KnowledgePoint"), Collections.emptySet(), "pagerank");
        adapter.calculatePageRank(query);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, atLeast(1)).query(captor.capture());
        String projectCypher = captor.getAllValues().stream()
                .filter(c -> c.contains("gds.graph.project"))
                .findFirst().orElse("");
        // GDS 2.x: 空 edgeTypes → '*' 字符串，非 {'*': ...} map
        assertTrue(projectCypher.contains("'*'"),
                "GDS 2.x 空边类型应使用 '*' 字符串: " + projectCypher);
    }

    @Test
    @DisplayName("ALIGNED_TO 和 MASTERS 使用 UNDIRECTED orientation（DESIGN §2.3）")
    void testProjection_UndirectedEdges() {
        MetricsQuery query = new MetricsQuery(
                Set.of("KnowledgePoint"), Set.of("ALIGNED_TO", "MASTERS"), "pagerank");
        adapter.calculatePageRank(query);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, atLeast(1)).query(captor.capture());
        String projectCypher = captor.getAllValues().stream()
                .filter(c -> c.contains("gds.graph.project"))
                .findFirst().orElse("");

        assertTrue(projectCypher.contains("UNDIRECTED"),
                "ALIGNED_TO 和 MASTERS 应使用 UNDIRECTED: " + projectCypher);
    }

    @Test
    @DisplayName("PREREQUISITE_OF 使用 NATURAL orientation（默认方向）")
    void testProjection_NaturalEdges() {
        MetricsQuery query = new MetricsQuery(
                Set.of("KnowledgePoint"), Set.of("PREREQUISITE_OF"), "pagerank");
        adapter.calculatePageRank(query);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, atLeast(1)).query(captor.capture());
        String projectCypher = captor.getAllValues().stream()
                .filter(c -> c.contains("gds.graph.project"))
                .findFirst().orElse("");

        assertTrue(projectCypher.contains("NATURAL"),
                "PREREQUISITE_OF 应使用 NATURAL: " + projectCypher);
    }

    // ======================== 错误处理与调度 ========================

    @Test
    @DisplayName("不支持的指标类型抛异常")
    void testCalculate_UnsupportedMetric() {
        MetricsQuery query = new MetricsQuery(Set.of(), Set.of(), "betweenness");
        assertThrows(IllegalArgumentException.class, () -> adapter.calculate(query));
    }
}