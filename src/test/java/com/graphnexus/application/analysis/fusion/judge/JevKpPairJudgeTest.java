package com.graphnexus.application.analysis.fusion.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.infrastructure.typesafe.client.TypeSafeHttpException;
import com.graphnexus.infrastructure.typesafe.client.TypeSafeJevClient;
import com.graphnexus.infrastructure.typesafe.config.TypeSafeProperties;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneAnswer;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneRequest;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneResponse;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JevKpPairJudge 知识点候选对判断")
class JevKpPairJudgeTest {

    private TypeSafeJevClient client;
    private TypeSafeProperties properties;
    private JevKpPairJudge judge;

    @BeforeEach
    void setUp() {
        client = mock(TypeSafeJevClient.class);
        properties = new TypeSafeProperties();
        properties.setEnabled(true);
        properties.setMode(TypeSafeProperties.Mode.SHADOW);
        properties.setApiKey("test-key");
        properties.setCacheMaxEntries(10);
        judge = new JevKpPairJudge(client, properties, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("将 Score 与三个 Noul 映射为可审计判断，并构建最小状态")
    void mapsScoreAndNouls() {
        when(client.evaluate(any())).thenReturn(validResponse());

        KpPairJudgment result = judge.judge(pair());

        assertEquals("jev-1.13.0", result.model());
        assertEquals(JevLinkOutcome.SAME, result.outcome());
        assertEquals(0.93, result.confidence(), 0.0001);
        assertEquals(0.95, result.sameCoreConcept(), 0.0001);
        assertEquals(314, result.inputTokens());
        assertEquals(21, result.outputTokens());
        assertFalse(result.stateHash().isBlank());

        ArgumentCaptor<SystemOneRequest> captor = ArgumentCaptor.forClass(SystemOneRequest.class);
        verify(client).evaluate(captor.capture());
        assertEquals("jev-1.13.0", captor.getValue().model());
        assertEquals(List.of("link_state", "same_core_concept", "descriptions_consistent", "neighborhood_consistent"),
                captor.getValue().questions().keySet().stream().toList());
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) captor.getValue().state();
        assertTrue(state.containsKey("mention"));
        assertTrue(state.containsKey("candidate"));
    }

    @Test
    @DisplayName("相同输入使用 stateHash 缓存，不重复请求 Jev")
    void cachesIdenticalPair() {
        when(client.evaluate(any())).thenReturn(validResponse());

        KpPairJudgment first = judge.judge(pair());
        KpPairJudgment second = judge.judge(pair());

        assertSame(first, second);
        verify(client, times(1)).evaluate(any());
    }

    @Test
    @DisplayName("概率分布非法时拒绝整个响应")
    void rejectsInvalidProbabilityDistribution() {
        SystemOneResponse invalid = new SystemOneResponse("jev-1.13.0", Map.of(
                "link_state", new SystemOneAnswer("score", null, null, 1.8,
                        Map.of("0", 0.1, "1", 0.2, "2", 0.2), 0.9, Map.of()),
                "same_core_concept", new SystemOneAnswer("noul", 0.9, null, null, null, null, null),
                "descriptions_consistent", new SystemOneAnswer("noul", 0.9, null, null, null, null, null),
                "neighborhood_consistent", new SystemOneAnswer("noul", 0.9, null, null, null, null, null)),
                new SystemOneUsage(10, 2));
        when(client.evaluate(any())).thenReturn(invalid);

        assertThrows(TypeSafeHttpException.class, () -> judge.judge(pair()));
    }

    @Test
    @DisplayName("OFF 模式不允许远程判断")
    void offModeDoesNotCallRemoteService() {
        properties.setMode(TypeSafeProperties.Mode.OFF);

        assertThrows(IllegalStateException.class, () -> judge.judge(pair()));
        verifyNoInteractions(client);
    }

    private KpPairState pair() {
        return new KpPairState(
                new KnowledgePointDescriptor("mention-1", "一次函数图像", "研究一次函数图像", "数学",
                        "初中", "八年级", List.of("函数", "一次函数"), List.of("平面直角坐标系"), List.of()),
                new KnowledgePointDescriptor("kp-1", "一次函数的图象", "一次函数图象及其性质", "数学",
                        "初中", "八年级", List.of("函数", "一次函数"), List.of("平面直角坐标系"),
                        List.of("一次函数图像")));
    }

    private SystemOneResponse validResponse() {
        return new SystemOneResponse("jev-1.13.0", Map.of(
                "link_state", new SystemOneAnswer("score", null, null, 1.94,
                        Map.of("0", 0.01, "1", 0.04, "2", 0.95), 0.93, Map.of("0", "different")),
                "same_core_concept", new SystemOneAnswer("noul", 0.95, null, null, null, null, null),
                "descriptions_consistent", new SystemOneAnswer("noul", 0.92, null, null, null, null, null),
                "neighborhood_consistent", new SystemOneAnswer("noul", 0.88, null, null, null, null, null)),
                new SystemOneUsage(314, 21));
    }
}
