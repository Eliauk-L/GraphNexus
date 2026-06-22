package com.graphnexus.application.analysis.fusion.service.impl;

import com.graphnexus.application.analysis.fusion.config.FusionProperties;
import com.graphnexus.application.analysis.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.fusion.entity.FusionLogDO;
import com.graphnexus.infrastructure.mysql.fusion.repository.FusionLogRepository;
import com.graphnexus.infrastructure.neo4j.repository.FusionGraphRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FusionServiceImpl 单元测试 — AC-7 updateLogFailed 笔误修复验证。
 *
 * @author Jay
 * @date 2026/06/22
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FusionServiceImpl 融合服务")
class FusionServiceImplTest {

    @Mock
    private FusionGraphRepository fusionGraphRepository;

    @Mock
    private FusionLogRepository fusionLogRepository;

    @Mock
    private FusionProperties fusionProperties;

    @Mock
    private Map<String, KpMatchingStrategy> matchingStrategies;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Neo4jTransactionManager neo4jTransactionManager;

    @Mock
    private FusionGroupBuilder groupBuilder;

    @Mock
    private MastersRecalculationService mastersService;

    @Mock
    private FusionRollbackService rollbackService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FusionServiceImpl fusionService;

    /**
     * AC-7: updateLogFailed 必须设置 status="FAILED"（非 "COMPLETED"）。
     *
     * <p>通过 fuseIncremental 触发异常路径：mock Neo4j 查询抛 RuntimeException
     * → catch 块调 updateLogFailed → 断言 fusionLogRepository.save() 的 status 为 "FAILED"。</p>
     */
    @Test
    @DisplayName("AC-7: 融合失败时 fusion_log.status = FAILED")
    void updateLogFailedSetsStatusToFailed() {
        // Given: 无并发冲突
        when(fusionLogRepository.findTopByStatusOrderByExecutedAtDesc("RUNNING"))
                .thenReturn(Optional.empty());

        // Given: createLogEntry 返回一个新 logEntry（模拟 save 行为）
        when(fusionLogRepository.save(any(FusionLogDO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Given: Neo4j 查询失败（模拟 Neo4j 宕机）
        when(fusionGraphRepository.findKnowledgePointsByNamesAndSubject(anyList(), anyString()))
                .thenThrow(new RuntimeException("Neo4j connection lost"));

        // When: 调 fuseIncremental（预期抛 BusinessException）
        BusinessException ex = assertThrows(BusinessException.class, () ->
                fusionService.fuseIncremental(List.of("二次函数顶点坐标"), "数学"));

        // Then: 异常消息包含融合失败
        assertTrue(ex.getMessage().contains("增量融合失败"));

        // Then: fusionLogRepository.save() 至少被调用 2 次（createLogEntry + updateLogFailed）
        ArgumentCaptor<FusionLogDO> captor = ArgumentCaptor.forClass(FusionLogDO.class);
        verify(fusionLogRepository, atLeast(2)).save(captor.capture());

        // 最后一次 save 的 status 应为 "FAILED"（updateLogFailed 修复后）
        FusionLogDO lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("FAILED", lastSaved.getStatus(),
                "updateLogFailed 必须设置 status='FAILED'，非 'COMPLETED'");
    }
}